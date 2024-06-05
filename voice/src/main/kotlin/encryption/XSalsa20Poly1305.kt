package dev.kord.voice.encryption

import com.iwebpp.crypto.TweetNaclFast
import dev.kord.voice.EncryptionMode
import dev.kord.voice.encryption.VoiceEncryption.Box
import dev.kord.voice.encryption.VoiceEncryption.Unbox
import dev.kord.voice.encryption.strategies.LiteNonceStrategy
import dev.kord.voice.encryption.strategies.NonceStrategy
import dev.kord.voice.io.ByteArrayView
import dev.kord.voice.io.MutableByteArrayCursor
import dev.kord.voice.udp.RTPPacket

/**
 * An [encryption method][VoiceEncryption] that uses the XSalsa20 stream cipher and Poly1035 hash function.
 */
public data class XSalsa20Poly1305(public val nonceStrategyFactory: NonceStrategy.Factory = LiteNonceStrategy) : VoiceEncryption {
    override val supportsDecryption: Boolean get() = true
    override val mode: EncryptionMode get() = nonceStrategyFactory.mode
    override val nonceLength: Int get() = 24

    override fun createBox(key: ByteArray): Box = object : Box {
        private val codec: XSalsa20Poly1305Codec = XSalsa20Poly1305Codec(key)
        private val nonceStrategy: NonceStrategy = nonceStrategyFactory.create()

        override val overhead: Int
            get() = TweetNaclFast.SecretBox.boxzerobytesLength + nonceStrategyFactory.length

        override fun apply(src: ByteArrayView, dst: MutableByteArrayCursor, nonce: ByteArray, header: ByteArrayView): Boolean =
            codec.encrypt(src, nonce, dst)

        override fun generateNonce(header: () -> ByteArrayView): ByteArrayView =
            nonceStrategy.generate(header)

        override fun appendNonce(nonce: ByteArrayView, dst: MutableByteArrayCursor) =
            nonceStrategy.append(nonce, dst)
    }

    override fun createUnbox(key: ByteArray): Unbox = object : Unbox {
        private val codec: XSalsa20Poly1305Codec = XSalsa20Poly1305Codec(key)
        private val nonceStrategy: NonceStrategy = nonceStrategyFactory.create()

        override fun apply(src: ByteArrayView, dst: MutableByteArrayCursor, nonce: ByteArray, header: ByteArrayView): Boolean =
            codec.decrypt(src, nonce, dst)

        override fun getNonce(packet: RTPPacket): ByteArrayView =
            nonceStrategy.strip(packet)
    }
}
