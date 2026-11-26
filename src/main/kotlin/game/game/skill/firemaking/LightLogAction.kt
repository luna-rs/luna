package game.skill.firemaking

import api.predef.*
import api.bot.isRealBot
import api.predef.ext.*
import game.player.Sound
import engine.bot.economy.BotProductionMetrics
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.Direction
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.ObjectType

/**
 * A [LightAction] implementation that enables lighting logs to create fires. Coloured logs pass their own item,
 * experience and fire object, and light like [Log.NORMAL] otherwise.
 *
 * @author lare96
 */
class LightLogAction(plr: Player, val log: Log, val removeLog: Boolean, val logId: Int = log.id,
                     val exp: Double = log.exp, val fireId: Int = Firemaking.FIRE_OBJECT,
                     private val groundLog: GroundItem? = null) :
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
                    if (mob.inventory.remove(logId)) {
                        logGroundItem = world.addItem(logId, 1, mob.position, mob)
                        mob.sendMessage("You light the ${itemName(logId).lowercase()}...")
                        return true
                    }
                    return false
                }
                logGroundItem = groundLog?.takeIf { it.position == mob.position && it.isVisibleTo(mob) }
                if (groundLog != null && logGroundItem == null) return false
                mob.sendMessage("You light the ${itemName(logId).lowercase()}...")
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
            if (mob.isRealBot()) BotProductionMetrics.recordSink(logId)
            val firePosition = mob.position
            mob.playSound(Sound.FIRE_LIT)
            mob.firemaking.addExperience(exp)

            // Walk in a non-blocked direction prioritizing west.
            for (dir in WALK_DIRECTIONS) {
                if (mob.navigator.step(dir)) {
                    mob.actions.submit(object : LockedAction(mob, false, 1) {
                        override fun run(): Boolean {
                            mob.face(dir.opposite())
                            return true
                        }
                    })
                    break
                }
            }
            val fireObject = world.addObject(fireId, firePosition)
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

