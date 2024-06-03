package dev.kord.voice

import dev.kord.common.annotation.KordVoice
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.BaseVoiceChannelBehavior
import dev.kord.core.behavior.channel.connect
import dev.kord.core.behavior.interaction.respondPublic
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.interaction.GuildChatInputCommandInteractionCreateEvent
import dev.kord.core.on
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

suspend fun main(args: Array<out String>) {
    val token = System.getenv("DISCORD_TOKEN")
                ?: args.elementAtOrNull(0)
                ?: error("No token was provided")

    val kord = Kord(token)

    kord.createGlobalChatInputCommand("join", "Joins your voice channel and echos your mic.")

    kord.on<ReadyEvent> {
        println("ready")
    }

    kord.on<GuildChatInputCommandInteractionCreateEvent> {
        val channel = interaction.user.asMember(interaction.guildId)
            .getVoiceState()
            .getChannelOrNull()

        if (channel == null) {
            interaction.respondPublic { content = "not in channel" }
            return@on
        }

        interaction.respondPublic { content = "success" }
        channel.connectEcho()
    }

    kord.login()
}

@OptIn(KordVoice::class)
private suspend fun BaseVoiceChannelBehavior.connectEcho() {
    val buffer = mutableListOf(AudioFrame.SILENCE, AudioFrame.SILENCE, AudioFrame.SILENCE, AudioFrame.SILENCE)
    val connection = connect {
        receiveVoice = true
        frameProvider(20.milliseconds) {
            buffer.removeFirstOrNull() ?: AudioFrame.SILENCE
        }
    }

    connection.scope.launch {
        connection.streams.incomingAudioFrames.collect { (ssrc, frame) ->
            println("Received frame from: $ssrc")
            buffer.add(frame)
        }
    }
}