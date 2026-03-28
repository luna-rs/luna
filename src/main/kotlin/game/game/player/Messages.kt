package game.player

import io.luna.game.model.mob.Player

/**
 * A collection of common player-facing message builders.
 *
 * This object centralizes reusable text for situations that occur frequently across gameplay systems, such as
 * busy-state checks and inventory capacity failures.
 *
 * @author lare96
 */
object Messages {

    /**
     * Builds a busy-state message.
     *
     * When [name] is `null`, this returns a generic message intended for the current player. Otherwise, it returns a
     * message stating that the named target is currently busy.
     *
     * @param name The optional target name to include in the message.
     * @return The formatted busy-state message.
     */
    fun busy(plr: Player? = null) =
        if (plr == null) "You are busy." else "${plr.username} is busy at the moment."

    /**
     * @return A message indicating the player does not have enough inventory space.
     */
    fun inventoryFull() = "You do not have enough space in your inventory."
}