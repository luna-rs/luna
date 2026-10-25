package game.content.miningGuild

import api.predef.ext.*
import game.obj.doors.DoorPassageAction
import game.obj.doors.DoorType
import game.obj.doors.Doors
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection

/**
 * Mining Guild access and door passage from Lost City's 377 content scripts.
 * Uses the same conditional passage pattern as the Al Kharid border gate.
 *
 * @author lare96
 */
object MiningGuild {

    const val DWARF = 382
    const val DOOR = 2112
    const val REQUIRED_LEVEL = 60

    /**
     * The conditional door is owned by this script, rather than registered as a freely opening door.
     */
    private val doorType = DoorType(DOOR, 1532, null, null, null, 3)

    /**
     * Checks the current Mining level, including boosts and drains, at either entrance.
     */
    fun checkAccess(plr: Player): Boolean {
        if (plr.skill(Skill.MINING).level >= REQUIRED_LEVEL) {
            return true
        }
        plr.newDialogue()
            .npc(DWARF, "Sorry, but you're not experienced enough", "to go in there.")
            .text("You need a Mining level of 60 to access the Mining Guild.")
            .open()
        return false
    }

    /**
     * Returns the opposite side of a straight door using Lost City's check_axis rule.
     */
    fun doorDestination(door: Position, direction: ObjectDirection, from: Position): Position {
        val entering = Doors.isEntering(door, direction, from)
        return if (entering) door.translate(1, direction.toNormalDirection()) else door
    }

    /**
     * Walks to the door and crosses it after checking the player's Mining level.
     */
    fun openDoor(plr: Player, door: GameObject) {
        if (checkAccess(plr) && plr.actions.first(Crossing::class.java) == null) {
            plr.submitAction(Crossing(plr, door))
        }
    }

    /**
     * Aligns with the entrance, waits for any other crossing, then takes a forced step through.
     * Like BorderGate.Crossing, walking away cancels this weak action.
     *
     * @author lare96
     */
    private class Crossing(plr: Player, door: GameObject) : DoorPassageAction(plr, door, doorType) {
        override fun checkAccess(entering: Boolean): Boolean = MiningGuild.checkAccess(mob)
    }
}
