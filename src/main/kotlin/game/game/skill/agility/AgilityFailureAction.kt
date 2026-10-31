package game.skill.agility

import api.predef.*
import game.player.Sound
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.Direction
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.ExactMovement
import io.luna.game.model.mob.block.PlayerModelAnimation
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag
import io.luna.net.msg.out.SoundMessageWriter

/**
 * Moves to an obstacle's failure point, stumbles with the failure message, and lands two ticks later without
 * awarding course progress. Keeps the player locked and restores their movement animations even when interrupted.
 *
 * @author lare96
 */
class AgilityFailureAction(plr: Player, private val failure: AgilityFailure) : LockedAction(plr) {

    private var previousAnimation: PlayerModelAnimation? = null
    private var ticks = 0
    private var fallTick = 0

    override fun onLock() {
        previousAnimation = mob.model
        mob.walking.clear()
        val approach = failure.approach
        if (approach != null) {
            fallTick = maxOf(1, mob.position.computeLongestDistance(approach))
            mob.model = PlayerModelAnimation.Builder()
                .setStandingId(failure.walkingAnimation)
                .setWalkingId(failure.walkingAnimation).build()
            mob.flags.flag(UpdateFlag.APPEARANCE)
            mob.exactMove(ExactMovement(mob.lastRegion, mob.position, approach, 0,
                fallTick * AgilityAction.CYCLES_PER_TICK, Direction.between(mob.position, approach)))
            mob.move(approach)
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
            AgilityFailure.WILDERNESS_STONES -> if (ticks <= fallTick) mob.playSound(Sound.JUMP_NO_LAND)
            else -> Unit
        }
        if (ticks == fallTick) {
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
        mob.animation(Animation(-1))
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
