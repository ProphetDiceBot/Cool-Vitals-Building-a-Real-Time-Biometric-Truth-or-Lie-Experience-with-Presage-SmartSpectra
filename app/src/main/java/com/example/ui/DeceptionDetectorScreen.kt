package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.data.DeceptionAnalysis
import com.example.data.StatementLog
import com.example.ui.theme.VitalsAmber
import com.example.ui.theme.VitalsBgBottom
import com.example.ui.theme.VitalsBgTop
import com.example.ui.theme.VitalsBlue
import com.example.ui.theme.VitalsCardOverlay
import com.example.ui.theme.VitalsCoral
import com.example.ui.theme.VitalsMint
import com.example.ui.theme.VitalsPillBg
import com.example.ui.theme.VitalsTeal
import com.example.ui.theme.VitalsTextMuted
import com.example.ui.theme.VitalsTextPrimary
import com.example.ui.theme.VitalsViolet
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class DetectorTab {
    SCANNER,
    HISTORY,
}

@Composable
fun DeceptionDetectorScreen(
    statusText: String,
    validationText: String,
    buttonText: String,
    buttonEnabled: Boolean,
    isListeningSpeech: Boolean,
    livePartialTranscript: String,
    lastLoggedTranscript: String?,
    latestAnalysis: DeceptionAnalysis?,
    heartRateText: String,
    breathingRateText: String,
    hrvText: String,
    expressionText: String,
    arterialPressureSamples: List<Float>,
    chestSamples: List<Float>,
    abdomenSamples: List<Float>,
    historyState: UiState<List<StatementLog>>,
    selectedFilter: HistoryFilter,
    isApiKeyMissing: Boolean,
    onPreviewCreated: (PreviewView) -> Unit,
    onToggleClick: () -> Unit,
    onMicToggleClick: () -> Unit,
    onManualStatementSubmit: (String) -> Unit,
    onFilterChange: (HistoryFilter) -> Unit,
    onDeleteStatement: (String) -> Unit,
    onSignOutClick: () -> Unit,
) {
    var currentTab by rememberSaveable { mutableStateOf(DetectorTab.SCANNER) }

    if (currentTab == DetectorTab.HISTORY) {
        BackHandler {
            currentTab = DetectorTab.SCANNER
        }
    }

    val allLogs = (historyState as? UiState.Success)?.data ?: emptyList()
    val flaggedLiesCount = remember(allLogs) { allLogs.count { it.isLie } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(VitalsBgTop, VitalsBgBottom),
                ),
            )
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        // Keep PreviewView composed so camera surface stays bound when switching tabs
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 52.dp)
                .height(if (currentTab == DetectorTab.SCANNER) 360.dp else 1.dp)
                .align(Alignment.TopCenter),
        ) {
            val previewDescription = stringResource(R.string.preview_content_description)
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        contentDescription = previewDescription
                        setBackgroundColor(android.graphics.Color.BLACK)
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        onPreviewCreated(this)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("camera_preview"),
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0x44000000),
                                Color.Transparent,
                                Color(0xDD05070C),
                            ),
                        ),
                    ),
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Top Navigation & Session Bar
            TopModeSwitcherBar(
                currentTab = currentTab,
                flaggedLiesCount = flaggedLiesCount,
                totalLogsCount = allLogs.size,
                onTabSelected = { currentTab = it },
                onSignOutClick = onSignOutClick,
            )

            if (currentTab == DetectorTab.SCANNER) {
                LiveScannerContent(
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
                    recentLogs = allLogs.take(2),
                    isApiKeyMissing = isApiKeyMissing,
                    onToggleClick = onToggleClick,
                    onMicToggleClick = onMicToggleClick,
                    onManualStatementSubmit = onManualStatementSubmit,
                    onOpenHistoryClick = { currentTab = DetectorTab.HISTORY },
                    modifier = Modifier.weight(1f),
                )
            } else {
                HistoryReviewContent(
                    historyState = historyState,
                    selectedFilter = selectedFilter,
                    onFilterChange = onFilterChange,
                    onDeleteStatement = onDeleteStatement,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TopModeSwitcherBar(
    currentTab: DetectorTab,
    flaggedLiesCount: Int,
    totalLogsCount: Int,
    onTabSelected: (DetectorTab) -> Unit,
    onSignOutClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        // Segmented Tab Switcher
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(VitalsCardOverlay)
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(3.dp),
        ) {
            val scannerSelected = currentTab == DetectorTab.SCANNER
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (scannerSelected) VitalsTeal else Color.Transparent)
                    .clickable { onTabSelected(DetectorTab.SCANNER) }
                    .testTag("tab_scanner"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Radar,
                        contentDescription = null,
                        tint = if (scannerSelected) Color.Black else VitalsTextPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(R.string.tab_live_scanner),
                        color = if (scannerSelected) Color.Black else VitalsTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            val historySelected = currentTab == DetectorTab.HISTORY
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (historySelected) VitalsCoral else Color.Transparent)
                    .clickable { onTabSelected(DetectorTab.HISTORY) }
                    .testTag("tab_history"),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.History,
                        contentDescription = null,
                        tint = if (historySelected) Color.Black else VitalsTextPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (flaggedLiesCount > 0) {
                            "${stringResource(R.string.tab_history)} ($flaggedLiesCount/${totalLogsCount})"
                        } else {
                            "${stringResource(R.string.tab_history)} ($totalLogsCount)"
                        },
                        color = if (historySelected) Color.Black else VitalsTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        IconButton(
            onClick = onSignOutClick,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(VitalsPillBg)
                .testTag("sign_out_button"),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = stringResource(R.string.sign_out_desc),
                tint = VitalsTextPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun LiveScannerContent(
    statusText: String,
    validationText: String,
    buttonText: String,
    buttonEnabled: Boolean,
    isListeningSpeech: Boolean,
    livePartialTranscript: String,
    lastLoggedTranscript: String?,
    latestAnalysis: DeceptionAnalysis?,
    heartRateText: String,
    breathingRateText: String,
    hrvText: String,
    expressionText: String,
    arterialPressureSamples: List<Float>,
    chestSamples: List<Float>,
    abdomenSamples: List<Float>,
    recentLogs: List<StatementLog>,
    isApiKeyMissing: Boolean,
    onToggleClick: () -> Unit,
    onMicToggleClick: () -> Unit,
    onManualStatementSubmit: (String) -> Unit,
    onOpenHistoryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var manualStatementInput by rememberSaveable { mutableStateOf("") }

    Box(modifier = modifier.fillMaxSize()) {
        // Top HUD Controls Over Camera Preview
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusPill(
                    text = statusText,
                    accent = VitalsCoral,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("status_pill"),
                )
                StatusPill(
                    text = validationText,
                    accent = VitalsAmber,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("validation_pill"),
                )
                IconButton(
                    onClick = onMicToggleClick,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isListeningSpeech) VitalsCoral.copy(alpha = 0.28f) else VitalsPillBg)
                        .border(
                            width = 1.dp,
                            color = if (isListeningSpeech) VitalsCoral else VitalsTeal.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(14.dp),
                        )
                        .testTag("mic_toggle_button"),
                ) {
                    Icon(
                        imageVector = if (isListeningSpeech) Icons.Filled.Mic else Icons.Filled.MicOff,
                        contentDescription = stringResource(R.string.mic_toggle_desc),
                        tint = if (isListeningSpeech) VitalsCoral else VitalsTextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Button(
                    onClick = onToggleClick,
                    enabled = buttonEnabled,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f),
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .sizeIn(minWidth = 84.dp, minHeight = 48.dp)
                        .testTag("toggle_button"),
                ) {
                    Text(
                        text = buttonText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // Live Deception Verdict & Stress Score Banner
            DeceptionVerdictBanner(
                analysis = latestAnalysis,
                lastLoggedTranscript = lastLoggedTranscript,
            )

            if (isApiKeyMissing) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = VitalsCardOverlay,
                    border = BorderStroke(1.dp, VitalsAmber.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_warning_banner"),
                ) {
                    Text(
                        text = stringResource(R.string.api_key_missing_banner),
                        color = VitalsAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // Bottom Panel: Speech-to-Text Bar + Vitals Cards + Waveforms
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(start = 14.dp, end = 14.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            // Live Speech-to-Text Ticker + Manual Statement Log Input
            SpeechCaptureCard(
                isListeningSpeech = isListeningSpeech,
                livePartialTranscript = livePartialTranscript,
                manualInput = manualStatementInput,
                recentLogs = recentLogs,
                onManualInputChange = { manualStatementInput = it },
                onSubmitManualStatement = {
                    val trimmed = manualStatementInput.trim()
                    if (trimmed.isNotEmpty()) {
                        onManualStatementSubmit(trimmed)
                        manualStatementInput = ""
                    }
                },
                onOpenHistoryClick = onOpenHistoryClick,
            )

            // Row 1: Pulse Rate & Breathing Rate
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricCard(
                    title = stringResource(R.string.card_pulse_rate),
                    value = heartRateText,
                    valueColor = VitalsCoral,
                    accent = VitalsCoral,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("pulse_rate_card"),
                )
                MetricCard(
                    title = stringResource(R.string.card_breathing_rate),
                    value = breathingRateText,
                    valueColor = VitalsTeal,
                    accent = VitalsTeal,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("breathing_rate_card"),
                )
            }

            // Row 2: HRV RMSSD & Expression
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MetricCard(
                    title = stringResource(R.string.card_hrv_rmssd),
                    value = hrvText,
                    valueColor = VitalsTextPrimary,
                    accent = VitalsMint,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("hrv_card"),
                )
                MetricCard(
                    title = stringResource(R.string.card_expression),
                    value = expressionText,
                    valueColor = VitalsTextPrimary,
                    accent = VitalsAmber,
                    isMonospace = true,
                    valueFontSize = 18f,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("expression_card"),
                )
            }

            // Row 3: Arterial Pressure Waveform
            WaveformCard(
                title = stringResource(R.string.card_arterial_pressure),
                samples = arterialPressureSamples,
                graphColor = VitalsViolet,
                graphHeightDp = 56,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("arterial_pressure_card"),
            )

            // Row 4: Chest Waveform & Abdomen Waveform
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WaveformCard(
                    title = stringResource(R.string.card_chest_waveform),
                    samples = chestSamples,
                    graphColor = VitalsTeal,
                    graphHeightDp = 48,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chest_waveform_card"),
                )
                WaveformCard(
                    title = stringResource(R.string.card_abdomen_waveform),
                    samples = abdomenSamples,
                    graphColor = VitalsBlue,
                    graphHeightDp = 48,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("abdomen_waveform_card"),
                )
            }
        }
    }
}

@Composable
private fun DeceptionVerdictBanner(
    analysis: DeceptionAnalysis?,
    lastLoggedTranscript: String?,
) {
    val isLie = analysis?.isLie == true
    val score = analysis?.deceptionScore ?: 0
    val accentColor = when {
        analysis == null -> VitalsTeal
        isLie -> VitalsCoral
        else -> VitalsMint
    }
    val titleText = when {
        analysis == null -> stringResource(R.string.verdict_monitoring)
        isLie -> "${stringResource(R.string.verdict_lie_flagged)} • $score% STRESS"
        else -> "${stringResource(R.string.verdict_truth_verified)} • $score% STRESS"
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isLie) Color(0xE63B1219) else VitalsCardOverlay,
        border = BorderStroke(1.5.dp, accentColor.copy(alpha = if (isLie) 0.85f else 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("deception_verdict_banner"),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = if (isLie) Icons.Filled.WarningAmber else Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = titleText,
                        color = accentColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                if (analysis != null) {
                    Text(
                        text = analysis.reason,
                        color = VitalsTextMuted,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            LinearProgressIndicator(
                progress = { (score / 100f).coerceIn(0.05f, 1f) },
                color = accentColor,
                trackColor = Color.White.copy(alpha = 0.12f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
            )

            if (!lastLoggedTranscript.isNullOrBlank()) {
                Text(
                    text = "Last logged: \"$lastLoggedTranscript\"",
                    color = VitalsTextPrimary,
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SpeechCaptureCard(
    isListeningSpeech: Boolean,
    livePartialTranscript: String,
    manualInput: String,
    recentLogs: List<StatementLog>,
    onManualInputChange: (String) -> Unit,
    onSubmitManualStatement: () -> Unit,
    onOpenHistoryClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = VitalsCardOverlay,
        border = BorderStroke(
            width = 1.dp,
            color = if (isListeningSpeech) VitalsTeal.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.14f),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("speech_capture_card"),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isListeningSpeech) VitalsCoral else VitalsTextMuted),
                )
                Text(
                    text = when {
                        livePartialTranscript.isNotBlank() -> "\"$livePartialTranscript\""
                        isListeningSpeech -> stringResource(R.string.stt_listening)
                        else -> stringResource(R.string.stt_idle)
                    },
                    color = if (livePartialTranscript.isNotBlank()) VitalsTextPrimary else VitalsTextMuted,
                    fontSize = 12.sp,
                    fontWeight = if (livePartialTranscript.isNotBlank()) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (recentLogs.isNotEmpty()) {
                    val latest = recentLogs.first()
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (latest.isLie) VitalsCoral.copy(alpha = 0.22f) else VitalsMint.copy(alpha = 0.2f),
                        modifier = Modifier
                            .clickable { onOpenHistoryClick() }
                            .testTag("recent_log_badge"),
                    ) {
                        Text(
                            text = if (latest.isLie) "LIE (${latest.deceptionScore}%)" else "TRUTH (${latest.deceptionScore}%)",
                            color = if (latest.isLie) VitalsCoral else VitalsMint,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = manualInput,
                    onValueChange = onManualInputChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.manual_statement_hint),
                            fontSize = 12.sp,
                            color = VitalsTextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = VitalsTextPrimary,
                        fontSize = 12.sp,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSubmitManualStatement() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VitalsTeal,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
                        cursorColor = VitalsTeal,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("statement_input_field"),
                )

                Button(
                    onClick = onSubmitManualStatement,
                    enabled = manualInput.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VitalsTeal,
                        contentColor = Color.Black,
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .sizeIn(minWidth = 64.dp, minHeight = 48.dp)
                        .testTag("log_statement_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.action_log_statement),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.action_log_statement),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryReviewContent(
    historyState: UiState<List<StatementLog>>,
    selectedFilter: HistoryFilter,
    onFilterChange: (HistoryFilter) -> Unit,
    onDeleteStatement: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (historyState) {
            is UiState.Loading -> {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    CircularProgressIndicator(color = VitalsTeal)
                }
            }

            is UiState.Error -> {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = VitalsCardOverlay,
                    border = BorderStroke(1.dp, VitalsCoral.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = historyState.message,
                        color = VitalsCoral,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            }

            is UiState.Success -> {
                val allLogs = historyState.data
                val liesCount = allLogs.count { it.isLie }
                val truthsCount = allLogs.size - liesCount
                val avgScore = if (allLogs.isNotEmpty()) {
                    allLogs.map { it.deceptionScore }.average().roundToInt()
                } else {
                    0
                }

                // Summary Statistics Banner
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SummaryStatPill(
                        label = "Total Logged",
                        value = allLogs.size.toString(),
                        accent = VitalsTeal,
                        modifier = Modifier.weight(1f),
                    )
                    SummaryStatPill(
                        label = "Flagged Lies",
                        value = liesCount.toString(),
                        accent = VitalsCoral,
                        modifier = Modifier.weight(1f),
                    )
                    SummaryStatPill(
                        label = "Avg Stress",
                        value = "$avgScore%",
                        accent = VitalsAmber,
                        modifier = Modifier.weight(1f),
                    )
                }

                // Filter Chips Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FilterChip(
                        selected = selectedFilter == HistoryFilter.ALL,
                        onClick = { onFilterChange(HistoryFilter.ALL) },
                        label = { Text("${stringResource(R.string.filter_all)} (${allLogs.size})") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VitalsTeal.copy(alpha = 0.25f),
                            selectedLabelColor = VitalsTeal,
                        ),
                        modifier = Modifier.testTag("filter_all_chip"),
                    )
                    FilterChip(
                        selected = selectedFilter == HistoryFilter.LIES_ONLY,
                        onClick = { onFilterChange(HistoryFilter.LIES_ONLY) },
                        label = { Text("${stringResource(R.string.filter_lies)} ($liesCount)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VitalsCoral.copy(alpha = 0.25f),
                            selectedLabelColor = VitalsCoral,
                        ),
                        modifier = Modifier.testTag("filter_lies_chip"),
                    )
                    FilterChip(
                        selected = selectedFilter == HistoryFilter.TRUTHS_ONLY,
                        onClick = { onFilterChange(HistoryFilter.TRUTHS_ONLY) },
                        label = { Text("${stringResource(R.string.filter_truths)} ($truthsCount)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = VitalsMint.copy(alpha = 0.25f),
                            selectedLabelColor = VitalsMint,
                        ),
                        modifier = Modifier.testTag("filter_truths_chip"),
                    )
                }

                val filteredLogs = remember(allLogs, selectedFilter) {
                    when (selectedFilter) {
                        HistoryFilter.ALL -> allLogs
                        HistoryFilter.LIES_ONLY -> allLogs.filter { it.isLie }
                        HistoryFilter.TRUTHS_ONLY -> allLogs.filter { !it.isLie }
                    }
                }

                if (filteredLogs.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = VitalsCardOverlay,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(28.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = null,
                                    tint = VitalsTeal,
                                    modifier = Modifier.size(40.dp),
                                )
                                Text(
                                    text = stringResource(R.string.empty_history_title),
                                    color = VitalsTextPrimary,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                                Text(
                                    text = stringResource(R.string.empty_history_subtitle),
                                    color = VitalsTextMuted,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("history_list"),
                    ) {
                        items(
                            items = filteredLogs,
                            key = { it.id.ifEmpty { it.hashCode().toString() } },
                        ) { item ->
                            StatementHistoryCard(
                                log = item,
                                onDelete = {
                                    if (item.id.isNotBlank()) {
                                        onDeleteStatement(item.id)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryStatPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(VitalsCardOverlay)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp),
    ) {
        Text(
            text = value,
            color = accent,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = label,
            color = VitalsTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun StatementHistoryCard(
    log: StatementLog,
    onDelete: () -> Unit,
) {
    val accent = if (log.isLie) VitalsCoral else VitalsMint
    val formattedTime = remember(log.createdAt, log.updatedAt) {
        val ts = log.createdAt ?: log.updatedAt ?: Timestamp.now()
        SimpleDateFormat("MMM d, h:mm:ss a", Locale.getDefault()).format(ts.toDate())
    }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (log.isLie) Color(0xE62A131C) else VitalsCardOverlay,
        border = BorderStroke(1.dp, accent.copy(alpha = if (log.isLie) 0.7f else 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("statement_log_card_${log.id}"),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = accent.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.6f)),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = if (log.isLie) Icons.Filled.WarningAmber else Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = if (log.isLie) "LIE FLAGGED • ${log.deceptionScore}%" else "TRUTH • ${log.deceptionScore}%",
                                color = accent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }

                    Text(
                        text = formattedTime,
                        color = VitalsTextMuted,
                        fontSize = 11.sp,
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("delete_statement_${log.id}"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = stringResource(R.string.delete_statement_desc),
                        tint = VitalsTextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Text(
                text = "\"${log.transcript}\"",
                color = VitalsTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = log.reason,
                color = accent.copy(alpha = 0.9f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            )

            // Snapshot Telemetry Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                VitalSnapshotChip(
                    label = "Pulse",
                    value = if (log.pulseRate > 0) "${log.pulseRate} bpm" else "--",
                    color = VitalsCoral,
                    modifier = Modifier.weight(1f),
                )
                VitalSnapshotChip(
                    label = "Breath",
                    value = if (log.breathingRate > 0) "${log.breathingRate} bpm" else "--",
                    color = VitalsTeal,
                    modifier = Modifier.weight(1f),
                )
                VitalSnapshotChip(
                    label = "HRV",
                    value = if (log.hrvRmssd > 0.0) "${(log.hrvRmssd * 10).roundToInt() / 10.0} ms" else "--",
                    color = VitalsMint,
                    modifier = Modifier.weight(1f),
                )
                VitalSnapshotChip(
                    label = "Face",
                    value = log.expression.trim(),
                    color = VitalsAmber,
                    modifier = Modifier.weight(1.2f),
                )
            }
        }
    }
}

@Composable
private fun VitalSnapshotChip(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(horizontal = 8.dp, vertical = 5.dp),
    ) {
        Text(
            text = label,
            color = VitalsTextMuted,
            fontSize = 10.sp,
            maxLines = 1,
        )
        Text(
            text = value,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StatusPill(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(VitalsPillBg)
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 90f / 255f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 10.dp),
    ) {
        Text(
            text = text,
            color = VitalsTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    valueColor: Color,
    accent: Color,
    modifier: Modifier = Modifier,
    isMonospace: Boolean = false,
    valueFontSize: Float = 20f,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(VitalsCardOverlay)
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 70f / 255f),
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = title,
            color = VitalsTextMuted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            color = valueColor,
            fontSize = valueFontSize.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = if (isMonospace) FontFamily.Monospace else FontFamily.Default,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WaveformCard(
    title: String,
    samples: List<Float>,
    graphColor: Color,
    graphHeightDp: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(VitalsCardOverlay)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = title,
            color = VitalsTextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.height(4.dp))
        SignalGraph(
            samples = samples,
            graphColor = graphColor,
            contentDesc = title,
            modifier = Modifier
                .fillMaxWidth()
                .height(graphHeightDp.dp),
        )
    }
}

@Composable
private fun SignalGraph(
    samples: List<Float>,
    graphColor: Color,
    contentDesc: String,
    modifier: Modifier = Modifier,
) {
    val gridColor = Color.White.copy(alpha = 12f / 255f)
    val lineColor = graphColor.copy(alpha = 170f / 255f)

    Canvas(
        modifier = modifier.semantics {
            contentDescription = contentDesc
        },
    ) {
        val sampleCount = samples.size
        if (sampleCount < 2) return@Canvas

        val inset = 8f
        val drawLeft = inset
        val drawTop = inset
        val drawRight = size.width - inset
        val drawBottom = size.height - inset
        val drawWidth = drawRight - drawLeft
        val drawHeight = drawBottom - drawTop
        if (drawWidth <= 0f || drawHeight <= 0f) return@Canvas

        for (gridLine in 1..3) {
            val y = drawTop + drawHeight * (1f - gridLine / 4f)
            drawLine(
                color = gridColor,
                start = Offset(drawLeft, y),
                end = Offset(drawRight, y),
                strokeWidth = 1f,
            )
        }

        var minValue = Float.POSITIVE_INFINITY
        var maxValue = Float.NEGATIVE_INFINITY
        samples.forEach { sample ->
            minValue = min(minValue, sample)
            maxValue = max(maxValue, sample)
        }
        val range = if (maxValue - minValue == 0f) 1f else maxValue - minValue

        val path = Path()
        samples.forEachIndexed { index, value ->
            val x = drawLeft + drawWidth * index / (sampleCount - 1)
            val normalized = (value - minValue) / range
            val y = drawBottom - normalized * drawHeight
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(
                width = 3.5f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
