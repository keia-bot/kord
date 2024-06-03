@file:JvmName("VoiceBaseVoiceChannelBehavior")

package dev.kord.core.behavior.channel

import dev.kord.common.annotation.KordInternal
import dev.kord.common.annotation.KordVoice
import dev.kord.core.entity.channel.VoiceChannel
import dev.kord.core.exception.GatewayNotFoundException
import dev.kord.voice.VoiceConnection
import dev.kord.voice.VoiceConnectionBuilder
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.plus

/**
 * Connect to this [VoiceChannel] and create a [VoiceConnection] for this voice session.
 *
 * @param builder a builder for the [VoiceConnection].
 * @throws GatewayNotFoundException when there is no associated [dev.kord.gateway.Gateway] for the [dev.kord.core.entity.Guild] this channel is in.
 * @throws dev.kord.voice.exception.VoiceConnectionInitializationException when there was a problem retrieving voice information from Discord.
 * @return a [VoiceConnection] representing the connection to this [VoiceConnection].
 */
@KordVoice
public suspend fun BaseVoiceChannelBehavior.connect(builder: VoiceConnectionBuilder.() -> Unit): VoiceConnection {
    val connection = createVoiceConnection(builder)
    connection.connect()

    return connection
}

/**
 * Creates a [VoiceConnection] with the information of this [VoiceChannel].
 *
 * The connection is not started, you must call [VoiceConnection.connect] to start the connection.
 *
 * @param builder a builder for the [VoiceConnection].
 * @throws GatewayNotFoundException when there is no associated [dev.kord.gateway.Gateway] for the [dev.kord.core.entity.Guild] this channel is in.
 * @return a [VoiceConnection] representing the connection to this [VoiceConnection].
 */
@KordVoice
@KordInternal
public suspend fun BaseVoiceChannelBehavior.createVoiceConnection(builder: VoiceConnectionBuilder.() -> Unit): VoiceConnection =
    VoiceConnection(
        guild.gateway ?: GatewayNotFoundException.voiceConnectionGatewayNotFound(guildId),
        kord.selfId,
        id,
        guildId,
    ) {
        scope { guild.kord + SupervisorJob(guild.kord.coroutineContext.job) }
        builder()
    }
