package dev.kord.voice.encryption

import com.ionspin.kotlin.crypto.secretbox.SecretBox
import dev.kord.voice.EncryptionMode
import dev.kord.voice.encryption.strategies.NonceStrategy
import dev.kord.voice.io.ByteArrayView
import dev.kord.voice.io.MutableByteArrayCursor
import dev.kord.voice.udp.RTPPacket

public actual fun XSalsa20Poly1305(nonceStrategyFactory: NonceStrategy.Factory): VoiceEncryption =
    XSalsa20Poly1305Impl(nonceStrategyFactory)

private class XSalsa20Poly1305Impl(public val nonceStrategyFactory: NonceStrategy.Factory) : VoiceEncryption {
    override val nonceLength: Int
        get() = 24

    override val mode: EncryptionMode
        get() = nonceStrategyFactory.mode

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun createBox(key: ByteArray): VoiceEncryption.Box = object : VoiceEncryption.Box {
        private val uKey = key.toUByteArray()
        private val nonceStrategy: NonceStrategy = nonceStrategyFactory.create()

        override val overhead: Int
            get() = 16 + nonceStrategyFactory.length

        override fun apply(
            src: ByteArrayView,
            dst: MutableByteArrayCursor,
            aead: ByteArrayView,
            nonce: ByteArray,
        ): Boolean = try {
            val result =
                SecretBox.easy(src.toByteArray().toUByteArray(), nonce.toUByteArray(), uKey).toByteArray()

            dst.writeByteArray(result)
            true
        } catch (ex: Throwable) {
            false
        }

        override fun generateNonce(header: () -> ByteArrayView): ByteArrayView =
            nonceStrategy.generate(header)

        override fun appendNonce(nonce: ByteArrayView, dst: MutableByteArrayCursor): Unit =
            nonceStrategy.append(nonce, dst)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    override fun createUnbox(key: ByteArray): VoiceEncryption.Unbox = object : VoiceEncryption.Unbox {
        private val uKey = key.toUByteArray()
        private val nonceStrategy: NonceStrategy = nonceStrategyFactory.create()

        override fun apply(
            src: ByteArrayView,
            dst: MutableByteArrayCursor,
            aead: ByteArrayView,
            nonce: ByteArray,
        ): Boolean = try {
            val result =
                SecretBox.openEasy(src.toByteArray().toUByteArray(), nonce.toUByteArray(), uKey).toByteArray()

            dst.writeByteArray(result)
            true
        } catch (ex: Throwable) {
            false
        }

        override fun getNonce(packet: RTPPacket): ByteArrayView = nonceStrategy.strip(packet)
    }
}