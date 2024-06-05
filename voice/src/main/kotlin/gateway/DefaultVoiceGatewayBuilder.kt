package dev.kord.voice.gateway

import dev.kord.common.annotation.KordInternal
import dev.kord.common.annotation.KordVoice
import dev.kord.common.entity.Snowflake
import dev.kord.common.http.httpEngine
import dev.kord.gateway.retry.LinearRetry
import dev.kord.gateway.retry.Retry
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.websocket.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlin.time.Duration.Companion.seconds

@KordVoice
public class DefaultVoiceGatewayBuilder(
    public val selfId: Snowflake,
    public val guildId: Snowflake,
    public val sessionId: String,
) {
    public var client: HttpClient? = null

    public var reconnectRetry: Retry? = null

    public var eventFlow: MutableSharedFlow<VoiceEvent> = MutableSharedFlow(extraBufferCapacity = Int.MAX_VALUE)
    public var isDeaf: Boolean = false

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

    public fun build(): DefaultVoiceGateway {
        val client = client ?: HttpClient(httpEngine()) {
            install(WebSockets)
            install(ContentNegotiation) {
                json()
            }
        }
        val retry = reconnectRetry ?: LinearRetry(2.seconds, 20.seconds, 10)

        val data = DefaultVoiceGatewayData(
            selfId,
            guildId,
            sessionId,
            client,
            retry,
            isDeaf,
            eventFlow
        )

        return DefaultVoiceGateway(
            scopeFactory() + CoroutineName("kord-voice-gateway[$${guildId.value}]"),
            data
        )
    }
}
