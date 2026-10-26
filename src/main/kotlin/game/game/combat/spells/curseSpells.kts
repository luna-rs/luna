package game.combat.spells

import api.combat.magic.CombatSpellHandler.immobilize
import api.combat.magic.CombatSpellHandler.spell
import api.combat.magic.CombatSpellHandler.drainCurse
import api.predef.*
import api.predef.ext.*
import engine.combat.prayer.CombatPrayer
import engine.combat.status.hooks.TeleBlockStatusEffect
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.CombatSpell
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.minutes

/**
 * The standard Tele Block duration in minutes. Converted to seconds for the status effect and halved when the
 * victim has Protect from Magic active at impact.
 */
val TELE_BLOCK_MINUTES = 5

// Weakening curse spells.
spell(CombatSpell.CONFUSE) {
    drainCurse(victim, victim.attack, 0.05)
}
spell(CombatSpell.WEAKEN) {
    drainCurse(victim, victim.strength, 0.05)
}
spell(CombatSpell.CURSE) {
    drainCurse(victim, victim.defence, 0.05)
}
spell(CombatSpell.VULNERABILITY) {
    drainCurse(victim, victim.defence, 0.10)
}
spell(CombatSpell.ENFEEBLE) {
    drainCurse(victim, victim.strength, 0.10)
}
spell(CombatSpell.STUN) {
    drainCurse(victim, victim.attack, 0.10)
}

// Immobilizing curse spells.
spell(CombatSpell.BIND) {
    immobilize(victim, 8.ticks)
}
spell(CombatSpell.SNARE) {
    immobilize(victim, 16.ticks)
}
spell(CombatSpell.ENTANGLE) {
    immobilize(victim, 24.ticks)
}

// Teleblock curse spell.
spell(CombatSpell.TELEBLOCK) {
    if (victim is Player && victim.isAlive) {
        val plr = victim.asPlr()
        var duration = TELE_BLOCK_MINUTES.minutes.inWholeSeconds.toInt()
        if (plr.combat.prayers.isActive(CombatPrayer.PROTECT_FROM_MAGIC)) {
            duration /= 2
        }
        val ticks = duration.seconds.inTicks()
        if (plr.status.add(TeleBlockStatusEffect(plr, ticks.ticks))) {
            val time = when (val minutes = duration / 60) {
                0 -> "under a minute"
                1 -> "1 minute"
                else -> "$minutes minutes"
            }
            plr.sendMessage("A Tele Block has been cast on you. It will expire in $time.")
        } else if (attacker is Player) {
            attacker.asPlr().sendMessage("That player is currently immune to this spell.")
        }
    }
}
