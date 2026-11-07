package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AuthScreen
import com.example.auth.signOut
import com.example.data.StatementRepository
import com.example.ui.DeceptionDetectorScreen
import com.example.ui.DeceptionViewModel
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.presagetech.smartspectra.CameraSelection
import com.presagetech.smartspectra.ProcessingStatus
import com.presagetech.smartspectra.SmartSpectraConfig
import com.presagetech.smartspectra.SmartSpectraSdk
import com.presagetech.smartspectra.proto.MetricTypesProto.MetricType
import com.presagetech.smartspectra.proto.MetricsProto.ExpressionType
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private companion object {
        const val MAX_GRAPH_POINTS = 200
        const val DEFAULT_API_KEY_PLACEHOLDER = "YOUR_SMARTSPECTRA_API_KEY"
    }

    private val sdk by lazy { SmartSpectraSdk.shared }
    private var speechRecognizer: SpeechRecognizer? = null
    private var speechRestartJob: Job? = null

    private var statusText by mutableStateOf("Status: Idle")
    private var validationText by mutableStateOf("Validation: --")
    private var buttonText by mutableStateOf("Start")
    private var buttonEnabled by mutableStateOf(true)

    private var isListeningSpeech by mutableStateOf(false)
    private var shouldKeepListeningSpeech by mutableStateOf(false)
    private var livePartialTranscript by mutableStateOf("")

    private var currentPulseRate by mutableStateOf<Int?>(null)
    private var currentBreathingRate by mutableStateOf<Int?>(null)
    private var currentHrvRmssd by mutableStateOf<Double?>(null)
    private var currentExpressionName by mutableStateOf<String?>(null)
    private var currentExpressionConfidence by mutableStateOf<Int?>(null)

    private var heartRateText by mutableStateOf("-- bpm")
    private var breathingRateText by mutableStateOf("-- bpm")
    private var hrvText by mutableStateOf("-- ms")
    private var expressionText by mutableStateOf("--")

    private val arterialPressureSamples = mutableStateListOf<Float>()
    private val chestSamples = mutableStateListOf<Float>()
    private val abdomenSamples = mutableStateListOf<Float>()

    private var latestChestTimestamp: Long = Long.MIN_VALUE
    private var latestAbdomenTimestamp: Long = Long.MIN_VALUE
    private var latestPressureTimestamp: Long = Long.MIN_VALUE

    private var onSpokenStatementCaptured: ((String) -> Unit)? = null
    private var onVitalsUpdated: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        sdk.config.apiKey = BuildConfig.SMARTSPECTRA_API_KEY
        sdk.config.imageOutputEnabled = false
        sdk.useCamera(CameraSelection.Front)
        sdk.config.requestedMetrics =
            SmartSpectraConfig.breathingMetrics +
                SmartSpectraConfig.cardioMetrics +
                listOf(MetricType.EXPRESSIONS)

        bindSdk()
        resetMeasurementUi()
        statusText = "Status: Idle"

        setContent {
            MyApplicationTheme {
                var currentUser by remember { mutableStateOf(Firebase.auth.currentUser) }

                DisposableEffect(Unit) {
                    val listener = FirebaseAuth.AuthStateListener { auth ->
                        currentUser = auth.currentUser
                    }
                    Firebase.auth.addAuthStateListener(listener)
                    onDispose {
                        Firebase.auth.removeAuthStateListener(listener)
                    }
                }

                if (currentUser == null) {
                    AuthScreen(
                        onAuthSuccess = {
                            currentUser = Firebase.auth.currentUser
                        },
                    )
                } else {
                    val uid = currentUser!!.uid
                    key(uid) {
                        val context = LocalContext.current
                        val scope = rememberCoroutineScope()
                        val credentialManager = remember(context) { CredentialManager.create(context) }

                        val viewModel: DeceptionViewModel = viewModel(
                            key = uid,
                            factory = viewModelFactory {
                                initializer {
                                    val app = checkNotNull(this[APPLICATION_KEY]) {
                                        "APPLICATION_KEY missing from CreationExtras"
                                    }
                                    val databaseId = app.getString(R.string.firestore_database_id)
                                    val db = FirebaseFirestore.getInstance(databaseId)
                                    DeceptionViewModel(StatementRepository(db), uid)
                                }
                            },
                        )

                        DisposableEffect(viewModel) {
                            onSpokenStatementCaptured = { spokenText ->
                                viewModel.evaluateAndLogStatement(
                                    transcript = spokenText,
                                    pulseRate = currentPulseRate,
                                    breathingRate = currentBreathingRate,
                                    hrvRmssd = currentHrvRmssd,
                                    expressionName = currentExpressionName,
                                    expressionConfidence = currentExpressionConfidence,
                                    formattedExpression = expressionText,
                                )
                            }
                            onVitalsUpdated = {
                                viewModel.updatePhysiologicalBaseline(
                                    pulseRate = currentPulseRate,
                                    breathingRate = currentBreathingRate,
                                    hrvRmssd = currentHrvRmssd,
                                    expressionName = currentExpressionName,
                                    expressionConfidence = currentExpressionConfidence,
                                )
                            }
                            onDispose {
                                onSpokenStatementCaptured = null
                                onVitalsUpdated = null
                            }
                        }

                        val historyState by viewModel.historyState.collectAsStateWithLifecycle()
                        val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
                        val latestAnalysis by viewModel.latestAnalysis.collectAsStateWithLifecycle()
                        val lastLoggedTranscript by viewModel.lastLoggedTranscript.collectAsStateWithLifecycle()

                        val permissionsLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestMultiplePermissions(),
                        ) { grants ->
                            val cameraGranted = grants[Manifest.permission.CAMERA] == true ||
                                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                            val audioGranted = grants[Manifest.permission.RECORD_AUDIO] == true ||
                                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

                            if (cameraGranted) {
                                viewModel.resetSessionBaseline()
                                startProcessing(startSpeech = audioGranted)
                            } else {
                                statusText = "Status: Camera required"
                            }
                        }

                        val audioOnlyPermissionLauncher = rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestPermission(),
                        ) { granted ->
                            if (granted) {
                                shouldKeepListeningSpeech = true
                                startSpeechListening()
                            }
                        }

                        DeceptionDetectorScreen(
                            statusText = statusText,
                            validationText = validationText,
                            buttonText = buttonText,
                            buttonEnabled = buttonEnabled,
                            isListeningSpeech = isListeningSpeech,
                            livePartialTranscript = livePartialTranscript,
                            lastLoggedTranscript = lastLoggedTranscript,
                            latestAnalysis = latestAnalysis,
                            heartRateText = heartRateText,
                            breathingRateText = breathingRateText,
                            hrvText = hrvText,
                            expressionText = expressionText,
                            arterialPressureSamples = arterialPressureSamples,
                            chestSamples = chestSamples,
                            abdomenSamples = abdomenSamples,
                            historyState = historyState,
                            selectedFilter = selectedFilter,
                            isApiKeyMissing = BuildConfig.SMARTSPECTRA_API_KEY.isBlank() ||
                                BuildConfig.SMARTSPECTRA_API_KEY == DEFAULT_API_KEY_PLACEHOLDER,
                            onPreviewCreated = { previewView ->
                                sdk.config.previewSurfaceProvider = previewView.surfaceProvider
                            },
                            onToggleClick = {
                                when (sdk.processingStatus.value) {
                                    ProcessingStatus.RUNNING -> {
                                        stopSpeechListening()
                                        lifecycleScope.launch {
                                            runCatching { sdk.stop() }
                                        }
                                    }
                                    ProcessingStatus.STARTING, ProcessingStatus.STOPPING -> Unit
                                    else -> {
                                        val hasCam = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.CAMERA,
                                        ) == PackageManager.PERMISSION_GRANTED
                                        val hasMic = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO,
                                        ) == PackageManager.PERMISSION_GRANTED

                                        if (!hasCam || !hasMic) {
                                            permissionsLauncher.launch(
                                                arrayOf(
                                                    Manifest.permission.CAMERA,
                                                    Manifest.permission.RECORD_AUDIO,
                                                ),
                                            )
                                        } else {
                                            viewModel.resetSessionBaseline()
                                            startProcessing(startSpeech = true)
                                        }
                                    }
                                }
                            },
                            onMicToggleClick = {
                                if (isListeningSpeech || shouldKeepListeningSpeech) {
                                    stopSpeechListening()
                                } else {
                                    val hasMic = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO,
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasMic) {
                                        shouldKeepListeningSpeech = true
                                        startSpeechListening()
                                    } else {
                                        audioOnlyPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            onManualStatementSubmit = { manualText ->
                                viewModel.evaluateAndLogStatement(
                                    transcript = manualText,
                                    pulseRate = currentPulseRate,
                                    breathingRate = currentBreathingRate,
                                    hrvRmssd = currentHrvRmssd,
                                    expressionName = currentExpressionName,
                                    expressionConfidence = currentExpressionConfidence,
                                    formattedExpression = expressionText,
                                )
                            },
                            onFilterChange = viewModel::setFilter,
                            onDeleteStatement = viewModel::deleteStatement,
                            onSignOutClick = {
                                stopSpeechListening()
                                lifecycleScope.launch {
                                    runCatching { sdk.stop() }
                                }
                                signOut(
                                    credentialManager = credentialManager,
                                    scope = scope,
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        stopSpeechListening()
        lifecycleScope.launch {
            when (sdk.processingStatus.value) {
                ProcessingStatus.RUNNING,
                ProcessingStatus.STARTING,
                ProcessingStatus.STOPPING,
                -> runCatching { sdk.stop() }
                else -> Unit
            }
        }
    }

    override fun onDestroy() {
        stopSpeechListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        super.onDestroy()
    }

    private fun startProcessing(startSpeech: Boolean) {
        if (startSpeech) {
            shouldKeepListeningSpeech = true
            startSpeechListening()
        }
        lifecycleScope.launch {
            resetMeasurementUi()
            runCatching { sdk.start() }
                .onFailure {
                    statusText = "Error: ${it.message ?: "Unknown"}"
                }
        }
    }

    private fun startSpeechListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            return
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) {
                        isListeningSpeech = true
                    }

                    override fun onBeginningOfSpeech() {
                        isListeningSpeech = true
                    }

                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit

                    override fun onEndOfSpeech() {
                        isListeningSpeech = false
                    }

                    override fun onError(error: Int) {
                        isListeningSpeech = false
                        livePartialTranscript = ""
                        scheduleNextSpeechListen()
                    }

                    override fun onResults(results: Bundle?) {
                        isListeningSpeech = false
                        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        val spoken = matches?.firstOrNull()?.trim().orEmpty()
                        livePartialTranscript = ""
                        if (spoken.isNotEmpty()) {
                            onSpokenStatementCaptured?.invoke(spoken)
                        }
                        scheduleNextSpeechListen()
                    }

                    override fun onPartialResults(partialResults: Bundle?) {
                        val partial = partialResults
                            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()
                            ?.trim()
                            .orEmpty()
                        if (partial.isNotEmpty()) {
                            livePartialTranscript = partial
                        }
                    }

                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                })
            }
        }

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        runCatching {
            speechRecognizer?.startListening(recognizerIntent)
            isListeningSpeech = true
        }
    }

    private fun scheduleNextSpeechListen() {
        speechRestartJob?.cancel()
        if (!shouldKeepListeningSpeech) return
        speechRestartJob = lifecycleScope.launch {
            delay(350L)
            if (shouldKeepListeningSpeech) {
                startSpeechListening()
            }
        }
    }

    private fun stopSpeechListening() {
        shouldKeepListeningSpeech = false
        speechRestartJob?.cancel()
        speechRestartJob = null
        isListeningSpeech = false
        livePartialTranscript = ""
        runCatching {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        }
    }

    private fun bindSdk() {
        sdk.processingStatus.observe(this) { updateProcessingStatus(it) }
        sdk.validationStatus.observe(this) { status ->
            validationText = "Validation: ${status?.code?.name?.replace('_', ' ') ?: "--"}"
        }
        sdk.error.observe(this) { error ->
            if (error != null) {
                statusText = "Error: ${error.message ?: "Unknown"}"
            }
        }
        sdk.metrics.observe(this) { metrics ->
            if (metrics == null) return@observe
            var updatedVitals = false

            if (metrics.hasCardio()) {
                val pulse = metrics.cardio.pulseRateList
                    .lastOrNull { it.timestamp > 0 }
                    ?.value
                    ?.roundToInt()
                if (pulse != null) {
                    currentPulseRate = pulse
                    heartRateText = "$pulse bpm"
                    updatedVitals = true
                }

                metrics.cardio.arterialPressureTraceList.forEach { sample ->
                    if (sample.timestamp > latestPressureTimestamp) {
                        latestPressureTimestamp = sample.timestamp
                        appendSample(arterialPressureSamples, sample.value)
                    }
                }

                metrics.cardio.hrvList.lastOrNull()?.rmssd?.let { rmssd ->
                    if (rmssd > 0) {
                        val rounded = (rmssd * 10).roundToInt() / 10.0
                        currentHrvRmssd = rounded
                        hrvText = "$rounded ms"
                        updatedVitals = true
                    }
                }
            }

            if (metrics.hasBreathing()) {
                if (metrics.breathing.rateCount > 0) {
                    val breathingRate = metrics.breathing.rateList.last().value.roundToInt()
                    currentBreathingRate = breathingRate
                    breathingRateText = "$breathingRate bpm"
                    updatedVitals = true
                }

                metrics.breathing.upperTraceList.forEach { sample ->
                    if (sample.timestamp > latestChestTimestamp) {
                        latestChestTimestamp = sample.timestamp
                        appendSample(chestSamples, sample.value)
                    }
                }

                metrics.breathing.lowerTraceList.forEach { sample ->
                    if (sample.timestamp > latestAbdomenTimestamp) {
                        latestAbdomenTimestamp = sample.timestamp
                        appendSample(abdomenSamples, sample.value)
                    }
                }
            }

            if (metrics.hasFace()) {
                val expression = metrics.face.expressionList.lastOrNull()
                val topScore = expression?.scoresList
                    ?.filter { it.confidence > 0f }
                    ?.maxByOrNull { it.confidence }
                val expressionName = topScore?.type?.expressionName()
                if (topScore != null && expressionName != null) {
                    val confInt = topScore.confidence.roundToInt()
                    currentExpressionName = expressionName
                    currentExpressionConfidence = confInt
                    expressionText = "%-8.8s %3d%%".format(expressionName, confInt)
                    updatedVitals = true
                }
            }

            if (updatedVitals) {
                onVitalsUpdated?.invoke()
            }
        }
    }

    private fun appendSample(target: MutableList<Float>, value: Float) {
        target.add(value)
        while (target.size > MAX_GRAPH_POINTS) {
            target.removeAt(0)
        }
    }

    private fun updateProcessingStatus(status: ProcessingStatus?) {
        when (status) {
            ProcessingStatus.IDLE -> {
                statusText = "Status: Idle"
                buttonText = "Start"
                buttonEnabled = true
            }
            ProcessingStatus.STARTING -> {
                statusText = "Status: Starting"
                buttonText = "Starting..."
                buttonEnabled = false
            }
            ProcessingStatus.RUNNING -> {
                statusText = "Status: Running"
                buttonText = "Stop"
                buttonEnabled = true
            }
            ProcessingStatus.STOPPING -> {
                statusText = "Status: Stopping"
                buttonText = "Stopping..."
                buttonEnabled = false
            }
            ProcessingStatus.ERROR -> {
                buttonText = "Start"
                buttonEnabled = true
            }
            null -> Unit
        }
    }

    private fun resetMeasurementUi() {
        latestChestTimestamp = Long.MIN_VALUE
        latestAbdomenTimestamp = Long.MIN_VALUE
        latestPressureTimestamp = Long.MIN_VALUE
        arterialPressureSamples.clear()
        chestSamples.clear()
        abdomenSamples.clear()
        currentPulseRate = null
        currentBreathingRate = null
        currentHrvRmssd = null
        currentExpressionName = null
        currentExpressionConfidence = null
        heartRateText = "-- bpm"
        expressionText = "--"
        breathingRateText = "-- bpm"
        hrvText = "-- ms"
    }

    private fun ExpressionType.expressionName(): String? =
        when (this) {
            ExpressionType.ANGRY -> "Angry"
            ExpressionType.CONTEMPT -> "Contempt"
            ExpressionType.DISGUST -> "Disgust"
            ExpressionType.FEAR -> "Fear"
            ExpressionType.HAPPY -> "Happy"
            ExpressionType.NEUTRAL -> "Neutral"
            ExpressionType.SAD -> "Sad"
            ExpressionType.SURPRISE -> "Surprise"
            else -> null
        }
}
