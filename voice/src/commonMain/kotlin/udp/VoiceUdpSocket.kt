package dev.kord.voice.udp

import dev.kord.common.annotation.KordVoice
import dev.kord.voice.io.ByteArrayView
import io.ktor.utils.io.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlin.coroutines.EmptyCoroutineContext

@KordVoice
public expect class SocketAddress(hostname: String, port: Int) {
    public val hostname: String

    public val port: Int
}

/**
 * A global [connectVoiceUdpSocket] for all [dev.kord.voice.VoiceConnection]s, unless specified otherwise.
 * Initiated once and kept open for the lifetime of this process.
 */
@KordVoice
public expect val GlobalVoiceUdpSocket: VoiceUdpSocket

public expect fun connectVoiceUdpSocket(address: SocketAddress): VoiceUdpSocket

@KordVoice
public interface VoiceUdpSocket {
    public val scope: CoroutineScope

    public fun all(address: SocketAddress): Flow<ByteReadPacket>

    public suspend fun send(address: SocketAddress, packet: ByteArrayView): Unit

    public suspend fun stop()

    public companion object {
        private object None : VoiceUdpSocket {
            override val scope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)

            override fun all(address: SocketAddress): Flow<ByteReadPacket> = emptyFlow()

            override suspend fun send(address: SocketAddress, packet: ByteArrayView) {}

            override suspend fun stop() {}
        }

        public fun none(): VoiceUdpSocket = None
    }
}

public fun interface VoiceUdpSocketFactory {
    public suspend fun connect(to: SocketAddress): VoiceUdpSocket

    public data object Default : VoiceUdpSocketFactory {
        override suspend fun connect(to: SocketAddress): VoiceUdpSocket = connectVoiceUdpSocket(to)
    }

    public data object Global : VoiceUdpSocketFactory {
        override suspend fun connect(to: SocketAddress): VoiceUdpSocket = GlobalVoiceUdpSocket
    }
}

public suspend fun VoiceUdpSocket.recv(address: SocketAddress): ByteReadPacket = all(address).first()
