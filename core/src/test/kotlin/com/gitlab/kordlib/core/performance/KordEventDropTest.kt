package com.gitlab.kordlib.core.performance

import com.gitlab.kordlib.cache.api.DataCache
import com.gitlab.kordlib.common.entity.*
import com.gitlab.kordlib.core.ClientResources
import com.gitlab.kordlib.core.Kord
import com.gitlab.kordlib.core.event.guild.GuildCreateEvent
import com.gitlab.kordlib.core.gateway.MasterGateway
import com.gitlab.kordlib.core.on
import com.gitlab.kordlib.core.supplier.EntitySupplyStrategy
import com.gitlab.kordlib.gateway.*
import com.gitlab.kordlib.rest.request.KtorRequestHandler
import com.gitlab.kordlib.rest.service.RestClient
import io.ktor.client.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.time.Clock
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.minutes

class KordEventDropTest {

    object SpammyGateway : Gateway {
        val channel = BroadcastChannel<Event>(1)

        @OptIn(FlowPreview::class)
        override val events: Flow<Event>
            get() = channel.asFlow().buffer(Channel.UNLIMITED)

        override val ping: Duration
            get() = Duration.ZERO

        override suspend fun detach() {}

        override suspend fun send(command: Command) {}

        override suspend fun start(configuration: GatewayConfiguration) {}

        override suspend fun stop() {}
    }

    val kord = Kord(
            resources = ClientResources("token", 1, HttpClient(), EntitySupplyStrategy.cache, Intents.none),
            cache = DataCache.none(),
            MasterGateway(mapOf(0 to SpammyGateway)),
            RestClient(KtorRequestHandler("token", clock = Clock.systemUTC())),
            Snowflake("420"),
            BroadcastChannel(1),
            Dispatchers.Default
    )

    @Test
    fun `hammering the gateway does not drop core events`() = runBlocking {
        val amount = 1_000

        val event = GuildCreate(
                DiscordGuild(
                        "1337",
                        "discord guild",
                        afkTimeout = 0,
                        defaultMessageNotifications = DefaultMessageNotificationLevel.AllMessages,
                        emojis = emptyList(),
                        explicitContentFilter = ExplicitContentFilter.AllMembers,
                        features = emptyList(),
                        mfaLevel = MFALevel.Elevated,
                        ownerId = "123",
                        preferredLocale = "en",
                        description = "A not really real guild",
                        premiumTier = PremiumTier.None,
                        region = "idk",
                        roles = emptyList(),
                        verificationLevel = VerificationLevel.High
                ), 0)

        val counter = AtomicInteger(0)
        val countdown = CountDownLatch(amount)
        kord.on<GuildCreateEvent> {
            counter.incrementAndGet()
            countdown.countDown()
        }

        repeat(amount) {
            SpammyGateway.channel.send(event)
        }

        withTimeout(1.minutes) {
            countdown.await()
        }
        assertEquals(amount, counter.get())
    }

}
