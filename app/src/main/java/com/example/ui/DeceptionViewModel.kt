package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DeceptionAnalysis
import com.example.data.DeceptionEvaluator
import com.example.data.StatementLog
import com.example.data.StatementRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

enum class HistoryFilter {
    ALL,
    LIES_ONLY,
    TRUTHS_ONLY,
}

class DeceptionViewModel(
    private val repository: StatementRepository,
    private val currentUserId: String,
) : ViewModel() {

    val historyState: StateFlow<UiState<List<StatementLog>>> = repository.observeStatements(currentUserId)
        .map<List<StatementLog>, UiState<List<StatementLog>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val rootError = firestoreError ?: error
            Log.w(TAG, "Error observing statement logs", rootError)
            emit(UiState.Error(rootError.message ?: "Failed to load statement history"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = UiState.Loading,
        )

    private val _selectedFilter = MutableStateFlow(HistoryFilter.ALL)
    val selectedFilter: StateFlow<HistoryFilter> = _selectedFilter.asStateFlow()

    private val _latestAnalysis = MutableStateFlow<DeceptionAnalysis?>(null)
    val latestAnalysis: StateFlow<DeceptionAnalysis?> = _latestAnalysis.asStateFlow()

    private val _lastLoggedTranscript = MutableStateFlow<String?>(null)
    val lastLoggedTranscript: StateFlow<String?> = _lastLoggedTranscript.asStateFlow()

    private val _errorBanner = MutableStateFlow<String?>(null)
    val errorBanner: StateFlow<String?> = _errorBanner.asStateFlow()

    // Rolling baseline buffers for personalized physiological calibration
    private val pulseHistory = ArrayDeque<Int>()
    private val breathingHistory = ArrayDeque<Int>()
    private val hrvHistory = ArrayDeque<Double>()

    fun setFilter(filter: HistoryFilter) {
        _selectedFilter.value = filter
    }

    fun updatePhysiologicalBaseline(
        pulseRate: Int?,
        breathingRate: Int?,
        hrvRmssd: Double?,
        expressionName: String?,
        expressionConfidence: Int?,
    ) {
        if (pulseRate != null && pulseRate in 40..220) {
            pulseHistory.addLast(pulseRate)
            while (pulseHistory.size > MAX_BASELINE_SAMPLES) pulseHistory.removeFirst()
        }
        if (breathingRate != null && breathingRate in 5..60) {
            breathingHistory.addLast(breathingRate)
            while (breathingHistory.size > MAX_BASELINE_SAMPLES) breathingHistory.removeFirst()
        }
        if (hrvRmssd != null && hrvRmssd in 5.0..250.0) {
            hrvHistory.addLast(hrvRmssd)
            while (hrvHistory.size > MAX_BASELINE_SAMPLES) hrvHistory.removeFirst()
        }

        _latestAnalysis.value = DeceptionEvaluator.evaluate(
            transcript = "",
            pulseRate = pulseRate,
            baselinePulse = pulseHistory.takeIf { it.size >= 3 }?.average(),
            breathingRate = breathingRate,
            baselineBreathing = breathingHistory.takeIf { it.size >= 3 }?.average(),
            hrvRmssd = hrvRmssd,
            baselineHrv = hrvHistory.takeIf { it.size >= 3 }?.average(),
            expressionName = expressionName,
            expressionConfidence = expressionConfidence,
        )
    }

    fun evaluateAndLogStatement(
        transcript: String,
        pulseRate: Int?,
        breathingRate: Int?,
        hrvRmssd: Double?,
        expressionName: String?,
        expressionConfidence: Int?,
        formattedExpression: String,
    ) {
        val cleaned = transcript.trim()
        if (cleaned.isEmpty()) return

        val analysis = DeceptionEvaluator.evaluate(
            transcript = cleaned,
            pulseRate = pulseRate,
            baselinePulse = pulseHistory.takeIf { it.size >= 3 }?.average(),
            breathingRate = breathingRate,
            baselineBreathing = breathingHistory.takeIf { it.size >= 3 }?.average(),
            hrvRmssd = hrvRmssd,
            baselineHrv = hrvHistory.takeIf { it.size >= 3 }?.average(),
            expressionName = expressionName,
            expressionConfidence = expressionConfidence,
        )

        _latestAnalysis.value = analysis
        _lastLoggedTranscript.value = cleaned

        viewModelScope.launch {
            val result = repository.logStatement(
                transcript = cleaned,
                isLie = analysis.isLie,
                deceptionScore = analysis.deceptionScore,
                verdict = analysis.verdict,
                pulseRate = pulseRate ?: 0,
                breathingRate = breathingRate ?: 0,
                hrvRmssd = hrvRmssd ?: 0.0,
                expression = formattedExpression.ifBlank { "Neutral" },
                reason = analysis.reason,
            )
            result.onFailure { e ->
                _errorBanner.value = e.localizedMessage ?: "Failed to save statement log"
            }
        }
    }

    fun deleteStatement(logId: String) {
        viewModelScope.launch {
            repository.deleteStatement(logId).onFailure { e ->
                _errorBanner.value = e.localizedMessage ?: "Failed to delete statement log"
            }
        }
    }

    fun resetSessionBaseline() {
        pulseHistory.clear()
        breathingHistory.clear()
        hrvHistory.clear()
        _latestAnalysis.value = null
    }

    private companion object {
        const val TAG = "DeceptionVM"
        const val STOP_TIMEOUT_MILLIS = 5000L
        const val MAX_BASELINE_SAMPLES = 30
    }
}
