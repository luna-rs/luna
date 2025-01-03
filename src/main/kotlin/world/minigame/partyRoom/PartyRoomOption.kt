package world.minigame.partyRoom

import io.luna.game.model.mob.Player

/**
 * Represents a party room lever option that needs to be registered to [PartyRoom.LEVER_OPTIONS].
 */
abstract class PartyRoomOption(val cost: Int, val description: String) {

    /**
     * If the lever can be pulled.
     */
    open fun canExecute(plr: Player) = true

    /**
     * What happens when the lever is pulled for this option.
     */
    abstract fun execute(plr: Player)
}