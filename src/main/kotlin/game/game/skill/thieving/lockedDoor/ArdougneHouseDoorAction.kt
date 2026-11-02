package game.skill.thieving.lockedDoor

import api.predef.*
import api.predef.ext.*
import game.obj.doors.DoorPassageAction
import game.obj.doors.DoorType
import game.player.Sound
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject

/**
 * The Ardougne house and castle locks and the Wilderness hut lock from Lost City's locked_door database.
 * Locks have a fixed one-half success chance and grant their source XP only on successful entry.
 * The check-axis side is locked; leaving is free. Existing passage handling closes the door after three ticks.
 *
 * @author lare96
 */
class ArdougneHouseDoorAction(player: Player, door: GameObject, private val lock: Lock, private val pick: Boolean) :
    DoorPassageAction(player, door, lock.type, pick) {

    /**
     * Source door ids, replacement models, levels and XP; the database stores XP in tenths.
     *
     * @author lare96
     */
    enum class Lock(val door: Int, open: Int, val level: Int, val experience: Double) {
        CHEST_HOUSE(2550, 2055, 1, 3.8),
        CHEST_HOUSE_SOUTH(2551, 2055, 16, 15.0),
        RANGE_HOUSE(2556, 2001, 13, 15.0),
        CASTLE(2555, 2001, 61, 50.0),
        WILDERNESS_HUT(2557, 1541, 23, 25.0);

        internal val type = DoorType(door, open, null, null, null, 3)

        /**
         * Only the Wilderness hut uses a lockpick; it is checked before the level and is never consumed.
         */
        val requiresLockpick: Boolean
            get() = this == WILDERNESS_HUT
    }

    override fun checkAccess(entering: Boolean): Boolean {
        if (!entering) {
            if (pick) mob.sendMessage("The door is already unlocked.")
            mob.sendMessage("You go through the door.")
            return true
        }
        if (!pick) {
            mob.sendMessage("This door is locked.")
            mob.playSound(Sound.LOCKED)
            return false
        }
        if (lock.requiresLockpick && !mob.inventory.contains(1523)) {
            mob.sendMessage("You need a lockpick for this lock.")
            return false
        }
        mob.sendMessage("You attempt to pick the lock.")
        if (mob.thieving.level < lock.level) {
            mob.sendMessage("You need level ${lock.level} Thieving to pick this lock.")
            return false
        }
        if (rand(99) < 50) {
            mob.sendMessage("You fail to pick the lock.")
            return false
        }
        mob.sendMessage("You manage to pick the lock.")
        mob.thieving.addExperience(lock.experience)
        return true
    }
}
