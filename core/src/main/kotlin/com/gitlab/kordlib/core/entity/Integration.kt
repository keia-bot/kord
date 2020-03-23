package com.gitlab.kordlib.core.entity

import com.gitlab.kordlib.common.entity.Snowflake
import com.gitlab.kordlib.core.Kord
import com.gitlab.kordlib.core.behavior.GuildBehavior
import com.gitlab.kordlib.core.behavior.RoleBehavior
import com.gitlab.kordlib.core.behavior.UserBehavior
import com.gitlab.kordlib.core.cache.data.IntegrationData
import com.gitlab.kordlib.core.toInstant
import com.gitlab.kordlib.rest.builder.integration.IntegrationModifyBuilder
import com.gitlab.kordlib.rest.json.response.IntegrationExpireBehavior
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * A [Discord integration](https://discordapp.com/developers/docs/resources/guild#get-guild-integrations).
 */
class Integration(val data: IntegrationData, override val kord: Kord) : Entity {

    override val id: Snowflake
        get() = Snowflake(data.id)

    /**
     * The name of this integration.
     */
    val name: String
        get() = data.name

    /**
     * The type of integration. (`"twitch"`, `"youtube"`, etc)
     */
    val type: String
        get() = data.type

    /**
     * Whether this integration is currently active.
     */
    val isEnabled: Boolean
        get() = data.enabled

    /**
     * Whether this integrations is syncing.
     */
    val isSyncing: Boolean
        get() = data.syncing

    /**
     * The id of the [guild][Guild] this integration is tied to.
     */
    val guildId: Snowflake
        get() = Snowflake(data.guildId)

    /**
     * The behavior of the [guild][Guild] this integration is tied to.
     */
    val guild: GuildBehavior
        get() = GuildBehavior(id = guildId, kord = kord)

    /**
     * The id of the [role][Role] used for 'subscribers' of the integration.
     */
    val roleId: Snowflake
        get() = Snowflake(data.id)

    /**
     * The behavior of the [role][Role] used for 'subscribers' of the integration.
     */
    val role: RoleBehavior
        get() = RoleBehavior(guildId = guildId, id = roleId, kord = kord)


    /**
     * Whether this integration requires emoticons to be synced, only supports Twitch right now.
     */
    val enablesEmoticons: Boolean
        get() = data.enableEmoticons

    /**
     * The behavior used to expire subscribers.
     */
    val expireBehavior: IntegrationExpireBehavior
        get() = data.expireBehavior

    /**
     * The grace period in days before expiring subscribers.
     */
    val expireGracePeriod: Duration
        get() = Duration.of(data.expireGracePeriod.toLong(), ChronoUnit.DAYS)

    /**
     * The id of the [user][User] for this integration.
     */
    val userId: Snowflake
        get() = Snowflake(data.id)

    /**
     * The behavior of the [user][User] for this integration.
     */
    val user: UserBehavior
        get() = UserBehavior(id = userId, kord = kord)

    /**
     * When this integration was last synced.
     */
    val syncedAt: Instant
        get() = data.syncedAt.toInstant()

    /**
     * Requests to get the guild this integration is tied to.
     */
    suspend fun getGuild(): Guild = kord.getGuild(guildId)!!

        /**
     * Requests to get the role used for 'subscribers' of the integration.
     */
    suspend fun getRole(): Role = kord.getRole(guildId = guildId, roleId = roleId)!!

    /**
     * Requests to delete the integration.
     */
    suspend fun delete() = kord.rest.guild.deleteGuildIntegration(guildId = guildId.value, integrationId = id.value)

    /**
     * Request to sync an integration.
     */
    suspend fun sync() = kord.rest.guild.syncGuildIntegration(guildId = guildId.value, integrationId = id.value)
}

suspend inline fun Integration.edit(builder: IntegrationModifyBuilder.() -> Unit) {
    kord.rest.guild.modifyGuildIntegration(guildId.value, id.value, builder)
}

