package com.patchcam.app.network

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.patchcam.app.models.PatchCandidate
import org.json.JSONObject
import java.io.File

/**
 * Second delivery path: iQOO Office Kit (a system feature of OriginOS 6,
 * not a third-party SDK PatchCam can call into). Office Kit itself
 * advertises three ways of moving something from phone to laptop —
 * clipboard sync, file EasyShare, and screen mirroring — so PatchCam uses
 * all three, each for what it's actually good at, instead of routing
 * everything through one generic "Send" button:
 *
 *  - Clipboard sync: the fastest path. The verified patch (as a plain
 *    unified diff) goes on the system clipboard; with Office Kit's
 *    clipboard sync on, it is already on the laptop's clipboard to paste,
 *    no file transfer needed. Best when the laptop just needs one hunk
 *    pasted straight into the editor.
 *  - File EasyShare: the patch is written to a small JSON file and handed
 *    to the share sheet, preferring the Office Kit app directly when it's
 *    installed. On the laptop, the bridge watches the folder Office Kit
 *    saves into (`--office-kit-dir`) and applies the patch exactly as if
 *    it had come over Wi-Fi. Best for the full structured patch + a
 *    backup trail, and works even when phone and laptop can't reach each
 *    other on Wi-Fi directly — typical for locked-down lab machines.
 *  - Screen mirroring: used live during the demo/pitch itself, so the
 *    judges watch the phone scan and the laptop file change on the same
 *    mirrored screen. PatchCam just needs to know this happened so the
 *    Flow screen can show it alongside the other two.
 *
 * Every one of these is logged by OfficeKitSessionTracker so the app has
 * an honest, on-device count of real Office Kit usage instead of a single
 * unverifiable "sent" toast.
 */
object OfficeKitShare {

    const val FILE_SUFFIX = ".patchcam.json"

    /**
     * Best-effort package names for the Office Kit / Vivo Office Kit app
     * across OriginOS builds. When one is installed, the share sheet opens
     * straight into it instead of showing every app on the phone. Not
     * guaranteed to match every device — verify the exact package on the
     * event phones before the demo; if none match, the chooser below still
     * works and just shows the full share sheet instead.
     */
    private val OFFICE_KIT_PACKAGE_CANDIDATES = listOf(
        "com.vivo.officekit",
        "com.vivo.oflinkservice",
        "com.iqoo.officekit"
    )

    private fun installedOfficeKitPackage(context: Context): String? =
        OFFICE_KIT_PACKAGE_CANDIDATES.firstOrNull { pkg ->
            runCatching {
                context.packageManager.getPackageInfo(pkg, 0)
            }.isSuccess
        }

    /** Plain unified-diff text of the patch, suitable for a clipboard paste. */
    fun diffText(candidate: PatchCandidate, fileHint: String?): String {
        val header = fileHint?.let { "--- $it\n+++ $it (PatchCam fix)\n" } ?: ""
        return buildString {
            append(header)
            append("@@ line ${candidate.targetLine} @@\n")
            append("- ").append(candidate.oldCode.trim()).append('\n')
            append("+ ").append(candidate.newCode.trim()).append('\n')
            if (candidate.description.isNotBlank()) {
                append("\n# ").append(candidate.description)
            }
        }
    }

    /**
     * Copies the patch to the system clipboard so it's ready to paste on
     * the laptop the moment Office Kit's clipboard sync picks it up.
     * Returns the text that was copied, for the caller's own confirmation
     * message.
     */
    fun copyToClipboard(context: Context, candidate: PatchCandidate, fileHint: String?): String {
        val text = diffText(candidate, fileHint)
        val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText("PatchCam fix", text))
        return text
    }

    fun buildPatchFile(
        context: Context,
        candidate: PatchCandidate,
        fileHint: String?
    ): File {
        val dir = File(context.cacheDir, "patchcam_outbox").apply { mkdirs() }

        // Only ever keep the most recent few files.
        dir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(5)
            ?.forEach { it.delete() }

        val file = File(dir, "patchcam_fix_${System.currentTimeMillis()}$FILE_SUFFIX")

        val json = JSONObject().apply {
            put("targetLine", candidate.targetLine)
            put("oldCode", candidate.oldCode)
            put("newCode", candidate.newCode)
            put("description", candidate.description)
            put("verified", candidate.verified)
            put("patch", candidate.newCode)
            put("source", "PatchCam Android")
            if (!fileHint.isNullOrBlank()) put("fileHint", fileHint)
        }

        file.writeText(json.toString(2))
        return file
    }

    fun shareIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val send = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Prefer launching Office Kit directly when it's on the phone, so
        // the share sheet doesn't even need to be shown.
        installedOfficeKitPackage(context)?.let { pkg ->
            send.setPackage(pkg)
            if (send.resolveActivity(context.packageManager) != null) {
                return send
            }
            send.setPackage(null)
        }

        return Intent.createChooser(send, "Send with iQOO Office Kit")
    }
}
