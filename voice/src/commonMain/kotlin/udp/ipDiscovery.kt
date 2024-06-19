package dev.kord.voice.udp

import dev.kord.voice.io.mutableCursor
import dev.kord.voice.io.view
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.utils.io.core.*
import kotlinx.coroutines.withTimeoutOrNull

private val ipDiscoveryLogger = KotlinLogging.logger { }

private const val MESSAGE_LENGTH: Short = 70
private const val DISCOVERY_HEADER_SIZE = 8
private const val DISCOVERY_DATA_SIZE: Int = 66
private const val DISCOVERY_MESSAGE_SIZE = DISCOVERY_HEADER_SIZE + DISCOVERY_DATA_SIZE

private const val REQUEST: Short = 0x01
private const val RESPONSE: Short = 0x02

@OptIn(ExperimentalUnsignedTypes::class)
public suspend fun VoiceUdpSocket.discoverIP(
    configuration: RealTimeConnectionConfiguration,
    address: SocketAddress,
    ssrc: Int,
): SocketAddress {
    while (configuration.ipDiscoveryRetry.hasNext) {
        ipDiscoveryLogger.trace { "discovering ip" }

        val data = ByteArray(DISCOVERY_MESSAGE_SIZE)
        with(data.mutableCursor()) {
            writeShort(REQUEST)
            writeShort(MESSAGE_LENGTH)
            writeInt(ssrc)
        }

        send(address, data.view())

        val packet = withTimeoutOrNull(configuration.ipDiscoveryTimeout) {
            recvResponse(address, ssrc)
        }

        if (packet == null) {
            configuration.ipDiscoveryRetry.retry()
            continue
        }

        val ip = String(packet.readBytes(64)).trimEnd(0.toChar())
        val port = packet.readUShort().toInt()

        return SocketAddress(ip, port)
    }

    error("Failed to discover local ip address")
}

private suspend fun VoiceUdpSocket.recvResponse(address: SocketAddress, ssrc: Int): ByteReadPacket {
    while (true) {
        val packet = recv(address)
        require(packet.readShort() == RESPONSE) { "did not receive a response." }
        require(packet.readShort() == MESSAGE_LENGTH) { "expected $MESSAGE_LENGTH bytes of data." }

        if (packet.readInt() == ssrc) return packet
    }
}
