package engine.combat.status.hooks

import engine.combat.status.BasicStatusEffect
import engine.combat.status.StatusEffectType
import io.luna.game.model.mob.Mob
import kotlin.time.Duration

/**
 * Immobilization status effect, preventing a [Mob] from moving.
 *
 * This effect clears the affected mob's current walking queue when applied. Its expiry is measured against the
 * world tick, so processing the victim's status action during the application tick does not shorten the freeze.
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

    /**
     * World tick on which the status action should remove this freeze.
     */
    private var expiresAt = 0L

    override fun onStart(restored: Boolean) {
        expiresAt = mob.world.currentTick + countdown
        mob.walking.clear()
    }

    override fun process() {
        // isReady decrements after this hook; include that decrement when projecting the absolute deadline.
        countdown = (expiresAt - mob.world.currentTick + 1).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }
}
