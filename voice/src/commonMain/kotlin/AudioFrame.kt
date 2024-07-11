package dev.kord.voice

import dev.kord.common.annotation.KordVoice
import kotlin.jvm.JvmInline
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A frame of Opus-encoded 48k stereo audio data.
 */
@KordVoice
@JvmInline
public value class AudioFrame(public val data: ByteArray) {
    public val sampleCount: UShort
        get() = getPacketSampleCount(SAMPLE_RATE, data).toUShort()

    public companion object {
        public val SILENCE: AudioFrame = AudioFrame(byteArrayOf(0xFC.toByte(), 0xFF.toByte(), 0xFE.toByte()))

        public val DEFAULT_DURATION: Duration = 20.seconds

        /** the sample rate of the opus packet rate. */
        public const val SAMPLE_RATE: Int = 48_000

        /** the number of channels the opus packet should have. */
        public const val NUM_CHANNELS: Int = 2

        public fun fromData(data: ByteArray?): AudioFrame? = data?.let(::AudioFrame)

        public fun getPacketSampleCount(
            sampleRate: Int,
            packet: ByteArray,
        ): Int {
            if (packet.isEmpty()) return 0

            val frameCount = getPacketFrameCount(packet)
            if (frameCount < 0) return 0

            val samples = frameCount * getPacketSamplesPerFrame(sampleRate, packet[0].toInt())
            return if (samples * 25 > sampleRate * 3) 0 else samples
        }

        private fun getPacketFrameCount(slice: ByteArray): Int = when {
            slice[0].toInt() and 0x03 == 0 -> 1
            slice[0].toInt() and 0x03 == 3 -> if (slice.size < 2) -1 else slice[1].toInt() and 0x3F
            else                           -> 2
        }

        private fun getPacketSamplesPerFrame(sampleRate: Int, firstByte: Int): Int {
            val shiftBits = (firstByte shr 3) and 0x03
            return when {
                firstByte and 0x80 != 0    -> (sampleRate shl shiftBits) / 400
                firstByte and 0x60 == 0x60 -> if (firstByte and 0x08 != 0) sampleRate / 50 else sampleRate / 100
                shiftBits == 3             -> sampleRate * 60 / 1000
                else                       -> (sampleRate shl shiftBits) / 100
            }
        }
    }
}