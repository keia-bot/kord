package dev.kord.voice

import dev.kord.common.KordConfiguration
import dev.kord.common.annotation.KordInternal
import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.Snowflake
import dev.kord.gateway.Gateway
import dev.kord.gateway.UpdateVoiceStatus
import dev.kord.gateway.VoiceServerUpdate
import dev.kord.gateway.VoiceStateUpdate
import dev.kord.voice.encryption.AeadAes256Gcm
import dev.kord.voice.encryption.VoiceEncryption
import dev.kord.voice.exception.VoiceConnectionInitializationException
import dev.kord.voice.gateway.DefaultVoiceGateway
import dev.kord.voice.gateway.DefaultVoiceGatewayBuilder
import dev.kord.voice.gateway.VoiceGateway
import dev.kord.voice.gateway.VoiceGatewayConfiguration
import dev.kord.voice.streams.DefaultStreams
import dev.kord.voice.streams.NOPStreams
import dev.kord.voice.streams.Streams
import dev.kord.voice.udp.AudioFrameSenderFactory
import dev.kord.voice.udp.GlobalVoiceUdpSocket
import dev.kord.voice.udp.VoiceUdpSocket
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@KordVoice
public class VoiceConnectionBuilder(
    public var gateway: Gateway,
    public var selfId: Snowflake,
    public var channelId: Snowflake,
    public var guildId: Snowflake,
) {
    private var voiceGatewayBuilder: (DefaultVoiceGatewayBuilder.() -> Unit)? = null

    /**
     * The [CoroutineScope] to use.
     * By default, the scope will be created using the given [dispatcher] when [build] is called.
     */
    @KordInternal
    public var scopeFactory: () -> CoroutineScope = { CoroutineScope(dispatcher + SupervisorJob()) }

    /**
     * The [CoroutineDispatcher] to use.
     * By default, [Dispatchers.Default] will be used.
     */
    public var dispatcher: CoroutineDispatcher = Dispatchers.Default

    /**
     * The amount of time to wait for the events required to create a [VoiceConnection].
     * By default, this is set to 5 seconds.
     */
    public var timeout: Duration = 5.seconds

    /**
     * The [AudioFrameProvider] for this [VoiceConnection].
     * By default, no audio will be provided.
     */
    public var frameProvider: AudioFrameProvider = EmptyAudioPlayerFrameProvider

    /**
     * The [AudioFrameInterceptor] for this [VoiceConnection].
     * By default, [DefaultFrameInterceptor] will be used.
     */
    public var frameInterceptor: AudioFrameInterceptor = DefaultFrameInterceptor()

    /**
     * The factory used to create the [dev.kord.voice.udp.AudioFrameSender] for this [VoiceConnection].
     * By default, [AudioFrameSenderFactory.Default] will be used.
     */
    public var frameSenderFactory: AudioFrameSenderFactory = AudioFrameSenderFactory.Default

    /**
     * The nonce strategy to be used for the encryption of audio packets.
     * If `null` & voice receive if disabled, [VoiceEncryption.AeadAes256Gcm] will be used,
     * otherwise [VoiceEncryption.XSalsaPoly1305] with the Lite strategy will be used.
     */
    public var voiceEncryption: VoiceEncryption? = null

    /**
     * A [dev.kord.voice.udp.VoiceUdpSocket] implementation to be used.
     * By default, [GlobalVoiceUdpSocket] will be used.
     */
    public var voiceSocket: VoiceUdpSocket = GlobalVoiceUdpSocket

    /**
     * A boolean indicating whether your voice state will be muted.
     */
    public var selfMute: Boolean = false

    /**
     * A boolean indicating whether your voice state will be deafened.
     */
    public var selfDeaf: Boolean = false

    /**
     * A flag to control the implementation of [streams]. Set to false by default.
     * When set to false, a NOP implementation will be used.
     * When set to true, a proper receiving implementation will be used.
     */
    public var receiveVoice: Boolean = false

    /**
     * A [Streams] implementation to be used. This will override the [receiveVoice] flag.
     */
    public var streams: Streams? = null

    /**
     * The amount of time the connection should wait before assuming the voice connection has been closed instead of
     * moved.
     */
    public var detachTimeout: Duration = 100.milliseconds

    /**
     *
     */
    public fun frameProvider(
        duration: Duration,
        provide: suspend () -> AudioFrame?,
    ) {
        this.frameProvider = object : AudioFrameProvider.Callback(duration) {
            override suspend fun read(): AudioFrame? = provide()
        }
    }

    /**
     *
     */
    public fun frameInterceptor(frameInterceptor: AudioFrameInterceptor) {
        this.frameInterceptor = frameInterceptor
    }

    /**
     *
     */
    public fun frameSender(factory: AudioFrameSenderFactory) {
        this.frameSenderFactory = factory
    }

    /**
     * A builder to customize the voice connection's underlying [VoiceGateway].
     */
    public fun voiceGateway(builder: DefaultVoiceGatewayBuilder.() -> Unit) {
        this.voiceGatewayBuilder = builder
    }

    @KordInternal
    public fun scope(factory: () -> CoroutineScope) {
        this.scopeFactory = factory
    }

    private suspend fun Gateway.updateVoiceState(): Pair<VoiceConnectionData, VoiceGatewayConfiguration> =
        coroutineScope {
            val voiceStateDeferred = async {
                withTimeoutOrNull(timeout) {
                    gateway.events.filterIsInstance<VoiceStateUpdate>()
                        .filter { it.voiceState.guildId.value == guildId && it.voiceState.userId == selfId }
                        .first()
                        .voiceState
                }
            }

            val voiceServerDeferred = async {
                withTimeoutOrNull(timeout) {
                    gateway.events.filterIsInstance<VoiceServerUpdate>()
                        .filter { it.voiceServerUpdateData.guildId == guildId }
                        .first()
                        .voiceServerUpdateData
                }
            }

            send(
                UpdateVoiceStatus(
                    guildId = guildId,
                    channelId = channelId,
                    selfMute = selfMute,
                    selfDeaf = selfDeaf,
                )
            )

            val voiceServer = voiceServerDeferred.await()
            val voiceState = voiceStateDeferred.await()

            if (voiceServer == null || voiceState == null)
                throw VoiceConnectionInitializationException("Did not receive a VoiceStateUpdate and or a VoiceServerUpdate in time!")

            VoiceConnectionData(
                selfId,
                guildId,
                voiceState.sessionId
            ) to VoiceGatewayConfiguration(
                voiceServer.token,
                "wss://${voiceServer.endpoint}/?v=${KordConfiguration.VOICE_GATEWAY_VERSION}",
            )
        }

    private fun createVoiceGateway(data: VoiceConnectionData): DefaultVoiceGateway {
        val builder = DefaultVoiceGatewayBuilder(selfId, guildId, data.sessionId)

        // use the same scope factory and dispatcher as the connection
        builder.scopeFactory = scopeFactory
        builder.dispatcher = dispatcher

        voiceGatewayBuilder?.invoke(builder)

        return builder.build()
    }

    /**
     * @throws dev.kord.voice.exception.VoiceConnectionInitializationException when there was a problem retrieving voice information from Discord.
     */
    public suspend fun build(): VoiceConnection {
        val (voiceConnectionData, voiceGatewayConfig) = gateway.updateVoiceState()

        val voiceGateway = createVoiceGateway(voiceConnectionData)

        //
        val voiceEncryption = voiceEncryption ?: AeadAes256Gcm

        val frameSender = frameSenderFactory.create(
            frameInterceptor,
            frameProvider,
            voiceEncryption,
            voiceSocket
        )

        val streams =
            streams ?: if (receiveVoice) DefaultStreams(voiceGateway, voiceSocket, voiceEncryption) else NOPStreams

        return VoiceConnection(
            scopeFactory() + CoroutineName("kord-voice-connection[${guildId.value}]"),
            voiceConnectionData,
            gateway,
            streams,
            frameProvider,
            frameInterceptor,
            frameSender,
            voiceSocket,
            voiceGateway,
            voiceEncryption,
            voiceGatewayConfig,
            detachTimeout
        )
    }

    // we can't use the SAM feature or else we break the IR backend, so lets just use this object instead
    private object EmptyAudioPlayerFrameProvider : AudioFrameProvider {
        override fun CoroutineScope.provide(): Flow<AudioFrame?> = emptyFlow()
    }
}
