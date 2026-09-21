package com.patchcam.app.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

/**
 * Finds the PatchCam laptop bridge from the 6-digit pairing code alone.
 *
 * The phone broadcasts "PATCHCAM_DISCOVER <code>" on the local network.
 * The bridge only answers when the code matches, replying with its
 * WebSocket port; the sender's address is the laptop's address. This is
 * why the user never has to type an IP address.
 *
 * Some guest / campus Wi-Fi networks block broadcast between devices. In
 * that case [find] returns null and the app asks for the laptop address.
 */
object LaptopDiscovery {

    private const val DISCOVERY_PORT = 8766

    suspend fun find(pairingCode: String, timeoutMs: Long = 3000): String? =
        withContext(Dispatchers.IO) {

            val socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = 600
            }

            try {
                val payload = "PATCHCAM_DISCOVER $pairingCode".toByteArray()
                val targets = broadcastAddresses()
                val deadline = System.currentTimeMillis() + timeoutMs

                while (System.currentTimeMillis() < deadline) {

                    for (target in targets) {
                        runCatching {
                            socket.send(
                                DatagramPacket(payload, payload.size, target, DISCOVERY_PORT)
                            )
                        }
                    }

                    val buffer = ByteArray(64)
                    val reply = DatagramPacket(buffer, buffer.size)

                    try {
                        socket.receive(reply)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }

                    val text = String(reply.data, 0, reply.length).trim()
                    val parts = text.split(" ")

                    if (parts.size == 2 && parts[0] == "PATCHCAM_HERE") {
                        val port = parts[1].toIntOrNull() ?: continue
                        return@withContext "${reply.address.hostAddress}:$port"
                    }
                }

                null
            } finally {
                socket.close()
            }
        }

    private fun broadcastAddresses(): List<InetAddress> {
        val result = LinkedHashSet<InetAddress>()

        runCatching {
            NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
                .filter { it.isUp && !it.isLoopback }
                .forEach { nic ->
                    nic.interfaceAddresses.forEach { address ->
                        address.broadcast?.let { result.add(it) }
                    }
                }
        }

        runCatching { result.add(InetAddress.getByName("255.255.255.255")) }

        return result.toList()
    }
}
