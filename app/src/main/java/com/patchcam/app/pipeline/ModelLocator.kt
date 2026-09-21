package com.patchcam.app.pipeline

import android.content.Context
import java.io.File

/**
 * Resolves where PatchCam's on-device LLM (.task) file actually lives.
 *
 * BACKGROUND — why this exists:
 * The previous implementation hard-coded a single path,
 * "/data/local/tmp/gemma-2b-it-cpu.task", in both MainActivity and
 * MediaPipeLlmInference. That path is a developer convenience: it's only
 * reachable by manually running `adb push` on a debuggable device, it does
 * not survive an app reinstall consistently, and on many stock (non-rooted)
 * devices `/data/local/tmp` is not guaranteed readable by a regular app's
 * process depending on OS/SELinux policy. For anyone who isn't the
 * developer with a USB cable plugged in, the model file simply never
 * exists there — which is the actual reason chat/patch generation was
 * "always" falling back to a generic failure message. It had nothing to do
 * with tokenizer/prompt/inference bugs; the model was never found in the
 * first place.
 *
 * The Gemma .task file is multiple GB, far too large to bundle as an APK
 * asset, so PatchCam still needs it to be provisioned onto the device
 * separately from the Play Store install. This locator makes that
 * provisioning story actually usable for a real (non-developer) install
 * by preferring the app's own external-files directory — a location any
 * installer/onboarding flow, or a one-time `adb push` aimed at THIS app's
 * sandbox (no root required), can write to — while still honoring the old
 * /data/local/tmp path for developers who already use it.
 */
object ModelLocator {

    const val MODEL_FILE_NAME = "gemma-2b-it-cpu.task"

    private const val LEGACY_DEBUG_PATH = "/data/local/tmp/$MODEL_FILE_NAME"

    /**
     * The directory a real provisioning flow (first-run download, or a
     * one-time `adb push` targeted at this app) should place the model
     * file into. Every installed copy of the app can read/write here
     * without root.
     */
    fun preferredModelDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun preferredModelFile(context: Context): File =
        File(preferredModelDirectory(context), MODEL_FILE_NAME)

    /**
     * Returns the first location that actually contains the model file,
     * preferring the app-owned directory over the legacy debug path.
     * Returns null if the model isn't provisioned anywhere PatchCam knows
     * to look.
     */
    fun resolveModelFile(context: Context): File? {
        val preferred = preferredModelFile(context)
        if (preferred.exists() && preferred.length() > 0) {
            return preferred
        }

        val legacy = File(LEGACY_DEBUG_PATH)
        if (legacy.exists() && legacy.length() > 0) {
            return legacy
        }

        return null
    }

    fun isModelAvailable(context: Context): Boolean =
        resolveModelFile(context) != null
}
