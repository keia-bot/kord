package dev.kord.voice.encryption

import com.iwebpp.crypto.TweetNaclFast
import dev.kord.voice.io.ByteArrayView
import dev.kord.voice.io.MutableByteArrayCursor
import dev.kord.voice.io.mutableCursor

public class XSalsa20Poly1305Codec(public val key: ByteArray) {
    private val encryption = XSalsa20Poly1305Encryption(key)

    public fun encrypt(
        message: ByteArrayView,
        nonce: ByteArray,
        output: MutableByteArrayCursor,
    ): Boolean =
        encryption.box(message, nonce, output)

    public fun decrypt(
        box: ByteArrayView,
        nonce: ByteArray,
        output: MutableByteArrayCursor,
    ): Boolean = encryption.open(box, nonce, output)
}

public fun XSalsa20Poly1305Codec.encrypt(
    message: ByteArrayView,
    nonce: ByteArray,
): ByteArray? {
    val buffer = ByteArray(message.viewSize + TweetNaclFast.SecretBox.boxzerobytesLength)
    if (!encrypt(message, nonce, buffer.mutableCursor())) return null
    return buffer
}

public fun XSalsa20Poly1305Codec.decrypt(
    box: ByteArrayView,
    nonce: ByteArray,
): ByteArray? {
    val buffer = ByteArray(box.viewSize - TweetNaclFast.SecretBox.boxzerobytesLength)
    if (!decrypt(box, nonce, buffer.mutableCursor())) return null
    return buffer
}