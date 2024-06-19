package dev.kord.voice.handlers

import dev.kord.voice.FrameInterceptorConfiguration
import dev.kord.voice.VoiceConnection
import dev.kord.voice.gateway.*
import dev.kord.voice.streams.StreamsConfig
import dev.kord.voice.udp.AudioFrameSenderConfiguration
import dev.kord.voice.udp.SocketAddress
import dev.kord.voice.udp.VoiceUdpSocket
import dev.kord.voice.udp.discoverIP
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private val udpLifeCycleLogger = KotlinLogging.logger { }

internal class UdpLifeCycleHandler(
    flow: Flow<VoiceEvent>,
    private val connection: VoiceConnection,
) : ConnectionEventHandler<VoiceEvent>(flow, "UdpInterceptor") {
    private var ssrc: UInt? by atomic(null)

    private var server: SocketAddress? by atomic(null)

    private var socket: VoiceUdpSocket? by atomic(null)

    private var streamsJob: Job? by atomic(null)

    private var audioSenderJob: Job? by atomic(null)

    @OptIn(ExperimentalUnsignedTypes::class)
    override suspend fun start() = coroutineScope {
        on<Ready> {
            ssrc = it.ssrc
            server = SocketAddress(it.ip, it.port)
            socket = connection.rtcConfig.voiceSocketFactory.connect(server!!)

            val ip: SocketAddress = socket!!.discoverIP(
                connection.rtcConfig,
                server!!,
                ssrc!!.toInt()
            )

            udpLifeCycleLogger.trace { "ip discovered for voice successfully" }

            val selectProtocol = SelectProtocol(
                protocol = "udp",
                data = SelectProtocol.Data(
                    address = ip.hostname,
                    port = ip.port,
                    mode = connection.rtcConfig.voiceEncryption.mode
                )
            )

            connection.voiceGateway.send(selectProtocol)
        }

        on<SessionDescription> {
            val key = it.secretKey.toUByteArray().toByteArray()

            // launch streams job.
            with(connection) {
                val config = StreamsConfig(key = key, server = server!!, socket = socket!!)
                streamsJob?.cancel()
                streamsJob = launch { streams.listen(config) }
            }

            // launch audio sender job.
            with(connection) {
                val config = AudioFrameSenderConfiguration(
                    ssrc = ssrc!!,
                    key = key,
                    server = server!!,
                    socket = socket!!,
                    interceptorConfiguration = FrameInterceptorConfiguration(gateway, voiceGateway, ssrc!!)
                )

                audioSenderJob?.cancel()
                audioSenderJob = launch { frameSender.start(config) }
            }
        }

        on<Close> {
            socket?.stop()
            socket = null

            streamsJob?.cancel()
            streamsJob = null

            audioSenderJob?.cancel()
            audioSenderJob = null
        }
    }
}
