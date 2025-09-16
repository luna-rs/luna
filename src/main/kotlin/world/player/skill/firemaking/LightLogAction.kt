package world.player.skill.firemaking

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.Direction
import io.luna.game.model.EntityType
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.ObjectType
import world.player.Sounds

/**
 * A [LightAction] implementation that enables lighting logs to create fires.
 */
class LightLogAction(plr: Player, val log: Log, val removeLog: Boolean) :
    LightAction(plr, Firemaking.computeLightDelay(plr, log)) {

    /**
     * The position that the log will be placed on.
     */
    private var logGroundItem: GroundItem? = null

    companion object {

        /**
         * Directions in prioritized order to try to walk after lighting a log
         */
        private val WALK_DIRECTIONS: List<Direction> =
            listOf(Direction.WEST, Direction.EAST, Direction.SOUTH, Direction.NORTH)
    }

    override fun canLight(): Boolean {

        return when {
            blocked() -> {
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
                        logGroundItem = world.addItem(log.id, 1, mob.position, mob)
                        mob.sendMessage("You light the ${itemName(log.id).lowercase()}...")
                        return true
                    }
                    return false
                }
                mob.sendMessage("You light the ${itemName(log.id).lowercase()}...")
                return true
            }
        }
    }

    override fun onLight() {
        if (blocked()) {
            mob.sendMessage("You cannot light a fire here.")
            return
        }
        light()
    }

    /**
     * Attempts to light the fire if the log is still on the ground.
     */
    private fun light() {
        if (logGroundItem != null && world.removeItem(logGroundItem!!)) {
            val firePosition = mob.position
            when {
                originalDelayTicks < 2 -> mob.playSound(Sounds.BURN_LOG_QUICK)
                else -> mob.playSound(Sounds.BURN_LOG)
            }
            mob.firemaking.addExperience(log.exp)

            // Walk in a non-blocked direction prioritizing west.
            val collision = mob.world.collisionManager
            for (dir in WALK_DIRECTIONS) {
                if (collision.traversable(mob.position, EntityType.NPC, dir)) {
                    val newPosition = mob.position.translate(1, dir)
                    mob.lock(3)
                    mob.walking.walk(newPosition)
                    world.scheduleOnce(2) {
                        mob.face(dir.opposite())
                    }
                    break
                }
            }
            val fireObject = world.addObject(Firemaking.FIRE_OBJECT, firePosition)
            world.scheduleOnce(rand(Firemaking.BURN_TIME)) {
                world.removeObject(fireObject)
                world.addItem(Firemaking.ASHES, 1, fireObject.position)
            }
        }
    }

    /**
     * Determines if the player cannot light a fire on their current position.
     */
    private fun blocked(): Boolean {
        return world.objects.findAll(mob.position)
            .filter {
                it.objectType == ObjectType.DEFAULT ||
                        it.objectType == ObjectType.STRAIGHT_WALL || it.objectType == ObjectType.DIAGONAL_WALL ||
                        it.objectType == ObjectType.WALL_CORNER || it.objectType == ObjectType.DIAGONAL_CORNER_WALL
            }.count() > 0
    }
}

