package dev.kord.voice.streams

import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.Snowflake
import dev.kord.voice.AudioFrame
import dev.kord.voice.udp.RTPPacket
import dev.kord.voice.udp.SocketAddress
import dev.kord.voice.udp.VoiceUdpSocket
import kotlinx.coroutines.flow.Flow

public data class StreamsConfig(val key: ByteArray, val server: SocketAddress, val socket: VoiceUdpSocket)

/**
 * A representation of receiving voice through Discord and different stages of processing.
 */
@KordVoice
public interface Streams {
    /**
     * Starts propagating packets from [server] with the following [key] to decrypt the incoming frames.
     */
    public suspend fun listen(config: StreamsConfig)

    /**
     * A flow of all incoming [dev.kord.voice.udp.RTPPacket]s through the UDP connection.
     */
    public val incomingAudioPackets: Flow<RTPPacket>

    /**
     * A flow of all incoming [AudioFrame]s mapped to their [ssrc][UInt].
     */
    public val incomingAudioFrames: Flow<Pair<UInt, AudioFrame>>

    /**
     * A flow of all incoming [AudioFrame]s mapped to their [userId][Snowflake].
     * Streams for every user should be built over time and will not be immediately available.
     */
    public val incomingUserStreams: Flow<Pair<Snowflake, AudioFrame>>

    /**
     * A map of [ssrc][UInt]s to their corresponding [userId][Snowflake].
     */
    public val ssrcToUser: Map<UInt, Snowflake>
}