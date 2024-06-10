package dev.kord.voice.udp

import dev.kord.voice.encryption.VoiceEncryption
import dev.kord.voice.io.ByteArrayView
import dev.kord.voice.io.MutableByteArrayCursor
import dev.kord.voice.io.mutableCursor
import dev.kord.voice.io.view
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized

<<<<<<<< HEAD:voice/src/commonMain/kotlin/udp/AudioPacketProvider.kt
public abstract class AudioPacketProvider(public val key: ByteArray, public val encryption: VoiceEncryption) {
    public abstract fun provide(sequence: UShort, timestamp: UInt, ssrc: UInt, data: ByteArray): ByteArrayView
}
========
@Suppress("FunctionName")
public actual fun DefaultAudioPacketProvider(key: ByteArray, nonceStrategy: NonceStrategy) : AudioPacketProvider =
    DefaultJvmAudioPacketProvider(key, nonceStrategy)
>>>>>>>> mainline/feature/native:voice/src/jvmMain/kotlin/dev/kord/voice/udp/DefaultAudioPacketProvider.kt


<<<<<<<< HEAD:voice/src/commonMain/kotlin/udp/AudioPacketProvider.kt
public class DefaultAudioPacketProvider(
    key: ByteArray,
    encryption: VoiceEncryption,
) : AudioPacketProvider(key, encryption) {
    private val box = encryption.createBox(key)
========
public class DefaultJvmAudioPacketProvider(key: ByteArray, nonceStrategy: NonceStrategy) :
    AudioPacketProvider(key, nonceStrategy) {
    private val codec = XSalsa20Poly1305Codec(key)
>>>>>>>> mainline/feature/native:voice/src/jvmMain/kotlin/dev/kord/voice/udp/DefaultAudioPacketProvider.kt

    private val packetBuffer = ByteArray(2048)
    private val packetBufferCursor: MutableByteArrayCursor = packetBuffer.mutableCursor()
    private val packetBufferView: ByteArrayView = packetBuffer.view()

    private val rtpHeaderView: ByteArrayView = packetBuffer.view(0, RTP_HEADER_LENGTH)!!

    private val nonceBuffer: MutableByteArrayCursor = ByteArray(encryption.nonceLength).mutableCursor()

    private val lock: SynchronizedObject = SynchronizedObject()

    override fun provide(sequence: UShort, timestamp: UInt, ssrc: UInt, data: ByteArray): ByteArrayView =
        synchronized(lock) {
            with(packetBufferCursor) {
                this.reset()
                nonceBuffer.reset()

                // make sure we enough room in this buffer
                resize(RTP_HEADER_LENGTH + data.size + box.overhead)

                // write header and generate nonce
                writeHeader(sequence.toShort(), timestamp.toInt(), ssrc.toInt())

                val rawNonce = box.generateNonce { rtpHeaderView }
                nonceBuffer.writeByteView(rawNonce)

                // encrypt data and write into our buffer
                val encrypted = box.apply(data.view(), this, rtpHeaderView, nonceBuffer.data)
                if (!encrypted) throw CouldNotEncryptDataException(data)

                box.appendNonce(rawNonce, this)

                // let's make sure we have the correct view of the packet
                if (!packetBufferView.resize(0, cursor)) error("couldn't resize packet buffer view?!")

                packetBufferView
            }
        }
}
