package dev.kord.voice.udp

import dev.kord.common.annotation.KordVoice
import dev.kord.voice.AudioFrame
import dev.kord.voice.AudioFrameInterceptor
import dev.kord.voice.AudioFrameProvider
import dev.kord.voice.encryption.VoiceEncryption
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlin.random.Random

@KordVoice
public data class DefaultAudioFrameSender(
    val frameInterceptor: AudioFrameInterceptor,
    val frameProvider: AudioFrameProvider,
    val voiceEncryption: VoiceEncryption,
) : AudioFrameSender {
    override suspend fun start(configuration: AudioFrameSenderConfiguration): Unit = coroutineScope {
        val frames: Flow<AudioFrame?> = with(frameProvider) {
            provide()
        }

        log.trace { "audio poller starting." }

        try {
            var sequence: UShort = Random.nextBits(UShort.SIZE_BITS).toUShort()

            val packetProvider = DefaultAudioPacketProvider(configuration.key, voiceEncryption)

            with(frameInterceptor) {
                frames.intercept(configuration.interceptorConfiguration)
                    .filterNotNull()
                    .map { packetProvider.provide(sequence, sequence * it.sampleCount, configuration.ssrc, it.data) }
                    .onEach { configuration.socket.send(configuration.server, it) }
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
