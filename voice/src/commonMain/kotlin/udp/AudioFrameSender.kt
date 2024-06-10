@file:Suppress("ArrayInDataClass")

package dev.kord.voice.udp

import dev.kord.common.annotation.KordVoice
import dev.kord.voice.AudioFrameInterceptor
import dev.kord.voice.AudioFrameProvider
import dev.kord.voice.FrameInterceptorConfiguration
import dev.kord.voice.encryption.VoiceEncryption

@KordVoice
public data class AudioFrameSenderConfiguration(
    val server: SocketAddress,
    val socket: VoiceUdpSocket,
    val ssrc: UInt,
    val key: ByteArray,
    val interceptorConfiguration: FrameInterceptorConfiguration,
)

@KordVoice
public interface AudioFrameSender {
    /**
     * This should start polling frames from [the audio provider][DefaultAudioFrameSenderData.provider] and
     * send them to Discord.
     */
    public suspend fun start(configuration: AudioFrameSenderConfiguration)
}

/**
 *
 */
public fun interface AudioFrameSenderFactory {
    /**
     *
     */
    public fun create(
        frameInterceptor: AudioFrameInterceptor,
        frameProvider: AudioFrameProvider,
        voiceEncryption: VoiceEncryption,
    ): AudioFrameSender

    public companion object {
        /**
         *
         */
        public val Default: AudioFrameSenderFactory
            get() = AudioFrameSenderFactory(::DefaultAudioFrameSender)
    }
}
