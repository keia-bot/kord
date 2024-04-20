package dev.kord.core.entity.interaction

/**
 * An [Interaction] created while using a chat-input command (via auto-complete or invoking it).

 */
public sealed interface ChatInputBasedInteraction : Interaction {

    /**
     * The [InteractionCommand] that was used to create this interaction.
     */
    public val command: InteractionCommand

}
