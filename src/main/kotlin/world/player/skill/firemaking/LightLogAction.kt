package world.player.skill.firemaking

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.Action
import io.luna.game.model.Direction
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.ObjectType
import world.player.Sounds

/**
 * A [LightAction] implementation that enables lighting logs to create fires.
 */
class LightLogAction(plr: Player, val log: Log, val removeLog: Boolean) :
    LightAction(plr, Firemaking.computeLightDelay(plr, log)) {

    override fun canLight(): Boolean {
        return when {
            blocked(Direction.NONE) -> {
                mob.sendMessage("You cannot light a fire here.")
                false
            }

            mob.firemaking.level < log.level -> {
                mob.sendMessage("You need a Firemaking level of ${log.level} to light this.")
                false
            }

            else -> {
                if (removeLog) {
                    if (mob.inventory.remove(log.id)) {
                        world.addItem(log.id, 1, mob.position, mob)
                        mob.sendMessage("You light the ${itemName(log.id).toLowerCase()}...")
                        return true
                    }
                    return false
                }
                mob.sendMessage("You light the ${itemName(log.id).toLowerCase()}...")
                return true
            }
        }
    }

    override fun onLight() {
        if (blocked(Direction.NONE)) {
            mob.sendMessage("You cannot light a fire here.")
            return
        }
        lightFire()
    }

    override fun ignoreIf(other: Action<*>?) =
        when (other) {
            is LightLogAction -> log.id == other.log.id
            else -> false
        }

    /**
     * Attempts to light the fire if the log is still on the ground.
     */
    private fun lightFire() {
        if (world.items.removeFromPosition(mob.position) { it.id == log.id }) {
            val firePosition = mob.position
            when {
                originalDelayTicks < 2 -> mob.playSound(Sounds.BURN_LOG_QUICK)
                else -> mob.playSound(Sounds.BURN_LOG)
            }
            mob.firemaking.addExperience(log.exp)
            val direction = when {
                !blocked(Direction.WEST) -> Direction.WEST
                !blocked(Direction.EAST) -> Direction.EAST
                !blocked(Direction.SOUTH) -> Direction.SOUTH
                !blocked(Direction.NORTH) -> Direction.NORTH
                else -> Direction.NONE
            }
            if (direction != Direction.NONE) {
                mob.walking.walk(mob.position.translate(1, direction))
            }
            val fireObject = world.addObject(Firemaking.FIRE_OBJECT, firePosition)
            world.scheduleOnce(rand(Firemaking.BURN_TIME)) {
                world.removeObject(fireObject)
                world.addItem(Firemaking.ASHES, 1, fireObject.position)
            }
        }
    }

    /**
     * Determines if [direction] is blocked, [Direction.NONE] will check if a fire can be placed on the current tile.
     */
    private fun blocked(direction: Direction): Boolean {
        if (direction == Direction.NONE) {
            return world.objects.findAll(mob.position)
                .filter {
                    it.objectType == ObjectType.DEFAULT ||
                            it.objectType == ObjectType.STRAIGHT_WALL || it.objectType == ObjectType.DIAGONAL_WALL ||
                            it.objectType == ObjectType.WALL_CORNER || it.objectType == ObjectType.DIAGONAL_CORNER_WALL
                }.count() > 0
        }
        return !world.collisionManager.traversable(mob.position, TYPE_NPC, direction)
    }
}

