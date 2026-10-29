package game.obj.doors

import api.predef.*
import api.predef.ext.*
import game.player.Sound
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * The locked door of the house north of Edgeville, which holds the ladder down to the Edgeville Dungeon hill giants. The
 * brass key is used on the door to go through it either way, and is kept.
 */
object BrassKeyDoor {

    const val DOOR = 1804
    const val OPEN = 1535
    const val KEY = 983
    private val doorType = DoorType(DOOR, OPEN, null, null, null, 3)

    /**
     * Clicking Open never opens the door, even with the key.
     */
    fun open(plr: Player) {
        plr.sendMessage("The door is locked.")
    }

    /**
     * Unlocks the door with the key and steps the player through.
     */
    fun unlock(plr: Player, door: GameObject) {
        if (plr.actions.first(Crossing::class.java) == null) {
            plr.submitAction(Crossing(plr, door))
        }
    }

    /**
     * Uses the shared passage so that the door closes behind the player.
     */
    private class Crossing(plr: Player, door: GameObject) : DoorPassageAction(plr, door, doorType) {
        override fun checkAccess(entering: Boolean): Boolean {
            if (!mob.inventory.contains(KEY)) {
                mob.sendMessage("The door is locked.")
                return false
            }
            mob.sendMessage("You unlock the door.")
            mob.playSound(Sound.LOCKED)
            return true
        }
    }
}
