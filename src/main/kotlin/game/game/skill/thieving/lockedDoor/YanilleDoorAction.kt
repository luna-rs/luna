package game.skill.thieving.lockedDoor

import api.predef.*
import api.predef.ext.*
import game.obj.doors.DoorPassageAction
import game.obj.doors.DoorType
import game.obj.doors.Doors
import game.player.Sound
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection

/**
 * Handles door 2559 using Lost City's locked_door script and inverted lock rule.
 *
 * @author lare96
 */
class YanilleDoorAction(plr: Player, door: GameObject, private val pick: Boolean) :
    DoorPassageAction(plr, door, TYPE, pick) {

    companion object {

        const val DOOR = 2559
        const val LOCKPICK = 1523
        const val REQUIRED_LEVEL = 82

        private val TYPE = DoorType(DOOR, 2070, null, null, null, 3)

        /**
         * The door is locked on the side opposite Lost City's check_axis result.
         */
        internal fun isLocked(door: Position, direction: ObjectDirection, player: Position): Boolean =
            !Doors.isEntering(door, direction, player)
    }

    override fun checkAccess(entering: Boolean): Boolean {
        if (entering) {
            if (pick) {
                mob.sendMessage("The door is already unlocked.")
            }
            mob.sendMessage("You go through the door.")
            mob.playSound(Sound.LOCKED)
            return true
        }
        if (!pick) {
            mob.sendMessage("This door is locked.")
            mob.playSound(Sound.LOCKED)
            return false
        }
        if (!mob.inventory.contains(LOCKPICK)) {
            mob.sendMessage("You need a lockpick for this lock.")
            return false
        }
        mob.sendMessage("You attempt to pick the lock.")
        if (mob.thieving.level < REQUIRED_LEVEL) {
            mob.sendMessage("You need level 82 Thieving to pick this lock.")
            return false
        }
        if (rand(99) < 50) {
            mob.sendMessage("You fail to pick the lock.")
            return false
        }
        mob.sendMessage("You manage to pick the lock.")
        mob.thieving.addExperience(50.0)
        return true
    }
}
