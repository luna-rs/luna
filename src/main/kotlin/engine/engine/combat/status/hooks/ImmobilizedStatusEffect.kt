package engine.combat.status.hooks

import engine.combat.status.BasicStatusEffect
import engine.combat.status.StatusEffectType
import io.luna.game.model.mob.Mob
import kotlin.time.Duration

/**
 * Immobilization status effect, preventing a [Mob] from moving.
 *
 * This effect clears the affected mob's current walking queue when applied.
 *
 * @param mob The mob being immobilized.
 * @param duration The amount of time the immobilization should last.
 * @author lare96
 */
open class ImmobilizedStatusEffect(mob: Mob, duration: Duration) :
    BasicStatusEffect<Mob>(mob,
                           type = StatusEffectType.IMMOBILIZED,
                           duration,
                           startMsg = "You have been frozen!") {

    override fun onStart(restored: Boolean) {
        mob.walking.clear()
    }
}