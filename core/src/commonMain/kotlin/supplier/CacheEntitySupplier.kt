package dev.kord.core.supplier

import dev.kord.cache.api.DataCache
import dev.kord.cache.api.query
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.Optional
import dev.kord.common.exception.RequestException
import dev.kord.core.Kord
import dev.kord.core.any
import dev.kord.core.cache.data.*
import dev.kord.core.cache.idEq
import dev.kord.core.cache.idGt
import dev.kord.core.cache.idLt
import dev.kord.core.entity.*
import dev.kord.core.entity.application.ApplicationCommandPermissions
import dev.kord.core.entity.application.GlobalApplicationCommand
import dev.kord.core.entity.application.GuildApplicationCommand
import dev.kord.core.entity.automoderation.AutoModerationRule
import dev.kord.core.entity.channel.Channel
import dev.kord.core.entity.channel.TopGuildChannel
import dev.kord.core.entity.channel.thread.ThreadChannel
import dev.kord.core.entity.channel.thread.ThreadMember
import dev.kord.core.entity.interaction.followup.FollowupMessage
import dev.kord.core.exception.EntityNotFoundException
import dev.kord.gateway.Gateway
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Instant

/**
 * [EntitySupplier] that uses a [DataCache] to resolve entities.
 *
 * Getting existing entities should not throw any [RequestException] unless
 * specified by the configured cache.
 *
 * The supplier might not be able to return entities independent of their actual
 * existence, their presence depends on incoming events from the [Gateway] and
 * how the cache is set up to store entities.
 *
 * Returned flows without entities will not throw an [EntityNotFoundException]
 * if none are presented like other `getX` functions. Instead, the flow will be empty.
 */
public class CacheEntitySupplier(private val kord: Kord) : EntitySupplier {

    /**
     * The Cache this [CacheEntitySupplier] operates on.
     *
     * Shorthand for [kord.cache][Kord.cache].
     */
    private inline val cache: DataCache get() = kord.cache

    /**
     *  Returns a [Flow] of [Channel]s fetched from cache.
     *
     *  The returned flow is lazily executed, any [RequestException] will be thrown on
     * [terminal operators](https://kotlinlang.org/docs/reference/coroutines/flow.html#terminal-flow-operators) instead.
     */
    public val channels: Flow<Channel>
        get() = cache.query<ChannelData>().asFlow().map { Channel.from(it, kord) }

    /**
     *  fetches all cached [Guild]s
     *
     *  The returned flow is lazily executed, any [RequestException] will be thrown on
     * [terminal operators](https://kotlinlang.org/docs/reference/coroutines/flow.html#terminal-flow-operators) instead.
     */
    override val guilds: Flow<Guild>
        get() = cache.query<GuildData>().asFlow().map { Guild(it, kord) }

    /**
     *  fetches all cached [Region]s
     *
     *  The returned flow is lazily executed, any [RequestException] will be thrown on
     * [terminal operators](https://kotlinlang.org/docs/reference/coroutines/flow.html#terminal-flow-operators) instead.
     */
    override val regions: Flow<Region>
        get() = cache.query<RegionData>().asFlow().map { Region(it, kord) }

    /**
     *  fetches all cached [Role]s
     */
    public val roles: Flow<Role>
        get() = cache.query<RoleData>().asFlow().map { Role(it, kord) }

    /**
     *  fetches all cached [User]s
     */
    public val users: Flow<User>
        get() = cache.query<UserData>().asFlow().map { User(it, kord) }

    /**
     *  fetches all cached [Member]s
     */
    public val members: Flow<Member>
        get() = cache.query<MemberData>().asFlow().mapNotNull {
            val userData =
                cache.query { idEq(UserData::id, it.userId) }.singleOrNull() ?: return@mapNotNull null
            Member(it, userData, kord)
        }

    public suspend fun getRole(id: Snowflake): Role? {
        val data = cache.query { idEq(RoleData::id, id) }.singleOrNull() ?: return null

        return Role(data, kord)
    }

    override suspend fun getGuildPreviewOrNull(guildId: Snowflake): GuildPreview? {
        val data = cache.query { idEq(GuildPreviewData::id, guildId) }.singleOrNull() ?: return null

        return GuildPreview(data, kord)
    }

    override suspend fun getGuildWidgetOrNull(guildId: Snowflake): GuildWidget? = null

    override suspend fun getChannelOrNull(id: Snowflake): Channel? {
        val data = cache.query { idEq(ChannelData::id, id) }.singleOrNull() ?: return null
        return Channel.from(data, kord)
    }

    override fun getGuildChannels(guildId: Snowflake): Flow<TopGuildChannel> = cache.query {
        idEq(ChannelData::guildId, guildId)
    }.asFlow().map { Channel.from(it, kord) }.filterIsInstance()

    override fun getChannelPins(channelId: Snowflake): Flow<Message> = cache.query {
        idEq(MessageData::channelId, channelId)
        idEq(MessageData::pinned, true)
    }.asFlow().map { Message(it, kord) }

    override suspend fun getGuildOrNull(id: Snowflake): Guild? {
        val data = cache.query { idEq(GuildData::id, id) }.singleOrNull() ?: return null
        return Guild(data, kord)
    }

    override suspend fun getMemberOrNull(guildId: Snowflake, userId: Snowflake): Member? {
        val userData = cache.query { idEq(UserData::id, userId) }.singleOrNull() ?: return null

        val memberData = cache.query {
            idEq(MemberData::userId, userId)
            idEq(MemberData::guildId, guildId)
        }.singleOrNull() ?: return null

        return Member(memberData, userData, kord)
    }

    override suspend fun getMessageOrNull(channelId: Snowflake, messageId: Snowflake): Message? {
        val data = cache.query { idEq(MessageData::id, messageId) }.singleOrNull()
            ?: return null

        return Message(data, kord)
    }

    override fun getMessagesAfter(messageId: Snowflake, channelId: Snowflake, limit: Int?): Flow<Message> {
        checkLimit(limit)
        return cache.query {
            idEq(MessageData::channelId, channelId)
            idGt(MessageData::id, messageId)
        }.asFlow().map { Message(it, kord) }.limit(limit)
    }

    override fun getMessagesBefore(messageId: Snowflake, channelId: Snowflake, limit: Int?): Flow<Message> {
        checkLimit(limit)
        return cache.query {
            idEq(MessageData::channelId, channelId)
            idLt(MessageData::id, messageId)
        }.asFlow().map { Message(it, kord) }.limit(limit)
    }


    override fun getMessagesAround(messageId: Snowflake, channelId: Snowflake, limit: Int): Flow<Message> {
        require(limit in 1..100) { "Expected limit to be in 1..100, but was $limit" }
        return flow {
            emitAll(getMessagesBefore(messageId, channelId, limit / 2))
            getMessageOrNull(channelId, messageId)?.let { emit(it) }
            emitAll(getMessagesAfter(messageId, channelId, limit / 2))
        }
    }

    override suspend fun getSelfOrNull(): User? = getUserOrNull(kord.selfId)

    override suspend fun getRoleOrNull(guildId: Snowflake, roleId: Snowflake): Role? {
        val data = cache.query {
            idEq(RoleData::id, roleId)
            idEq(RoleData::guildId, guildId)
        }.singleOrNull() ?: return null

        return Role(data, kord)
    }

    override fun getGuildRoles(guildId: Snowflake): Flow<Role> = cache.query {
        idEq(RoleData::guildId, guildId)
    }.asFlow().map { Role(it, kord) }

    override suspend fun getGuildBanOrNull(guildId: Snowflake, userId: Snowflake): Ban? {
        val data = cache.query {
            idEq(BanData::userId, userId)
            idEq(BanData::guildId, guildId)
        }.singleOrNull() ?: return null
        return Ban(data, kord)
    }

    override fun getGuildBans(guildId: Snowflake, limit: Int?): Flow<Ban> {
        checkLimit(limit)
        return cache.query { idEq(BanData::guildId, guildId) }
            .asFlow()
            .map { Ban(it, kord) }
            .limit(limit)
    }

    override fun getGuildMembers(guildId: Snowflake, limit: Int?): Flow<Member> {
        checkLimit(limit)
        return cache.query { idEq(MemberData::guildId, guildId) }
            .asFlow()
            .mapNotNull { memberData ->
                val userData = cache.query { idEq(UserData::id, memberData.userId) }.singleOrNull()
                userData?.let { Member(memberData, userData = it, kord) }
            }
            .limit(limit)
    }

    override fun getGuildVoiceRegions(guildId: Snowflake): Flow<Region> = cache.query {
        idEq(RegionData::guildId, guildId)
    }.asFlow().map { Region(it, kord) }

    override suspend fun getEmojiOrNull(guildId: Snowflake, emojiId: Snowflake): GuildEmoji? {
        val data = cache.query {
            idEq(EmojiData::guildId, guildId)
            idEq(EmojiData::id, emojiId)
        }.singleOrNull() ?: return null

        return GuildEmoji(data, kord)
    }

    override fun getEmojis(guildId: Snowflake): Flow<GuildEmoji> = cache.query {
        idEq(EmojiData::guildId, guildId)
    }.asFlow().map { GuildEmoji(it, kord) }

    override fun getCurrentUserGuilds(limit: Int?): Flow<Guild> {
        checkLimit(limit)
        return guilds.filter {
            members.any { it.id == kord.selfId }
        }.limit(limit)
    }

    override fun getChannelWebhooks(channelId: Snowflake): Flow<Webhook> = cache.query {
        idEq(WebhookData::channelId, channelId)
    }.asFlow().map { Webhook(it, kord) }

    override fun getGuildWebhooks(guildId: Snowflake): Flow<Webhook> = cache.query {
        idEq(WebhookData::guildId, guildId)
    }.asFlow().map { Webhook(it, kord) }

    override suspend fun getWebhookOrNull(id: Snowflake): Webhook? {
        val data = cache.query {
            idEq(WebhookData::id, id)
        }.singleOrNull() ?: return null

        return Webhook(data, kord)
    }

    override suspend fun getWebhookWithTokenOrNull(id: Snowflake, token: String): Webhook? {
        val data = cache.query {
            idEq(WebhookData::id, id)
            idEq(WebhookData::token, token)
        }.singleOrNull() ?: return null

        return Webhook(data, kord)
    }

    override suspend fun getWebhookMessageOrNull(
        webhookId: Snowflake,
        token: String,
        messageId: Snowflake,
        threadId: Snowflake?,
    ): Message? {
        val data = cache.query {
            idEq(MessageData::webhookId, webhookId)
            idEq(MessageData::id, messageId)
            if (threadId != null) idEq(MessageData::channelId, threadId)
        }.singleOrNull() ?: return null

        return Message(data, kord)
    }

    override suspend fun getUserOrNull(id: Snowflake): User? {
        val data = cache.query { idEq(UserData::id, id) }.singleOrNull() ?: return null

        return User(data, kord)
    }

    override suspend fun getTemplateOrNull(code: String): Template? {
        val data = cache.query {
            idEq(TemplateData::code, code)
        }.singleOrNull() ?: return null

        return Template(data, kord)
    }

    override fun getTemplates(guildId: Snowflake): Flow<Template> {
        return cache.query {
            idEq(TemplateData::sourceGuildId, guildId)
        }.asFlow().map { Template(it, kord) }
    }

    override suspend fun getStageInstanceOrNull(channelId: Snowflake): StageInstance? = null

    override fun getThreadMembers(channelId: Snowflake): Flow<ThreadMember> {
        return cache.query {
            idEq(ThreadMemberData::id, channelId)
        }.asFlow().map { ThreadMember(it, kord) }
    }

    override fun getActiveThreads(guildId: Snowflake): Flow<ThreadChannel> = flow {
        val result = cache.query {
            idEq(ChannelData::guildId, guildId)
        }.toCollection()
            .sortedByDescending { it.id }
            .asFlow()
            .filter {
                it.threadMetadata.value?.archived != true
            }.mapNotNull {
                Channel.from(it, kord) as? ThreadChannel
            }

        emitAll(result)
    }

    override fun getPublicArchivedThreads(channelId: Snowflake, before: Instant?, limit: Int?): Flow<ThreadChannel> {
        checkLimit(limit)
        return flow {
            val result = cache.query { idEq(ChannelData::parentId, channelId) }
                .toCollection()
                .sortedByDescending { it.threadMetadata.value?.archiveTimestamp }
                .asFlow()
                .filter {
                    val time = it.threadMetadata.value?.archiveTimestamp
                    it.threadMetadata.value?.archived == true
                            && time != null
                            && (before == null || time < before)
                            && (it.type == ChannelType.PublicGuildThread || it.type == ChannelType.PublicNewsThread)
                }
                .limit(limit)
                .mapNotNull { Channel.from(it, kord) as? ThreadChannel }

            emitAll(result)
        }
    }

    override fun getPrivateArchivedThreads(channelId: Snowflake, before: Instant?, limit: Int?): Flow<ThreadChannel> {
        checkLimit(limit)
        return flow {
            val result = cache.query { idEq(ChannelData::parentId, channelId) }
                .toCollection()
                .sortedByDescending { it.threadMetadata.value?.archiveTimestamp }
                .asFlow()
                .filter {
                    val time = it.threadMetadata.value?.archiveTimestamp
                    it.threadMetadata.value?.archived == true
                            && time != null
                            && (before == null || time < before)
                            && it.type == ChannelType.PrivateThread
                }
                .limit(limit)
                .mapNotNull { Channel.from(it, kord) as? ThreadChannel }

            emitAll(result)
        }
    }

    override fun getJoinedPrivateArchivedThreads(
        channelId: Snowflake,
        before: Snowflake?,
        limit: Int?,
    ): Flow<ThreadChannel> {
        checkLimit(limit)
        return flow {
            val result = cache.query { idEq(ChannelData::parentId, channelId) }
                .toCollection()
                .sortedByDescending { it.id }
                .asFlow()
                .filter {
                    it.threadMetadata.value?.archived == true
                            && (before == null || it.id < before)
                            && it.type == ChannelType.PrivateThread
                            && it.member !is Optional.Missing
                }
                .limit(limit)
                .mapNotNull { Channel.from(it, kord) as? ThreadChannel }

            emitAll(result)
        }
    }

    override fun getGuildApplicationCommands(
        applicationId: Snowflake,
        guildId: Snowflake,
        withLocalizations: Boolean?
    ): Flow<GuildApplicationCommand> = cache.query {
        idEq(ApplicationCommandData::guildId, guildId)
        idEq(ApplicationCommandData::applicationId, applicationId)
    }.asFlow()
        .map {
            when (withLocalizations) {
                true, null -> it
                false -> it.copy(
                    nameLocalizations = Optional.Missing(),
                    descriptionLocalizations = Optional.Missing(),
                )
            }
        }
        .map { GuildApplicationCommand(it, kord.rest.interaction) }


    override suspend fun getGuildApplicationCommandOrNull(
        applicationId: Snowflake,
        guildId: Snowflake,
        commandId: Snowflake
    ): GuildApplicationCommand? {
        val data = cache.query {
            idEq(ApplicationCommandData::id, commandId)
            idEq(ApplicationCommandData::guildId, guildId)
            idEq(ApplicationCommandData::applicationId, applicationId)
        }.singleOrNull() ?: return null

        return GuildApplicationCommand(data, kord.rest.interaction)
    }

    override suspend fun getGlobalApplicationCommandOrNull(
        applicationId: Snowflake,
        commandId: Snowflake
    ): GlobalApplicationCommand? {
        val data = cache.query {
            idEq(ApplicationCommandData::id, commandId)
            idEq(ApplicationCommandData::guildId, null)
            idEq(ApplicationCommandData::applicationId, applicationId)
        }.singleOrNull() ?: return null

        return GlobalApplicationCommand(data, kord.rest.interaction)
    }

    override fun getGlobalApplicationCommands(applicationId: Snowflake, withLocalizations: Boolean?): Flow<GlobalApplicationCommand> =
        cache.query {
            idEq(ApplicationCommandData::guildId, null)
            idEq(ApplicationCommandData::applicationId, applicationId)
        }.asFlow()
            .map {
                when (withLocalizations) {
                    true, null -> it
                    false -> it.copy(
                        nameLocalizations = Optional.Missing(),
                        descriptionLocalizations = Optional.Missing(),
                    )
                }
            }
            .map { GlobalApplicationCommand(it, kord.rest.interaction) }

    override fun getGuildApplicationCommandPermissions(
        applicationId: Snowflake,
        guildId: Snowflake
    ): Flow<ApplicationCommandPermissions> = cache.query {
        idEq(GuildApplicationCommandPermissionsData::guildId, guildId)
        idEq(GuildApplicationCommandPermissionsData::applicationId, applicationId)
    }.asFlow().map { ApplicationCommandPermissions(it) }


    override suspend fun getApplicationCommandPermissionsOrNull(
        applicationId: Snowflake,
        guildId: Snowflake,
        commandId: Snowflake
    ): ApplicationCommandPermissions? {
        val data = cache.query {
            idEq(GuildApplicationCommandPermissionsData::id, commandId)
            idEq(GuildApplicationCommandPermissionsData::guildId, guildId)
            idEq(GuildApplicationCommandPermissionsData::applicationId, applicationId)
        }.singleOrNull() ?: return null

        return ApplicationCommandPermissions(data)
    }

    override suspend fun getFollowupMessageOrNull(
        applicationId: Snowflake,
        interactionToken: String,
        messageId: Snowflake,
    ): FollowupMessage? {
        val data = cache.query {
            idEq(MessageData::applicationId, applicationId)
            idEq(MessageData::id, messageId)
        }.singleOrNull() ?: return null

        return FollowupMessage(Message(data, kord), applicationId, interactionToken, kord)
    }

    override suspend fun getGuildScheduledEventOrNull(guildId: Snowflake, eventId: Snowflake): GuildScheduledEvent? {
        val data = cache.query {
            idEq(GuildScheduledEventData::guildId, guildId)
            idEq(GuildScheduledEventData::id, eventId)
        }.singleOrNull() ?: return null

        return GuildScheduledEvent(data, kord)
    }

    override fun getGuildScheduledEventMembersBefore(
        guildId: Snowflake,
        eventId: Snowflake,
        before: Snowflake,
        limit: Int?,
    ): Flow<Member> {
        checkLimit(limit)
        return cache
            .query {
                idLt(MemberData::userId, before)
                idEq(MemberData::guildId, guildId)
            }
            .asFlow()
            .mapNotNull {
                val userData = cache.query { idEq(UserData::id, it.userId) }.singleOrNull()
                    ?: return@mapNotNull null
                Member(it, userData, kord)
            }
            .limit(limit)
    }

    override fun getGuildScheduledEventUsersBefore(
        guildId: Snowflake,
        eventId: Snowflake,
        before: Snowflake,
        limit: Int?,
    ): Flow<User> = getGuildScheduledEventMembersBefore(guildId, eventId, before, limit).map { it.asUser() }

    override fun getGuildScheduledEventMembersAfter(
        guildId: Snowflake,
        eventId: Snowflake,
        after: Snowflake,
        limit: Int?,
    ): Flow<Member> {
        checkLimit(limit)
        return cache
            .query {
                idGt(MemberData::userId, after)
                idEq(MemberData::guildId, guildId)
            }
            .asFlow()
            .mapNotNull {
                val userData = cache.query { idEq(UserData::id, it.userId) }.singleOrNull()
                    ?: return@mapNotNull null
                Member(it, userData, kord)
            }
            .limit(limit)
    }

    override fun getGuildScheduledEventUsersAfter(
        guildId: Snowflake,
        eventId: Snowflake,
        after: Snowflake,
        limit: Int?,
    ): Flow<User> = getGuildScheduledEventMembersAfter(guildId, eventId, after, limit).map { it.asUser() }

    override suspend fun getStickerOrNull(id: Snowflake): Sticker? {
        val data = cache.query { idEq(StickerData::id, id) }.singleOrNull() ?: return null
        return Sticker(data, kord)
    }

    override suspend fun getGuildStickerOrNull(guildId: Snowflake, id: Snowflake): GuildSticker? {
        val data = cache.query {
            idEq(StickerData::id, id)
            idEq(StickerData::guildId, guildId)
        }.singleOrNull() ?: return null

        return GuildSticker(data, kord)
    }

    override fun getNitroStickerPacks(): Flow<StickerPack> {
        return cache.query<StickerPackData>().asFlow().map {
            StickerPack(it, kord)
        }
    }

    override fun getGuildStickers(guildId: Snowflake): Flow<GuildSticker> {
        return cache.query { idEq(StickerData::guildId, guildId) }
            .asFlow()
            .map { GuildSticker(it, kord) }
    }

    override fun getGuildScheduledEvents(guildId: Snowflake): Flow<GuildScheduledEvent> =
        cache.query {
            idEq(GuildScheduledEventData::guildId, guildId)
        }.asFlow().map { GuildScheduledEvent(it, kord) }

    override fun getAutoModerationRules(guildId: Snowflake): Flow<AutoModerationRule> =
        cache.query { idEq(AutoModerationRuleData::guildId, guildId) }
            .asFlow()
            .map { AutoModerationRule(it, kord) }

    override suspend fun getAutoModerationRuleOrNull(guildId: Snowflake, ruleId: Snowflake): AutoModerationRule? =
        cache
            .query {
                idEq(AutoModerationRuleData::id, ruleId)
                idEq(AutoModerationRuleData::guildId, guildId)
            }
            .singleOrNull()
            ?.let { AutoModerationRule(it, kord) }

    override suspend fun getEntitlementOrNull(applicationId: Snowflake, entitlementId: Snowflake): Entitlement? =
        cache
            .query {
                idEq(EntitlementData::id, entitlementId)
                idEq(EntitlementData::applicationId, applicationId)
            }
            .singleOrNull()
            ?.let { Entitlement(it, kord) }

    override suspend fun getEntitlements(
        applicationId: Snowflake,
        skuId: Snowflake?,
        limit: Int?,
        userId: Snowflake?,
        guildId: Snowflake?
    ): Flow<Entitlement> {
        checkLimit(limit)
        return cache
            .query {
                idEq(EntitlementData::applicationId, applicationId)
                skuId?.let { idEq(EntitlementData::skuId, it) }
                userId?.let { idEq(EntitlementData::userId, it) }
                guildId?.let { idEq(EntitlementData::guildId, it) }
            }
            .asFlow()
            .map { Entitlement(it, kord) }
            .limit(limit)
    }

    override fun toString(): String = "CacheEntitySupplier(cache=$cache)"
}


private fun checkLimit(limit: Int?) {
    require(limit == null || limit > 0) { "At least 1 item should be requested, but got $limit." }
}

private fun <T> Flow<T>.limit(limit: Int?): Flow<T> = if (limit == null) this else take(limit)
