package dev.kord.voice

import dev.kord.common.annotation.KordVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * An [AudioFrameProvider] is used to receive frames that are sent to the Discord Voice Server.
 */
@KordVoice
public interface AudioFrameProvider {
    /**
     * Returns a [Flow] of [AudioFrame]s that are emitted at the appropriate interval.
     */
    public fun CoroutineScope.provide(): Flow<AudioFrame?>

    /**
     * A [AudioFrameProvider] that reads [AudioFrame]s from a callback function.
     *
     * @param duration
     */
    public abstract class Callback(private val duration: Duration) : AudioFrameProvider {
        /**
         * Provides a single frame of audio, [AudioFrame].
         *
         * @return the frame of audio.
         */
        public abstract suspend fun read(): AudioFrame?

        /**
         * Polls [AudioFrame]s from the [read] method into a rendezvous channel at the appropriate interval
         * and returns a [Flow] to consume them.
         */
        final override fun CoroutineScope.provide(): Flow<AudioFrame?> {
            val frames = Channel<AudioFrame?>(Channel.RENDEZVOUS)
            launch {
                val mark = TimeSource.Monotonic.markNow()
                var nextFrameTimestamp = mark.elapsedNow().inWholeNanoseconds

                while (isActive) {
                    frames.send(read())

                    nextFrameTimestamp += duration.inWholeNanoseconds
                    delayUntilNextFrameTimestamp(mark.elapsedNow().inWholeNanoseconds, nextFrameTimestamp)
                }
            }

            return frames.consumeAsFlow()
        }

        public companion object {
            private suspend inline fun delayUntilNextFrameTimestamp(now: Long, nextFrameTimestamp: Long) =
                delay(max(0, nextFrameTimestamp - now) / 1_000_000)
        }
    }
}
