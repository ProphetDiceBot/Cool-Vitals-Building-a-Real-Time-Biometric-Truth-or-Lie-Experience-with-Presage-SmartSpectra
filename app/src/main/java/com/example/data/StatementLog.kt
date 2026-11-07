package com.example.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@IgnoreExtraProperties
data class StatementLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val transcript: String = "",
    @get:PropertyName("isLie") val isLie: Boolean = false,
    val deceptionScore: Long = 0L,
    val verdict: String = "TRUTH",
    val pulseRate: Long = 0L,
    val breathingRate: Long = 0L,
    val hrvRmssd: Double = 0.0,
    val expression: String = "Neutral",
    val reason: String = "Normal physiological baseline",
    val notes: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

data class DeceptionAnalysis(
    val isLie: Boolean,
    val deceptionScore: Int,
    val verdict: String,
    val reason: String,
)

object DeceptionEvaluator {
    private val highStressExpressions = setOf(
        "Fear",
        "Angry",
        "Contempt",
        "Disgust",
        "Surprise",
        "Sad",
    )

    private val deceptivePhraseCues = listOf(
        "i swear",
        "honestly",
        "to be honest",
        "believe me",
        "never",
        "i didn't",
        "i did not",
        "nothing",
        "no idea",
    )

    fun evaluate(
        transcript: String,
        pulseRate: Int?,
        baselinePulse: Double?,
        breathingRate: Int?,
        baselineBreathing: Double?,
        hrvRmssd: Double?,
        baselineHrv: Double?,
        expressionName: String?,
        expressionConfidence: Int?,
    ): DeceptionAnalysis {
        var score = 18
        val triggers = mutableListOf<String>()

        // 1. Cardiovascular Pulse Rate Analysis
        if (pulseRate != null && pulseRate > 0) {
            val baseHr = baselinePulse?.takeIf { it > 40.0 } ?: 74.0
            val deltaHr = pulseRate - baseHr
            when {
                deltaHr >= 14 || pulseRate >= 100 -> {
                    score += 32
                    triggers.add("Pulse spike ($pulseRate bpm, +${deltaHr.roundToInt()} vs baseline)")
                }
                deltaHr >= 7 || pulseRate >= 90 -> {
                    score += 20
                    triggers.add("Elevated pulse ($pulseRate bpm)")
                }
                else -> {
                    score -= 4
                }
            }
        }

        // 2. Respiratory Rate Analysis
        if (breathingRate != null && breathingRate > 0) {
            val baseRr = baselineBreathing?.takeIf { it > 6.0 } ?: 15.0
            val deltaRr = breathingRate - baseRr
            when {
                deltaRr >= 5 || breathingRate >= 21 || breathingRate <= 9 -> {
                    score += 22
                    triggers.add("Irregular respiration ($breathingRate bpm)")
                }
                deltaRr >= 3 || breathingRate >= 18 -> {
                    score += 12
                    triggers.add("Accelerated breathing ($breathingRate bpm)")
                }
            }
        }

        // 3. Heart Rate Variability (RMSSD) Autonomic Stress Analysis
        if (hrvRmssd != null && hrvRmssd > 0.0) {
            val baseHrv = baselineHrv?.takeIf { it > 10.0 } ?: 42.0
            val dropRatio = (baseHrv - hrvRmssd) / baseHrv
            when {
                hrvRmssd < 24.0 || dropRatio >= 0.25 -> {
                    score += 24
                    triggers.add("Suppressed HRV (${(hrvRmssd * 10).roundToInt() / 10.0} ms)")
                }
                hrvRmssd < 32.0 || dropRatio >= 0.15 -> {
                    score += 14
                    triggers.add("Reduced HRV (${(hrvRmssd * 10).roundToInt() / 10.0} ms)")
                }
                else -> {
                    score -= 5
                }
            }
        }

        // 4. Facial Micro-Expression Analysis
        if (!expressionName.isNullOrBlank() && expressionName in highStressExpressions) {
            val conf = expressionConfidence ?: 60
            if (conf >= 40) {
                score += if (conf >= 70) 24 else 15
                triggers.add("Micro-expression: $expressionName ($conf%)")
            }
        }

        // 5. Linguistic hedging cues when vitals are still calibrating or elevated
        val normalizedText = transcript.lowercase()
        val matchedCue = deceptivePhraseCues.firstOrNull { normalizedText.contains(it) }
        if (matchedCue != null) {
            val noLiveVitalsYet = (pulseRate == null || pulseRate == 0) &&
                (breathingRate == null || breathingRate == 0)
            score += if (noLiveVitalsYet) 45 else 14
            triggers.add("Verbal stress marker (\"$matchedCue\")")
        }

        val clampedScore = min(100, max(5, score))
        val isLie = clampedScore >= 55
        val verdict = if (isLie) "LIE" else "TRUTH"
        val reason = if (triggers.isNotEmpty()) {
            triggers.joinToString(" • ").take(480)
        } else if (isLie) {
            "Elevated autonomic stress signature detected"
        } else {
            "Stable cardiovascular & respiratory baseline"
        }

        return DeceptionAnalysis(
            isLie = isLie,
            deceptionScore = clampedScore,
            verdict = verdict,
            reason = reason,
        )
    }
}
