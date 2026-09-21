package com.patchcam.app.pipeline

import android.content.Context
import com.patchcam.app.models.FeedbackStats
import com.patchcam.app.models.FeedbackVote

/**
 * Remembers, on this phone only, whether PatchCam's fixes actually worked
 * for the user. This is what closes the feedback loop:
 *
 *   Observe -> Diagnose -> user says "worked / didn't" -> Update
 *
 * Votes are counted per fix rule (or per error kind when no patch was
 * produced). The counts nudge the confidence PatchCam reports the next
 * time the same kind of fix comes up.
 */
class FixFeedbackStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun record(key: String, vote: FeedbackVote) {
        bump(key, vote, +1)
    }

    /** Takes back an earlier vote when the user changes their mind. */
    fun undo(key: String, vote: FeedbackVote) {
        bump(key, vote, -1)
    }

    fun stats(key: String): FeedbackStats =
        FeedbackStats(
            worked = prefs.getInt(name(FeedbackVote.WORKED, key), 0),
            partly = prefs.getInt(name(FeedbackVote.PARTLY, key), 0),
            failed = prefs.getInt(name(FeedbackVote.DIDNT_WORK, key), 0)
        )

    /** Everything the user has ever told PatchCam, across all fix types. */
    fun totals(): FeedbackStats {
        var worked = 0
        var partly = 0
        var failed = 0

        for ((k, v) in prefs.all) {
            val count = (v as? Int) ?: continue
            when {
                k.startsWith("w:") -> worked += count
                k.startsWith("p:") -> partly += count
                k.startsWith("f:") -> failed += count
            }
        }

        return FeedbackStats(worked, partly, failed)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun bump(key: String, vote: FeedbackVote, delta: Int) {
        val name = name(vote, key)
        val next = (prefs.getInt(name, 0) + delta).coerceAtLeast(0)
        prefs.edit().putInt(name, next).apply()
    }

    private fun name(vote: FeedbackVote, key: String): String {
        val prefix = when (vote) {
            FeedbackVote.WORKED -> "w:"
            FeedbackVote.PARTLY -> "p:"
            FeedbackVote.DIDNT_WORK -> "f:"
        }
        return prefix + key
    }

    companion object {
        private const val FILE = "patchcam_feedback"

        /**
         * How many confidence points the user's history moves a fix:
         * confirmed fixes earn a little, failed fixes cost more (a wrong
         * fix is worse than a missing one).
         */
        fun confidenceAdjustment(stats: FeedbackStats): Int =
            (stats.worked * 4 + stats.partly - stats.failed * 8).coerceIn(-25, 10)
    }
}
