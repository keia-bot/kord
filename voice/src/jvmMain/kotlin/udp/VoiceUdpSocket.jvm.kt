package dev.kord.voice.udp

import dev.kord.common.annotation.KordVoice
import dev.kord.voice.io.ByteArrayView
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import io.ktor.network.sockets.Datagram as KtorDatagram

@KordVoice
public actual typealias SocketAddress = InetSocketAddress

@KordVoice
public actual val GlobalVoiceUdpSocket: VoiceUdpSocket = object : UdpSocket() {
    override val socket = aSocket(SelectorManager(scope.coroutineContext)).udp().bind()

    init {
        listen()
    }
}

public actual fun connectVoiceUdpSocket(address: SocketAddress): VoiceUdpSocket = object : UdpSocket() {
    override val socket = aSocket(SelectorManager(scope.coroutineContext))
        .udp()
        .connect(address)

    init {
        listen()
    }

    override suspend fun stop() {
        socket.dispose()
        scope.cancel()
    }
}

private abstract class UdpSocket : VoiceUdpSocket {
    override val scope =
        CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineName("kord-voice-socket"))

    protected abstract val socket: DatagramReadWriteChannel

    private val incoming: MutableSharedFlow<KtorDatagram> = MutableSharedFlow()

    override fun all(address: SocketAddress): Flow<ByteReadPacket> {
        return incoming
            .filter { it.address == address }
            .map { it.packet }
    }

    override suspend fun send(address: SocketAddress, packet: ByteArrayView) {
        val brp = ByteReadPacket(packet.data, packet.dataStart, packet.viewSize)
        socket.send(KtorDatagram(brp, address))
    }

    override suspend fun stop() {
    }

    protected fun listen() {
        socket.incoming.consumeAsFlow()
            .onEach { incoming.emit(it) }
            .launchIn(scope)
    }
}