package dev.kord.rest.builder.interaction

import dev.kord.common.annotation.KordDsl
import dev.kord.common.entity.ApplicationCommandType
import dev.kord.common.entity.ApplicationIntegrationType
import dev.kord.common.entity.InteractionContextType
import dev.kord.common.entity.Permissions
import dev.kord.rest.builder.RequestBuilder
import dev.kord.rest.json.request.ApplicationCommandCreateRequest
import dev.kord.rest.json.request.ApplicationCommandModifyRequest

@KordDsl
public interface ApplicationCommandCreateBuilder : LocalizedNameCreateBuilder,
    RequestBuilder<ApplicationCommandCreateRequest> {

    public var defaultMemberPermissions: Permissions?

    @Deprecated("'defaultPermission' is deprecated in favor of 'defaultMemberPermissions' and 'dmPermission'. Setting 'defaultPermission' to false can be replaced by setting 'defaultMemberPermissions' to empty Permissions and 'dmPermission' to false ('dmPermission' is only available for global commands).")
    public var defaultPermission: Boolean?
    public val type: ApplicationCommandType

    /**
     * Disables the command for everyone except admins by default.
     *
     * **This does not ensure normal users cannot execute the command, any admin can change this setting.**
     */
    public fun disableCommandInGuilds() {
        defaultMemberPermissions = Permissions()
    }

    /**
     * [Installation context(s)] where the command is available.
     */
    public var integrationTypes: List<ApplicationIntegrationType>

    /**
     * [Interaction context(s)][InteractionContextType] where the command can be used.
     */
    public var contexts: List<InteractionContextType>

    /** Indicates whether the command is age-restricted. Defaults to `false`. */
    public var nsfw: Boolean?
}

public fun ApplicationCommandCreateBuilder.allowGuildInstall() {
    integrationTypes += ApplicationIntegrationType.GuildInstall
}

public fun ApplicationCommandCreateBuilder.allowUserInstall() {
    integrationTypes += ApplicationIntegrationType.UserInstall
}

public fun ApplicationCommandCreateBuilder.allowInBotDMs() {
    contexts += InteractionContextType.BotDM
}

public fun ApplicationCommandCreateBuilder.allowInGuilds() {
    allowGuildInstall()
    contexts += InteractionContextType.Guild
}

public fun ApplicationCommandCreateBuilder.allowInUserDMs() {
    allowUserInstall()
    contexts += InteractionContextType.PrivateChannel
}

@KordDsl
public interface GlobalApplicationCommandCreateBuilder : ApplicationCommandCreateBuilder {
    public var dmPermission: Boolean?
}

@KordDsl
public interface GlobalApplicationCommandModifyBuilder : ApplicationCommandModifyBuilder {
    public var dmPermission: Boolean?
}

@KordDsl
public interface ApplicationCommandModifyBuilder : LocalizedNameModifyBuilder,
    RequestBuilder<ApplicationCommandModifyRequest> {

    public var defaultMemberPermissions: Permissions?

    @Deprecated("'defaultPermission' is deprecated in favor of 'defaultMemberPermissions' and 'dmPermission'. Setting 'defaultPermission' to false can be replaced by setting 'defaultMemberPermissions' to empty Permissions and 'dmPermission' to false ('dmPermission' is only available for global commands).")
    public var defaultPermission: Boolean?

    /** Indicates whether the command is age-restricted. */
    public var nsfw: Boolean?
}
