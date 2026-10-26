package game.combat.spells

import api.combat.magic.CombatSpellHandler.immobilize
import api.combat.magic.CombatSpellHandler.spell
import api.combat.magic.CombatSpellHandler.weaken
import api.predef.*
import api.predef.ext.*
import engine.combat.status.hooks.PoisonStatusEffect
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.combat.damage.CombatDamage
import kotlin.math.floor

/**
 * The chance for an accurate smoke spell hit to apply poison after damage delivery.
 */
val SMOKE_SPELLS_POISON_CHANCE = 1 of 8

/**
 * Attempts to poison the surviving recipient of one applied smoke spell hit.
 *
 * @param victim The actual recipient of the primary or secondary hit.
 * @param severity The poison severity to apply on a successful roll.
 */
fun poison(victim: Mob, severity: Int) {
    if (victim.isAlive && rand(SMOKE_SPELLS_POISON_CHANCE)) {
        victim.status.add(PoisonStatusEffect(victim, severity))
    }
}

/**
 * Heals a living caster for one quarter of damage actually delivered, rounded down.
 *
 * The hit has already passed defensive cancellation, reduction, and health capping. A lethal hit still heals from
 * its applied amount even though the recipient now has zero health. Effect-only hits and zero damage grant no healing.
 *
 * @param damage The applied primary or secondary blood spell hit.
 */
fun heal(damage: CombatDamage) {
    val attacker = damage.attacker
    val amount = floor(damage.rawAmount.coerceAtLeast(0) * 0.25).toInt()
    if (attacker.isAlive && amount > 0) {
        attacker.hitpoints.adjustLevel(amount)
        if (attacker is Player) {
            attacker.sendMessage("You drain some of your opponent's health.")
        }
    }
}

// Smoke spells.
spell(CombatSpell.SMOKE_RUSH) {
    poison(victim, 10)
}
spell(CombatSpell.SMOKE_BURST) {
    poison(victim, 10)

}
spell(CombatSpell.SMOKE_BLITZ) {
    poison(victim, 20)
}
spell(CombatSpell.SMOKE_BARRAGE) {
    poison(victim, 20)
}

// Blood spells.
spell(CombatSpell.BLOOD_RUSH) {
    heal(this)
}
spell(CombatSpell.BLOOD_BURST) {
    heal(this)
}
spell(CombatSpell.BLOOD_BLITZ) {
    heal(this)
}
spell(CombatSpell.BLOOD_BARRAGE) {
    heal(this)
}

// Shadow spells.
spell(CombatSpell.SHADOW_RUSH) {
    weaken(victim, victim.attack, 0.10)
}
spell(CombatSpell.SHADOW_BURST) {
    weaken(victim, victim.attack, 0.10)
}
spell(CombatSpell.SHADOW_BLITZ) {
    weaken(victim, victim.attack, 0.15)
}
spell(CombatSpell.SHADOW_BARRAGE) {
    weaken(victim, victim.attack, 0.15)
}

// Ice spells.
spell(CombatSpell.ICE_RUSH) {
    immobilize(attacker, victim, 8.ticks)
}
spell(CombatSpell.ICE_BURST) {
    immobilize(attacker, victim, 16.ticks)
}
spell(CombatSpell.ICE_BLITZ) {
    immobilize(attacker, victim, 24.ticks)
}
spell(CombatSpell.ICE_BARRAGE) {
    immobilize(attacker, victim, 32.ticks)
}
