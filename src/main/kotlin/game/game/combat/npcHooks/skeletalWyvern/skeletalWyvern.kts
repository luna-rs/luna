package game.combat.npcHooks.skeletalWyvern

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.combat.attack.CombatAttack

/*
 * Registers skeletal wyvern combat behavior.
 *
 * Wyverns may choose between melee, icy breath, and ranged attacks depending on distance:
 *
 * - Within 2 tiles: melee, icy breath, or ranged
 * - Within 6 tiles: icy breath or ranged
 * - Beyond 6 tiles: ranged only
 */
combat(3068, 3069, 3070, 3071) {
    attack {
        val attacks = ArrayList<CombatAttack<Npc>>(3)

        if (npc.isWithinDistance(other, 2)) {
            // Can use all attacks.
            attacks += melee(
                animationId = if (rand().nextBoolean()) 2985 else 2986,
                maxHit = 13
            )
            attacks += WyvernIcyBreathAttack(npc, other)
            attacks += WyvernRangedAttack(npc, other)
        } else if (npc.isWithinDistance(other, 6)) {
            // Can use ranged and icy breath attacks.
            attacks += WyvernIcyBreathAttack(npc, other)
            attacks += WyvernRangedAttack(npc, other)
        } else {
            // Can use ranged attack only.
            attacks += WyvernRangedAttack(npc, other)
        }

        attacks.random()
    }
}