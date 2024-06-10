package dev.kord.voice.udp

import dev.kord.gateway.retry.LinearRetry
import dev.kord.gateway.retry.Retry
import dev.kord.voice.encryption.AeadAes256Gcm
import dev.kord.voice.encryption.VoiceEncryption
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

public data class RealTimeConnectionConfiguration(
    val ipDiscoveryRetry: Retry,
    val ipDiscoveryTimeout: Duration,
    val voiceEncryption: VoiceEncryption,
    val voiceSocketFactory: VoiceUdpSocketFactory,
) {
    public class Builder {
        /**
         * The [Retry] strategy to use when attempting to discover our Local IP address.
         * By default, a linear retry of 5 tries over 30 seconds will be used.
         */
        public var ipDiscoveryRetry: Retry? = null

        /**
         * The amount of time to wait for an IP discovery response.
         * By default, a timeout of 5 seconds is used.
         */
        public var ipDiscoveryTimeout: Duration = 5.seconds

        /**
         * The nonce strategy to be used for the encryption of audio packets.
         * If `null` & voice receive if disabled, [VoiceEncryption.AeadAes256Gcm] will be used,
         * otherwise [VoiceEncryption.XSalsaPoly1305] with the Lite strategy will be used.
         */
        public var voiceEncryption: VoiceEncryption = AeadAes256Gcm

        /**
         * A [dev.kord.voice.udp.connectVoiceUdpSocket] implementation to be used.
         * By default, [GlobalVoiceUdpSocket] will be used.
         */
        public var voiceSocketFactory: VoiceUdpSocketFactory = VoiceUdpSocketFactory.Global

        public fun voiceSocket(block: suspend (SocketAddress) -> VoiceUdpSocket) {
            this.voiceSocketFactory = VoiceUdpSocketFactory(block)
        }

        public fun build(): RealTimeConnectionConfiguration = RealTimeConnectionConfiguration(
            ipDiscoveryRetry ?: LinearRetry(2.seconds, 30.seconds, 5),
            ipDiscoveryTimeout,
            voiceEncryption,
            voiceSocketFactory
        )
    }
}
