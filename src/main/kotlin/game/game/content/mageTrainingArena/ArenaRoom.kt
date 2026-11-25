package game.content.mageTrainingArena

import io.luna.game.event.impl.ControllableEvent
import io.luna.game.model.Position
import io.luna.game.model.mob.Player

/**
 * One of the four training rooms behind the entrance hall portals.
 *
 * @author TheLining
 */
abstract class ArenaRoom(

    /**
     * The room's name.
     */
    val title: String,

    /**
     * The entrance hall portal that leads here.
     */
    val portal: Int,

    /**
     * The Magic level needed to go through the portal: that of the room's lowest spell.
     */
    val level: Int,

    /**
     * The walkable overlay showing the room's Pizazz Points.
     */
    val overlay: Int,

    /**
     * Where the player stands in the entrance hall after using the room's exit teleport.
     */
    val exitTile: Position
) {

    /**
     * Items that belong to the room and never leave it.
     */
    abstract val roomItems: Set<Int>

    /**
     * Checks room-specific entry rules, telling [plr] why they can't go in.
     */
    open fun canEnter(plr: Player): Boolean = true

    /**
     * Places [plr] in the room.
     */
    abstract fun enter(plr: Player)

    /**
     * Settles [plr]'s visit as they leave. [throughPortal] is false when they log out, teleport or die.
     */
    open fun leave(plr: Player, throughPortal: Boolean) {}

    /**
     * Whether [position] is inside the room.
     */
    abstract fun inside(position: Position): Boolean

    /**
     * Handles an action [plr] takes in the room.
     *
     * @return `false` if the room handled or refused it.
     */
    open fun event(plr: Player, event: ControllableEvent): Boolean = true

    /**
     * Runs once a tick for [plr] while they are in the room.
     */
    open fun process(plr: Player) {}

    /**
     * Shows the room's overlay.
     */
    fun openOverlay(plr: Player) {
        MageTrainingArena.openOverlay(plr, overlay)
        refreshOverlay(plr)
    }

    /**
     * Updates the overlay's text.
     */
    abstract fun refreshOverlay(plr: Player)
}
