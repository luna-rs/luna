package game.skill.agility

import api.predef.*
import game.player.Sound
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.ExactMovement
import io.luna.game.model.mob.block.PlayerModelAnimation
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag
import io.luna.net.msg.out.SoundMessageWriter

/**
 * Shows the obstacle's [startMessage], moves to its failure point, stumbles with the failure message, and lands two
 * ticks later without awarding course progress. Keeps the player locked and restores their movement animations even
 * when interrupted.
 *
 * @author lare96
 */
class AgilityFailureAction(plr: Player, private val failure: AgilityFailure,
                           private val startMessage: String? = null) : LockedAction(plr) {

    private var previousAnimation: PlayerModelAnimation? = null
    private var ticks = 0
    private var fallTick = 0

    /**
     * The stone the last jump lands on. The player is only moved there when the next jump or the stumble starts, so
     * the client never sees them on it before the jump.
     */
    private var stone: Position? = null

    override fun onLock() {
        previousAnimation = mob.model
        mob.walking.clear()
        startMessage?.let { mob.sendMessage(it) }
        val approach = failure.approach ?: return
        val distance = maxOf(1, mob.position.computeLongestDistance(approach))
        if (failure.walkingAnimation == -1) {
            fallTick = distance * Agility.STONE_TICKS
            jumpStone(approach)
        } else {
            fallTick = distance
            AgilityWalk.modelFor(failure.walkingAnimation, previousAnimation)?.let {
                mob.model = it
                mob.flags.flag(UpdateFlag.APPEARANCE)
            }
            mob.walkStraight(approach)
        }
    }

    override fun run(): Boolean {
        if (failure.approach == null) {
            land()
            return true
        }
        ticks++
        when (failure) {
            AgilityFailure.BARBARIAN_LOG -> if (ticks == 3) {
                mob.queue(SoundMessageWriter(Sound.LOG_BALANCE.id, 4, 0))
            }
            AgilityFailure.BARBARIAN_LEDGE -> if (ticks == 2) {
                mob.queue(SoundMessageWriter(Sound.BALANCING_LEDGE.id, 2, 10))
            }
            AgilityFailure.WILDERNESS_LOG -> if (ticks == 1) {
                mob.queue(SoundMessageWriter(Sound.LOG_BALANCE.id, 3, 0))
            }
            AgilityFailure.WILDERNESS_STONES -> if (ticks < fallTick && ticks % Agility.STONE_TICKS == 0) {
                failure.approach?.let { jumpStone(it) }
            }
            else -> Unit
        }
        if (ticks == fallTick) {
            landOnStone()
            mob.animation(Animation(764))
            mob.queue(SoundMessageWriter(Sound.STUMBLE_LOOP.id, 10, 0))
            mob.sendMessage(failure.message)
        } else if (ticks == fallTick + 2) {
            land()
            return true
        }
        return false
    }

    override fun onUnlock() {
        previousAnimation?.let { mob.model = it }
        mob.flags.flag(UpdateFlag.APPEARANCE)
        mob.walking.isRunning = mob.isRunning
        mob.walking.clear()
        mob.animation(Animation(-1))
    }

    /**
     * Jumps one stepping stone towards [approach], as the crossing does.
     */
    private fun jumpStone(approach: Position) {
        landOnStone()
        val from = mob.position
        val direction = Direction.between(from, approach)
        val next = from.translate(1, direction)
        mob.animation(Animation(Agility.STONE_JUMP_ANIMATION))
        mob.playSound(Sound.JUMP_NO_LAND)
        mob.exactMove(ExactMovement(mob.lastRegion, from, next, Agility.STONE_JUMP_START, Agility.STONE_JUMP_END,
            direction))
        stone = next
    }

    private fun landOnStone() {
        stone?.let { mob.move(it) }
        stone = null
    }

    /**
     * Applies the fall through the normal damage pipeline so lethal falls still cause death.
     */
    private fun land() {
        mob.move(failure.landing)
        if (failure.approach == null) mob.sendMessage(failure.message)
        mob.damage(failure.damage(mob.hitpoints.level))
        when (failure) {
            AgilityFailure.BARBARIAN_LOG -> mob.playSound(Sound.WATERSPLASH)
            AgilityFailure.WILDERNESS_STONES -> mob.playSound(Sound.SIZZLE)
            else -> Unit
        }
    }
}
