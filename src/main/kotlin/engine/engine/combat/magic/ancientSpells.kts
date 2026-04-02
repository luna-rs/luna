package engine.combat.magic

import api.attr.Attr
import api.combat.magic.CombatSpellHandler.immobilize
import api.combat.magic.CombatSpellHandler.spell
import api.combat.magic.CombatSpellHandler.weaken
import api.predef.*
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.CombatDamage
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.util.RandomUtils
import io.luna.util.Rational
import kotlin.math.floor

/**
 * The chance for a smoke spell to apply poison.
 */
val SMOKE_SPELLS_POISON_CHANCE = Rational(1, 8)

/**
 * A cooldown timer used to limit how often the blood-spell drain message is sent to a player.
 */
val Player.drainMsgTimer by Attr.timer()

/**
 * Attempts to poison a victim using the configured smoke-spell poison chance.
 *
 * If the poison roll succeeds, the victim's poison severity is set to the supplied value.
 *
 * @param victim The mob being poisoned.
 * @param severity The poison severity to apply on a successful roll.
 */
fun poison(victim: Mob, severity: Int) {
    if (RandomUtils.roll(SMOKE_SPELLS_POISON_CHANCE)) {
        victim.combat.poisonSeverity = severity
    }
}

/**
 * Heals the attacking mob for a quarter of the damage dealt.
 *
 * The heal amount is calculated as {@code floor(damage * 0.25)}. If the resulting value is less than {@code 1}, no
 * healing is applied.
 *
 * When the attacker is a {@link Player}, a drain message may also be sent, subject to the player's drain-message timer.
 *
 * @param attacker The mob receiving the healing effect.
 * @param damage The combat damage instance used to derive the heal amount.
 */
fun heal(attacker: Mob, damage: CombatDamage) {
    val healAmount = floor(damage.amount.orElse(0) * 0.25).toInt()
    if (healAmount < 1) {
        return
    }

    attacker.hitpoints.addLevels(healAmount, false)

    if (attacker is Player && (!attacker.drainMsgTimer.isRunning && attacker.drainMsgTimer.duration.toSeconds() >= 1)) {
        attacker.sendMessage("You drain some of your opponent's health.")
        attacker.drainMsgTimer.reset().start()
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
    heal(attacker, this)
}
spell(CombatSpell.BLOOD_BURST) {
    heal(attacker, this)
}
spell(CombatSpell.BLOOD_BLITZ) {
    heal(attacker, this)
}
spell(CombatSpell.BLOOD_BARRAGE) {
    heal(attacker, this)
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
    immobilize(victim, 8)
}
spell(CombatSpell.ICE_BURST) {
    immobilize(victim, 16)
}
spell(CombatSpell.ICE_BLITZ) {
    immobilize(victim, 24)
}
spell(CombatSpell.ICE_BARRAGE) {
    immobilize(victim, 32)
}