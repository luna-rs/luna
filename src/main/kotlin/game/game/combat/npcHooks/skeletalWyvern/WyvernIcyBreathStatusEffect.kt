package game.combat.npcHooks.skeletalWyvern

import engine.combat.status.hooks.ImmobilizedStatusEffect
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import kotlin.time.Duration

/**
 * A skeletal wyvern icy breath status effect.
 *
 * This effect extends [ImmobilizedStatusEffect], so the target is already frozen by the superclass. This subclass adds
 * the wyvern-specific combat lockout behavior and player-facing message used when icy breath takes effect.
 *
 * @param mob The mob affected by the icy breath.
 * @param duration The amount of time the icy breath effect should remain active.
 * @author lare96
 */
class WyvernIcyBreathStatusEffect(mob: Mob, duration: Duration) : ImmobilizedStatusEffect(mob, duration) {

    override fun onStart(restored: Boolean) {
        if (mob is Player) {
            mob.sendMessage("You are unable to attack!")
        }
    }
}