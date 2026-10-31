package game.skill.agility

import api.predef.*
import game.player.Sound
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.ExactMovement
import io.luna.game.model.mob.block.PlayerModelAnimation
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag
import io.luna.net.msg.out.SoundMessageWriter

/**
 * Moves to an obstacle's failure point, stumbles, and lands without awarding course progress.
 * Keeps the player locked and restores their movement animations even when interrupted.
 *
 * @author lare96
 */
class AgilityFailureAction(plr: Player, private val failure: AgilityFailure) : LockedAction(plr) {

    private var previousAnimation: PlayerModelAnimation? = null
    private var ticks = 0
    private var fallTick = 0
    private var prepared = false

    override fun onLock() {
        previousAnimation = mob.model
        mob.walking.clear()
        val approach = failure.approach
        if (approach != null) {
            fallTick = maxOf(1, mob.position.computeLongestDistance(approach))
        }
    }

    override fun run(): Boolean {
        val approach = failure.approach
        if (approach == null) {
            land()
            return true
        }
        if (!prepared) {
            mob.model = PlayerModelAnimation.Builder()
                .setStandingId(failure.walkingAnimation)
                .setStandingTurnId(failure.walkingAnimation)
                .setWalkingId(failure.walkingAnimation).build()
            mob.flags.flag(UpdateFlag.APPEARANCE)
            mob.animation(Animation(-1))
            prepared = true
            return false
        }
        if (ticks == 0) {
            mob.exactMove(ExactMovement.to(mob, approach))
            mob.move(approach)
        }
        when (failure) {
            AgilityFailure.BARBARIAN_LOG -> if (ticks == 2) {
                mob.queue(SoundMessageWriter(Sound.LOG_BALANCE.id, 4, 0))
            }
            AgilityFailure.BARBARIAN_LEDGE -> if (ticks == 1) {
                mob.queue(SoundMessageWriter(Sound.BALANCING_LEDGE.id, 2, 10))
            }
            AgilityFailure.WILDERNESS_LOG -> if (ticks == 0) {
                mob.queue(SoundMessageWriter(Sound.LOG_BALANCE.id, 3, 0))
            }
            AgilityFailure.WILDERNESS_STONES -> if (ticks < fallTick) mob.playSound(Sound.JUMP_NO_LAND)
            else -> Unit
        }
        if (ticks == fallTick) {
            mob.animation(Animation(764))
            mob.queue(SoundMessageWriter(Sound.STUMBLE_LOOP.id, 10, 0))
        } else if (ticks == fallTick + 2) {
            land()
            return true
        }
        ticks++
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
        mob.sendMessage(failure.message)
        mob.damage(failure.damage(mob.hitpoints.level))
        when (failure) {
            AgilityFailure.BARBARIAN_LOG -> mob.playSound(Sound.WATERSPLASH)
            AgilityFailure.WILDERNESS_STONES -> mob.playSound(Sound.SIZZLE)
            else -> Unit
        }
    }
}
