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

    private abstract class Common(key: ByteArray) {
        protected val iv = ByteArray(IV_LEN)
        protected val ivCursor = iv.mutableCursor()

        protected val cipherKey = SecretKeySpec(key, "AES")
        protected val cipher: Cipher = Cipher.getInstance("AES/GCM/NoPadding")

        fun apply(
            mode: Int,
            src: ByteArrayView,
            dst: MutableByteArrayCursor,
            nonce: ByteArray,
            header: ByteArrayView,
            writeNonce: MutableByteArrayCursor.(nonce: ByteArray) -> Unit,
        ): Boolean {
            iv.fill(0)
            ivCursor.reset()
            ivCursor.apply { writeNonce(nonce) }

            init(mode)
            cipher.updateAAD(header.data, header.dataStart, header.viewSize)
            dst.cursor += cipher.doFinal(src.data, src.dataStart, src.viewSize, dst.data, dst.cursor)

            return true
        }

        fun init(mode: Int) {
            cipher.init(mode, cipherKey, GCMParameterSpec(AUTH_TAG_LEN * 8, iv, 0, IV_LEN))
        }
    }

    private class BoxImpl(key: ByteArray) : Box, Common(key) {
        private val nonceBuffer: ByteArray = ByteArray(NONCE_LEN)
        private val nonceCursor by lazy { nonceBuffer.mutableCursor() }
        private val nonceView by lazy { nonceBuffer.view() }

        override val overhead: Int
            get() = AUTH_TAG_LEN + NONCE_LEN

        override fun apply(
            src: ByteArrayView,
            dst: MutableByteArrayCursor,
            nonce: ByteArray,
            header: ByteArrayView,
        ): Boolean = apply(Cipher.ENCRYPT_MODE, src, dst, nonce, header, MutableByteArrayCursor::writeByteArray)

        override fun appendNonce(nonce: ByteArrayView, dst: MutableByteArrayCursor) {
            dst.writeByteView(nonce)
        }

        override fun generateNonce(header: () -> ByteArrayView): ByteArrayView {
            nonceCursor.reset()
            nonceCursor.writeByteView(header().view(0, NONCE_LEN)!!)
            return nonceView
        }
    }

    private class UnboxImpl(key: ByteArray) : Unbox, Common(key) {
        override fun apply(
            src: ByteArrayView,
            dst: MutableByteArrayCursor,
            nonce: ByteArray,
            header: ByteArrayView,
        ): Boolean = apply(Cipher.DECRYPT_MODE, src, dst, nonce, header) { writeByteView(it.view(0, NONCE_LEN)!!) }

        override fun getNonce(packet: RTPPacket): ByteArrayView = with(packet.payload) {
            val nonce = view(dataEnd - 4, dataEnd)!!
            resize(0, dataEnd - 4)

            return nonce
        }
    }
}
