package engine.combat.status.hooks

import engine.combat.status.BasicStatusEffect
import engine.combat.status.StatusEffectType
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.EntityState
import kotlin.time.Duration

/**
 * Immobilization status effect, preventing a [Mob] from moving.
 *
 * This effect clears the affected mob's current walking queue when applied. Its expiry is measured against the
 * world tick, so processing the victim's status action during the application tick does not shorten the freeze.
 * Status queries also use this deadline, allowing expiry-tick input before the status action removes the effect.
 *
 * @param mob The mob being immobilized.
 * Player-cast freezes on players release movement when the caster leaves the world or moves more than ten tiles
 * away. Once released, returning within range cannot restore the movement lock. The status owner retains the
 * original deadline separately for spell eligibility and immunity.
 *
 * @param duration The amount of time the immobilization should last.
 * @param caster The player whose presence sustains a PvP spell freeze, or null for other immobilization.
 * @author lare96
 */
open class ImmobilizedStatusEffect(mob: Mob, duration: Duration, private val caster: Player? = null) :
    BasicStatusEffect<Mob>(mob,
                           type = StatusEffectType.IMMOBILIZED,
                           duration,
                           startMsg = "You have been frozen!") {

    /**
     * World tick on which the status action should remove this freeze.
     */
    var expiresAt = 0L
        private set

    /**
     * Whether a PvP caster has already left range; movement release is permanent for this application.
     */
    private var released = false

    override fun onStart(restored: Boolean) {
        expiresAt = mob.world.currentTick + countdown
        mob.walking.clear()
    }

    override fun hasExpired(): Boolean {
        if (caster != null && mob is Player &&
            (caster.state != EntityState.ACTIVE || !caster.position.isWithinDistance(mob.position, 10))) {
            released = true
        }
        return released || mob.world.currentTick >= expiresAt
    }

    override fun process() {
        // isReady decrements after this hook; include that decrement when projecting the absolute deadline.
        countdown = if (hasExpired()) 1 else
            (expiresAt - mob.world.currentTick + 1).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }
}
