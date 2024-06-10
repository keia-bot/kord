package dev.kord.voice.encryption

import dev.kord.voice.EncryptionMode
import dev.kord.voice.io.ByteArrayView
import dev.kord.voice.io.MutableByteArrayCursor
import dev.kord.voice.udp.RTPPacket

public interface VoiceEncryption {
    public val nonceLength: Int

    public val mode: EncryptionMode

    public fun createBox(key: ByteArray): Box

    public fun createUnbox(key: ByteArray): Unbox

    public sealed interface Method {
        public fun apply(
            src: ByteArrayView,
            dst: MutableByteArrayCursor,
            aead: ByteArrayView,
            nonce: ByteArray,
        ): Boolean
    }

    /**
     *
     */
    public interface Box : Method {
        public val overhead: Int

        public fun generateNonce(header: () -> ByteArrayView): ByteArrayView

        public fun appendNonce(nonce: ByteArrayView, dst: MutableByteArrayCursor)
    }

    /**
     *
     */
    public interface Unbox : Method {
        /**
         * Strip the nonce from the [RTP packet][packet].
         *
         * @return the nonce.
         */
        public fun getNonce(packet: RTPPacket): ByteArrayView
    }
}
