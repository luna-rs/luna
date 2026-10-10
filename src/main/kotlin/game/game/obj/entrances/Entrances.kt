package game.obj.entrances

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.ExactMovement
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.game.model.`object`.GameObject
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * Moves players through cave and dungeon entrances.
 *
 * @author TheLining
 */
object Entrances {

    /**
     * Moves [plr] through [entrance].
     */
    fun use(plr: Player, entrance: Entrance) {
        travel(plr, entrance.landing.from(plr.position), entrance.message, entrance.delay, entrance.arrival,
               entrance.animation, entrance.arriveDelay, entrance.face)
    }

    /**
     * Moves [plr] to [destination], [delay] ticks after sending [message]. [arrival] is sent once they're there.
     *
     * @param plr The player.
     * @param destination Where they go.
     * @param message The message sent first, if any.
     * @param delay How many ticks after [message] they move.
     * @param arrival The message sent once they arrive, if any.
     * @param animation The animation played with [message], if any.
     * @param arriveDelay Whether to wait a tick first if they've only just walked up.
     * @param face The direction to face after arriving, if any.
     * @param onArrival Called once they arrive.
     */
    fun travel(plr: Player, destination: Position, message: String? = null, delay: Int = 0,
               arrival: String? = null, animation: Int? = null, arriveDelay: Boolean = false,
               face: Direction? = null,
               onArrival: (Player) -> Unit = {}) {
        val wait = if (arriveDelay && justArrived(plr)) 1 else 0
        plr.submitAction(object : LockedAction(plr) {
            override fun onLock() {
                mob.walking.clear()
            }

            override fun run(): Boolean {
                if (executions == wait) {
                    message?.let { mob.sendMessage(it) }
                    animation?.let { mob.animation(Animation(it)) }
                }
                if (executions == wait + delay) {
                    mob.move(destination)
                    face?.let { mob.face(it) }
                    arrival?.let { mob.sendMessage(it) }
                    onArrival(mob)
                    return true
                }
                return false
            }
        })
    }

    /**
     * Runs [steps] for [plr], one entry per tick, while they're locked. A `null` entry just waits.
     */
    fun sequence(plr: Player, vararg steps: (() -> Unit)?) {
        plr.submitAction(object : LockedAction(plr) {
            override fun onLock() {
                mob.walking.clear()
            }

            override fun run(): Boolean {
                steps[executions]?.invoke()
                return executions == steps.size - 1
            }
        })
    }

    /**
     * Moves [plr] smoothly to [destination], starting [startCycle] and ending [endCycle] client cycles (30 to a tick)
     * from now, while playing [animation] if there is one.
     */
    fun slide(plr: Player, destination: Position, startCycle: Int, endCycle: Int, animation: Int? = null) {
        animation?.let { plr.animation(Animation(it)) }
        val start = plr.position
        plr.exactMove(ExactMovement(plr.lastRegion, start, destination, startCycle, endCycle,
                                    Direction.between(start, destination)))
        plr.move(destination)
    }

    /**
     * Returns `true` if [plr] walked up to what they're using this tick, rather than already standing beside it.
     */
    fun justArrived(plr: Player) = plr.walkingDirection != Direction.NONE

    /**
     * Replaces [obj] with [id] for [ticks], then puts it back. Returns `false` and does nothing if someone else has
     * already replaced it.
     */
    fun replace(obj: GameObject, id: Int, ticks: Int): Boolean {
        if (!world.removeObject(obj)) {
            return false
        }
        val replacement = world.addObject(id, obj.position, obj.objectType, obj.direction)
        world.scheduleOnce(ticks) {
            if (world.removeObject(replacement)) {
                world.addObject(obj.id, obj.position, obj.objectType, obj.direction)
            }
        }
        return true
    }

    /**
     * Shows [plr] a picture of [itemId] with [text], without giving them the item, then runs [then] once they click
     * to continue.
     */
    fun itemBox(plr: Player, itemId: Int, text: String, then: (Player) -> Unit = {}) {
        val box = object : DialogueInterface(306) {
            override fun init(player: Player): Boolean {
                player.sendText(text, 308)
                player.queue(WidgetItemModelMessageWriter(307, 250, itemId))
                return true
            }
        }
        box.setContinueAction { then(it) }
        plr.overlays.open(box)
    }

    /**
     * Removes [obj] for [ticks], then puts it back. Returns `false` and does nothing if it's already gone.
     */
    fun remove(obj: GameObject, ticks: Int): Boolean {
        if (!world.removeObject(obj)) {
            return false
        }
        world.scheduleOnce(ticks) { world.addObject(obj.id, obj.position, obj.objectType, obj.direction) }
        return true
    }
}
