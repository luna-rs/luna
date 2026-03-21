package engine.combat.magic

import api.combat.magic.CombatSpellHandler.immobilize
import api.combat.magic.CombatSpellHandler.spell
import api.combat.magic.CombatSpellHandler.weaken
import api.combat.magic.TeleBlockAction
import api.predef.*
import api.predef.ext.*
import engine.combat.prayer.CombatPrayer
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.CombatSpell
import kotlin.time.Duration.Companion.seconds

/**
 * The duration of a standard Tele Block spell effect.
 */
val TELE_BLOCK_MINUTES = 5

// Weakening curse spells.
spell(CombatSpell.CONFUSE) {
    weaken(victim, victim.attack, 0.05)
}
spell(CombatSpell.WEAKEN) {
    weaken(victim, victim.strength, 0.05)
}
spell(CombatSpell.CURSE) {
    weaken(victim, victim.defence, 0.05)
}
spell(CombatSpell.VULNERABILITY) {
    weaken(victim, victim.defence, 0.10)
}
spell(CombatSpell.ENFEEBLE) {
    weaken(victim, victim.strength, 0.10)
}
spell(CombatSpell.STUN) {
    weaken(victim, victim.attack, 0.10)
}

// Immobilizing curse spells.
spell(CombatSpell.BIND) {
    immobilize(victim, 8)
}
spell(CombatSpell.SNARE) {
    immobilize(victim, 16)
}
spell(CombatSpell.ENTANGLE) {
    immobilize(victim, 24)
}

// Teleblock curse spell.
spell(CombatSpell.TELEBLOCK) {
    if (victim is Player) {
        val plr = victim.asPlr()
        var duration = TELE_BLOCK_MINUTES.seconds.inWholeSeconds.toInt()
        if(plr.combat.prayers.isActive(CombatPrayer.PROTECT_FROM_MAGIC)) {
            duration /= 2
        }
        val ticks = duration.seconds.inTicks()
        if (plr.combat.setTeleBlock(ticks)) {
            val time = when (val minutes = duration / 60) {
                0 -> "under a minute"
                1 -> "1 minute"
                else -> "$minutes minutes"
            }
            plr.sendMessage("A Tele Block has been cast on you. It will expire in $time.")
        } else if(attacker is Player) {
            attacker.asPlr().sendMessage("That player is currently immune to this spell.")
        }
    }
}