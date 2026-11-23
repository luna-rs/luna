package game.obj.doors

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player

/**
 * Doors and gates that never open when clicked. Trying one only tells the player that it is locked.
 *
 * @author TheLining
 */
object LockedDoors {

    /**
     * The ticks between a door's first and second message.
     */
    private const val TRY_TICKS = 2

    /**
     * The doors that also play the locked sound.
     */
    private val rattling = setOf(52, 53, 131, 2689)

    /**
     * Every locked door, mapped to what the player is told. A second message follows the first after a moment.
     * TODO Fight Arena: door 82 opens for a player in Khazard armour, and the cell keys open 79 and 80.
     */
    val messages: Map<Int, List<String>> = mapOf(
        52 to listOf("The gate is locked."),
        53 to listOf("The gate is locked."),
        71 to listOf("The door is securely locked."),
        72 to listOf("The door is securely locked."),
        73 to listOf("You can't open this door."),
        74 to listOf("You can't open this door."),
        79 to listOf("The gate appears to be locked."),
        80 to listOf("The cell gate is securely locked."),
        82 to listOf("This door appears to be locked."),
        131 to listOf("The door is locked."),
        1589 to listOf("You try to open the gate...", "The gate is locked securely, it won't open!"),
        1590 to listOf("You try to open the gate...", "The gate is locked securely, it won't open!"),
        1591 to listOf("You try to open the door...", "The door is locked tight, I can't open it."),
        2340 to listOf("It's locked."),
        2689 to listOf("The door seems to be pretty locked."))

    /**
     * Tells [plr] that the door [id] is locked.
     */
    fun tryOpen(plr: Player, id: Int) {
        val lines = messages[id] ?: return
        if (id in rattling) {
            plr.playSound(Sound.LOCKED)
        }
        plr.sendMessage(lines[0])
        if (lines.size > 1) {
            world.scheduleOnce(TRY_TICKS) {
                if (plr.state == EntityState.ACTIVE) {
                    plr.sendMessage(lines[1])
                }
            }
        }
    }
}
