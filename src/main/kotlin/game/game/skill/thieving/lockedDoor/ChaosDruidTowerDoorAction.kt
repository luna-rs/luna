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
 * Handles door 2554 of the Chaos Druid Tower north of Ardougne. It is locked from outside and needs no lockpick;
 * leaving the tower is free.
 */
class ChaosDruidTowerDoorAction(plr: Player, door: GameObject, private val pick: Boolean) :
    DoorPassageAction(plr, door, TYPE, pick) {

    companion object {

        const val DOOR = 2554
        const val REQUIRED_LEVEL = 46
        const val EXPERIENCE = 37.5

        private val TYPE = DoorType(DOOR, 2055, null, null, null, 3)

        /**
         * The door is locked for players outside the tower, who stand on the door's own tile.
         */
        internal fun isLocked(door: Position, direction: ObjectDirection, player: Position): Boolean =
            Doors.isEntering(door, direction, player)
    }

    override fun checkAccess(entering: Boolean): Boolean {
        if (!entering) {
            if (pick) {
                mob.sendMessage("The door is already unlocked.")
            }
            mob.sendMessage("You go through the door.")
            return true
        }
        if (!pick) {
            mob.sendMessage("This door is locked.")
            mob.playSound(Sound.LOCKED)
            return false
        }
        mob.sendMessage("You attempt to pick the lock.")
        if (mob.thieving.level < REQUIRED_LEVEL) {
            mob.sendMessage("You need level $REQUIRED_LEVEL Thieving to pick this lock.")
            return false
        }
        if (rand(99) < 50) {
            mob.sendMessage("You fail to pick the lock.")
            return false
        }
        mob.sendMessage("You manage to pick the lock.")
        mob.thieving.addExperience(EXPERIENCE)
        return true
    }
}
