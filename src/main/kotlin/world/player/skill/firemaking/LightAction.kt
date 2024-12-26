package world.player.skill.firemaking

import api.predef.ext.*
import io.luna.game.action.Action
import io.luna.game.action.ConditionalAction
import io.luna.game.action.RepeatingAction
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import world.player.Animations
import world.player.Sounds

/**
 * A [RepeatingAction] that allows a player to perform a generic firemaking based light action, where the end result
 * is determined by child classes.
 */
abstract class LightAction(plr: Player, val originalDelayTicks: Int) : ConditionalAction<Player>(plr, false, 1) {

    /**
     * The animation delay.
     */
    private var animationDelay: Int = 0

    /**
     * The mutable delay ticks.
     */
    private var delayTicks = originalDelayTicks

    final override fun start(): Boolean =
        if (!mob.inventory.contains(Firemaking.TINDERBOX)) {
            mob.sendMessage("You need a tinderbox to light this.")
            false
        } else {
            canLight()
        }

    final override fun condition(): Boolean {
        if (--delayTicks <= 0) {
            onLight()
            return false
        }
        handleLightAnimation()
        return true
    }

    final override fun stop() = mob.animation(Animation.CANCEL)

    override fun ignoreIf(other: Action<*>?) =
        when (other) {
            is LightAction -> true
            else -> false
        }

    /**
     * Determines what happens when you successfully light after the delay.
     */
    abstract fun onLight()

    /**
     * Further requirements for this action to proceed.
     */
    open fun canLight(): Boolean = true

    /**
     * Ensure the light animation only plays once every 1.8s, otherwise it stutters.
     */
    private fun handleLightAnimation() {
        if (--animationDelay <= 0) {
            mob.animation(Animations.FIREMAKING)
            mob.playSound(Sounds.LIGHT_FIRE)
            animationDelay = 3
        }
    }
}