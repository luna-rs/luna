package game.combat.npcHooks.skeletalWyvern

import engine.combat.status.hooks.ImmobilizedStatusEffect
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import kotlin.time.Duration

/**
 * A skeletal wyvern icy breath status effect.
 *
 * This effect retains [ImmobilizedStatusEffect]'s immediate walking-queue cleanup, then sends the wyvern-specific
 * combat lockout message. The status type blocks movement, and the combat context recognizes this subclass to block
 * attacks while it is active.
 *
 * @param mob The mob affected by the icy breath.
 * @param duration The amount of time the icy breath effect should remain active.
 * @author lare96
 */
class WyvernIcyBreathStatusEffect(mob: Mob, duration: Duration) : ImmobilizedStatusEffect(mob, duration) {

    override fun onStart(restored: Boolean) {
        super.onStart(restored)
        if (mob is Player) {
            mob.sendMessage("You are unable to attack!")
        }
    }
}
