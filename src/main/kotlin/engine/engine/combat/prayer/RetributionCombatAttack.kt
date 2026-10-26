package engine.combat.prayer

import engine.controllers.MultiCombatAreaListener
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamage
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.interact.InteractionPolicy

/**
 * A single Retribution recipient. Delivers an accurate 0..base-Prayer/4 hit through the ordinary defence and kill
 * attribution pipeline, ignoring protection prayers and granting no attack XP, poison, Smite, or chained explosion.
 * Single combat permits only the original opponent; multi combat permits adjacent legal targets in multi combat.
 *
 * @author lare96
 */
class RetributionCombatAttack(player: Player, target: Mob, private val origin: Position,
                             private val controller: PlayerController, private val opponent: Mob?,
                             private val maxHit: Int) :
    CombatAttack<Player>(player, target, InteractionPolicy.STANDARD_SIZE, 1) {

    /**
     * Rechecks activity ownership, world presence, occupied tiles, and combat restrictions at delivery.
     */
    fun canHit(): Boolean {
        if (attacker.state != EntityState.ACTIVE || attacker.isAlive || attacker.position != origin ||
            attacker.controllers.primary !== controller || victim.state != EntityState.ACTIVE ||
            !victim.isAlive || !victim.combat.isAttackable || victim.position.z != origin.z) return false
        val tile = victim.position
        if (tile.x > origin.x + 1 || tile.y > origin.y + 1 ||
            tile.x + victim.sizeX() - 1 < origin.x - 1 || tile.y + victim.sizeY() - 1 < origin.y - 1) return false
        val multi = MultiCombatAreaListener.inside(origin) && MultiCombatAreaListener.inside(tile)
        if (!multi && (victim !== opponent || !attacker.combat.checkMultiCombat(victim))) return false
        return controller.retribution(victim)
    }

    /**
     * Delivers without starting a new attack cycle for the dead player. Rechecking here prevents a moved or
     * disconnected target from being damaged by a previously selected burst.
     */
    fun deliver() {
        if (canHit()) {
            victim.combat.markAttackedBy(attacker)
            CombatDamageAction(calculateDamage(victim), this, true).run()
        }
    }

    override fun attack() = deliver()

    override fun calculateDamage(other: Mob): CombatDamage =
        CombatDamageRequest.damageIgnoringPrayers(attacker, other, CombatDamageType.MAGIC, maxHit).resolve()
}
