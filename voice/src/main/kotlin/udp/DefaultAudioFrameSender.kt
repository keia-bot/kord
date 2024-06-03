package dev.kord.voice.udp

import dev.kord.common.annotation.KordVoice
import dev.kord.voice.AudioFrame
import dev.kord.voice.AudioFrameInterceptor
import dev.kord.voice.AudioFrameProvider
import dev.kord.voice.encryption.VoiceEncryption
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

@KordVoice
public data class DefaultAudioFrameSender(
    val frameInterceptor: AudioFrameInterceptor,
    val frameProvider: AudioFrameProvider,
    val voiceEncryption: VoiceEncryption,
    val voiceSocket: VoiceUdpSocket,
) : AudioFrameSender {
    override suspend fun start(configuration: AudioFrameSenderConfiguration): Unit = coroutineScope {
        val frames = Channel<AudioFrame?>(Channel.RENDEZVOUS)
        with(frameProvider) {
            launch { provideFrames(frames) }
        }

        var sequence: UShort = Random.nextBits(UShort.SIZE_BITS).toUShort()
        val packetProvider = DefaultAudioPacketProvider(configuration.key, voiceEncryption)

        log.trace { "audio poller starting." }

        try {
            with(frameInterceptor) {
                frames.consumeAsFlow()
                    .intercept(configuration.interceptorConfiguration)
                    .filterNotNull()
                    .map { packetProvider.provide(sequence, sequence * 960u, configuration.ssrc, it.data) }
                    .map { Datagram(ByteReadPacket(it.data, it.dataStart, it.viewSize), configuration.server) }
                    .onEach(voiceSocket::send)
                    .onEach { sequence++ }
                    .collect()
            }
        } catch (e: Exception) {
            log.trace(e) { "poller stopped with reason" }
            /* we're done polling, nothing to worry about */
        }
    }

    public companion object {
        private val log = KotlinLogging.logger { }
    }
}
