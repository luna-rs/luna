package engine.combat.status.hooks

import engine.combat.status.BasicStatusEffect
import engine.combat.status.StatusEffectType
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.block.Graphic
import kotlin.time.Duration

/**
 * Stun status effect.
 *
 * While active, this disables the affected mob's combat actions and locks the player.
 *
 * @param mob The mob being stunned.
 * @param duration The amount of time the stun should last.
 * @author lare96
 */
class StunnedStatusEffect(mob: Mob, duration: Duration) :
    BasicStatusEffect<Mob>(mob,
                           type = StatusEffectType.STUNNED,
                           duration,
                           startMsg = "You have been stunned.") {

    override fun onStart(restored: Boolean) {
        mob.graphic(Graphic(80, 60, 5))
        mob.walking.clear()
    }
}