package com.patchcam.app.network

import com.patchcam.app.models.PatchCandidate
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * Stage 6 — Laptop bridge.
 *
 * Sends a validated PatchCandidate to the PatchCam laptop bridge
 * through an OkHttp WebSocket connection.
 */
class LaptopBridgeWebSocket(
    private val host: String,
    private val pairingCode: String
) {

    private val client = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun sendPatch(
        candidate: PatchCandidate,
        /** Name of the source file the error came from, if PatchCam saw it. */
        fileHint: String? = null
    ): Result<String> = suspendCancellableCoroutine { continuation ->

        val cleanHost = host
            .removePrefix("http://")
            .removePrefix("https://")
            .removePrefix("ws://")
            .removePrefix("wss://")

        val wsUrl = "ws://$cleanHost/ws/$pairingCode"

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        val payload = JSONObject().apply {
            put("targetLine", candidate.targetLine)
            put("oldCode", candidate.oldCode)
            put("newCode", candidate.newCode)
            put("description", candidate.description)
            put("verified", candidate.verified)
            put("patch", candidate.newCode)
            if (!fileHint.isNullOrBlank()) put("fileHint", fileHint)
        }

        val webSocket = client.newWebSocket(
            request,
            object : WebSocketListener() {

                override fun onOpen(
                    webSocket: WebSocket,
                    response: Response
                ) {
                    webSocket.send(payload.toString())
                }

                override fun onMessage(
                    webSocket: WebSocket,
                    text: String
                ) {
                    webSocket.close(1000, "Patch sent")

                    /*
                     * The bridge answers with what it actually did
                     * ("Applied to main.py, line 4 ... re-checked ✓"), so
                     * the phone can show the real outcome.
                     */
                    val ack = runCatching { JSONObject(text) }.getOrNull()
                    val status = ack?.optString("status").orEmpty()
                    val message = ack?.optString("message").orEmpty()

                    if (continuation.isActive) {
                        continuation.resume(
                            if (status == "rejected" || status == "error") {
                                Result.failure(
                                    IllegalStateException(
                                        message.ifBlank { "The laptop rejected the patch." }
                                    )
                                )
                            } else {
                                Result.success(
                                    message.ifBlank {
                                        "Patch sent successfully — check the laptop bridge."
                                    }
                                )
                            }
                        )
                    }
                }

                override fun onClosing(
                    webSocket: WebSocket,
                    code: Int,
                    reason: String
                ) {
                    if (code == 4401 && continuation.isActive) {
                        continuation.resume(
                            Result.failure(
                                IllegalStateException(
                                    "Wrong pairing code — check the code shown on the laptop screen."
                                )
                            )
                        )
                    }
                }

                override fun onFailure(
                    webSocket: WebSocket,
                    t: Throwable,
                    response: Response?
                ) {
                    if (continuation.isActive) {
                        continuation.resume(
                            Result.failure(t)
                        )
                    }
                }
            }
        )

        continuation.invokeOnCancellation {
            webSocket.cancel()
        }
    }
}