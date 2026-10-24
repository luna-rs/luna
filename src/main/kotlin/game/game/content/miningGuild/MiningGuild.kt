package game.content.miningGuild

import api.predef.ext.*
import game.obj.doors.DoorType
import game.obj.doors.Doors
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import java.util.concurrent.CompletableFuture

/**
 * Mining Guild access and door passage from Lost City's 377 content scripts.
 * Uses the same conditional passage pattern as the Al Kharid border gate.
 *
 * @author lare96
 */
object MiningGuild {

    const val DWARF = 382
    const val DOOR = 2112

    /**
     * The conditional door is owned by this script, rather than registered as a freely opening door.
     */
    private val doorType = DoorType(DOOR, 1532, null, null, null, 3)

    /**
     * Door positions reserved for a crossing until their closed objects have been restored.
     */
    private val busy = HashSet<Position>()

    /**
     * Checks the current Mining level, including boosts and drains, at either entrance.
     */
    fun checkAccess(plr: Player): Boolean {
        if (plr.skill(Skill.MINING).level >= 60) {
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
        val entering = when (direction) {
            ObjectDirection.NORTH, ObjectDirection.SOUTH -> from.y == door.y
            ObjectDirection.WEST, ObjectDirection.EAST -> from.x == door.x
        }
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
    private class Crossing(plr: Player, door: GameObject) : Action<Player>(plr, ActionType.WEAK) {
        private val position = door.position
        private val direction = door.direction
        private val destination = doorDestination(position, direction, plr.position)
        private val start = if (destination == position) position.translate(1, direction.toNormalDirection()) else position
        private var walking: CompletableFuture<NavigationResult>? = null
        private var ticks = 0

        override fun run(): Boolean {
            if (++ticks > 30) {
                mob.sendMessage("You can't get through the door right now.")
                return true
            }
            val pending = walking
            if (pending != null && !pending.isDone) {
                return false
            }
            if (mob.position != start) {
                if (pending != null) {
                    mob.sendMessage("You can't reach the door from here.")
                    return true
                }
                walking = mob.navigator.navigate(start, false)
                return false
            }
            if (position in busy) {
                return false
            }
            if (!checkAccess(mob)) {
                return true
            }
            val closed = world.objects.findAll(position)
                .filter { it.id == DOOR && it.direction == direction }.findFirst().orElse(null) ?: return true
            val swing = Doors.singleSwap(closed, doorType, true) ?: return true
            if (!world.removeObject(closed)) {
                return true
            }
            val open = world.addObject(swing.id, swing.position, closed.objectType, swing.direction)
            val blocker = world.addObject(83, position, closed.objectType, direction)
            busy += position
            mob.playSound(doorType.openSoundOrDefault)
            world.scheduleOnce(doorType.durationOrDefault) {
                try {
                    world.removeObject(open)
                    world.removeObject(blocker)
                    world.addObject(closed.id, position, closed.objectType, direction)
                } finally {
                    busy -= position
                }
            }
            mob.walking.addStep(Direction.between(start, destination))
            return true
        }
    }
}
