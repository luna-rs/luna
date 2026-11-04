package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.combat.attack.MagicCombatAttack
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType

/**
 * Salarin the twisted, deep in the Yanille Agility Dungeon.
 */
val SALARIN = 205

/**
 * What each Strike spell hits Salarin for whenever it doesn't splash.
 */
val STRIKE_HITS = mapOf(CombatSpell.WIND_STRIKE to 9, CombatSpell.WATER_STRIKE to 10,
                        CombatSpell.EARTH_STRIKE to 11, CombatSpell.FIRE_STRIKE to 12)

/*
 * Only Strike spells hurt Salarin, and each always hits him for its own amount; the last hit is capped by his
 * remaining hitpoints when it's dealt. Every other attack lands for nothing.
 */
combat(SALARIN) {
    defend {
        if (other !is Player) return@defend
        val hit = STRIKE_HITS[(source as? MagicCombatAttack<*>)?.spellEffect?.spell]
        val incoming = damage
        if (hit == null) {
            damage = CombatDamageRequest.zero(other, npc, incoming?.type ?: CombatDamageType.MELEE).resolve()
            npc.speak("Your pitiful attacks cannot hurt me!")
        } else if (incoming != null && incoming.amount.isPresent) {
            damage = CombatDamageRequest.builder(other, npc, CombatDamageType.MAGIC)
                .setBaseAccuracy(1.0)
                .setFlatBonusAccuracy(1.0)
                .setBaseMaxHit(0)
                .setFlatBonusDamage(hit)
                .build()
                .resolve()
        }
    }
}
