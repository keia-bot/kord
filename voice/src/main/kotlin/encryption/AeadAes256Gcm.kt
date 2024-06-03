package dev.kord.voice.encryption

import dev.kord.voice.EncryptionMode
import dev.kord.voice.encryption.VoiceEncryption.Box
import dev.kord.voice.encryption.VoiceEncryption.Unbox
import dev.kord.voice.io.ByteArrayView
import dev.kord.voice.io.MutableByteArrayCursor
import dev.kord.voice.io.mutableCursor
import dev.kord.voice.io.view
import dev.kord.voice.udp.RTPPacket
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * An [encryption method][VoiceEncryption] that uses AES-256 GCM.
 */
public data object AeadAes256Gcm : VoiceEncryption {
    private const val AUTH_TAG_LEN = 16
    private const val NONCE_LEN = 4
    private const val IV_LEN = 12

    override val supportsDecryption: Boolean get() = true

    override val mode: EncryptionMode get() = EncryptionMode.AeadAes256Gcm

    override val nonceLength: Int get() = 4

    override fun createBox(key: ByteArray): Box = BoxImpl(key)

    override fun createUnbox(key: ByteArray): Unbox = UnboxImpl(key)

    private abstract class Impl(key: ByteArray) {
        protected val iv = ByteArray(IV_LEN)
        protected val ivCursor = iv.mutableCursor()

        protected open val nonceBuffer: ByteArray = ByteArray(NONCE_LEN)
        protected val nonceCursor by lazy { nonceBuffer.mutableCursor() }
        protected val nonceView by lazy { nonceBuffer.view() }

        protected val cipherKey = SecretKeySpec(key, "AES")
        protected val cipher: Cipher = Cipher.getInstance("AES/GCM/NoPadding")

        fun init(mode: Int) {
            cipher.init(mode, cipherKey, GCMParameterSpec(AUTH_TAG_LEN * 8, iv, 0, IV_LEN))
        }
    }

    private class BoxImpl(key: ByteArray) : Box, Impl(key) {
        override val overhead: Int
            get() = AUTH_TAG_LEN + NONCE_LEN

        override fun apply(src: ByteArrayView, nonce: ByteArray, dst: MutableByteArrayCursor): Boolean {
            iv.fill(0)
            ivCursor.reset()
            ivCursor.writeByteArray(nonce)

            init(Cipher.ENCRYPT_MODE)
            cipher.updateAAD(dst.data.copyOfRange(0, dst.cursor))
            dst.cursor += cipher.doFinal(src.data, src.dataStart, src.viewSize, dst.data, dst.cursor)

            return true
        }

        override fun appendNonce(nonce: ByteArrayView, dst: MutableByteArrayCursor) {
            dst.writeByteView(nonce)
        }

        override fun generateNonce(header: () -> ByteArrayView): ByteArrayView {
            nonceCursor.reset()
            nonceCursor.writeByteView(header().view(0, NONCE_LEN)!!)
            return nonceView
        }
    }

    private class UnboxImpl(key: ByteArray) : Unbox, Impl(key) {
        // Since RTPPacket expects a container big enough to fit the entire header this will
        // need to be 12 bytes (or the min length of an RTP header). We will only use the
        // first NONCE_LEN bytes, though.
        override val nonceBuffer: ByteArray = ByteArray(12)

        override fun apply(
            src: ByteArrayView,
            nonce: ByteArray,
            dst: MutableByteArrayCursor,
        ): Boolean {
            iv.fill(0)
            ivCursor.reset()
            ivCursor.writeByteView(nonce.view(0, NONCE_LEN)!!) // only use first NONCE_LEN bytes.

            init(Cipher.ENCRYPT_MODE)
            cipher.init(Cipher.DECRYPT_MODE, cipherKey, GCMParameterSpec(AUTH_TAG_LEN * 8, iv, 0, IV_LEN))
            dst.cursor += cipher.doFinal(src.data, src.dataStart, src.viewSize, dst.data, dst.cursor)

            return true
        }

        override fun getNonce(packet: RTPPacket): ByteArrayView {
            packet.writeHeader(nonceCursor)
            return nonceView
        }
    }
}
