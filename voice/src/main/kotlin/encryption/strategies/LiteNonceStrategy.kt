package dev.kord.voice.encryption.strategies

import dev.kord.voice.EncryptionMode
import dev.kord.voice.io.ByteArrayView
import dev.kord.voice.io.MutableByteArrayCursor
import dev.kord.voice.io.mutableCursor
import dev.kord.voice.io.view
import dev.kord.voice.udp.RTPPacket
import kotlinx.atomicfu.atomic

public class LiteNonceStrategy : NonceStrategy {
    private var count: Int by atomic(0)
    private val nonceBuffer: ByteArray = ByteArray(4)
    private val nonceView = nonceBuffer.view()
    private val nonceCursor = nonceBuffer.mutableCursor()

    override fun strip(packet: RTPPacket): ByteArrayView {
        return with(packet.payload) {
            val nonce = view(dataEnd - 4, dataEnd)!!
            resize(dataStart, dataEnd - 4)
            nonce
        }
    }

    override fun generate(header: () -> ByteArrayView): ByteArrayView {
        count++
        nonceCursor.reset()
        nonceCursor.writeInt(count)
        return nonceView
    }

    override fun append(nonce: ByteArrayView, cursor: MutableByteArrayCursor) {
        cursor.writeByteView(nonce)
    }

    public companion object Factory : NonceStrategy.Factory {
        override val mode: EncryptionMode get() = EncryptionMode.XSalsa20Poly1305Lite
        override val nonceLength: Int get() = 4

        override fun create(): NonceStrategy = LiteNonceStrategy()
    }
}
