package com.patchcam.app.network

import android.content.Context
import com.patchcam.app.models.OfficeKitChannel
import com.patchcam.app.models.OfficeKitStats

/**
 * Counts real taps on each of PatchCam's three Office Kit touchpoints, on
 * this phone only. There is no public Office Kit SDK for a third-party app
 * to call into (Office Kit is a system feature of OriginOS 6 — clipboard
 * sync, file EasyShare, and screen mirroring between phone and laptop), so
 * this tracker is PatchCam's own honest log of when the user actually used
 * one of those three surfaces during a debugging session, rather than a
 * claim about Office Kit's internal state.
 *
 * Kept for two reasons:
 *  1. It gives the Flow/Result screens a real number to show ("Office Kit
 *     used 6 times this session") instead of a one-shot "sent" toast.
 *  2. It's the honest record behind the demo pitch — "here's what we
 *     actually did with Office Kit", not just a claim in a slide.
 */
class OfficeKitSessionTracker(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun record(channel: OfficeKitChannel) {
        val now = System.currentTimeMillis()

        val countKey = countKey(channel)
        prefs.edit()
            .putInt(countKey, prefs.getInt(countKey, 0) + 1)
            .putLong(KEY_LAST, now)
            .apply()

        if (prefs.getLong(KEY_FIRST, 0L) == 0L) {
            prefs.edit().putLong(KEY_FIRST, now).apply()
        }
    }

    fun stats(): OfficeKitStats =
        OfficeKitStats(
            clipboardSyncs = prefs.getInt(countKey(OfficeKitChannel.CLIPBOARD_SYNC), 0),
            fileShares = prefs.getInt(countKey(OfficeKitChannel.FILE_SHARE), 0),
            mirrorSessions = prefs.getInt(countKey(OfficeKitChannel.SCREEN_MIRROR), 0),
            firstEventAt = prefs.getLong(KEY_FIRST, 0L),
            lastEventAt = prefs.getLong(KEY_LAST, 0L)
        )

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun countKey(channel: OfficeKitChannel) = "count:${channel.name}"

    companion object {
        private const val FILE = "patchcam_office_kit"
        private const val KEY_FIRST = "first_event_at"
        private const val KEY_LAST = "last_event_at"
    }
}
