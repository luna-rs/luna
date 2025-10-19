package world.player.skill.firemaking

import api.predef.ext.*
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import world.player.Animations
import world.player.Sounds

/**
 * An [Action] that allows a player to perform a generic firemaking based light action, where the end result
 * is determined by child classes.
 *
 * @author lare96
 */
abstract class LightAction(plr: Player, val originalDelayTicks: Int) : Action<Player>(plr, ActionType.WEAK) {

    /**
     * The animation delay.
     */
    private var animationDelay: Int = 0

    /**
     * The mutable delay ticks.
     */
    private var delayTicks = originalDelayTicks

    override fun onSubmit() {
        if (!mob.inventory.contains(Firemaking.TINDERBOX)) {
            mob.sendMessage("You need a tinderbox to light this.")
            complete()
        } else if (!canLight()) {
            complete()
        }
    }

    override fun run(): Boolean {
        if (--delayTicks <= 0) {
            onLight()
            return true
        }
        handleLightAnimation()
        return false
    }

    override fun onFinished() {
        mob.animation(Animation.CANCEL)
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