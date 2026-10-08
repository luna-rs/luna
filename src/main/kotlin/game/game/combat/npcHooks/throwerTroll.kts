package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import api.predef.ext.*
import io.luna.game.model.LocalProjectile
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.combat.damage.CombatDamageRequest
import io.luna.game.model.mob.combat.damage.CombatDamageType

/**
 * The graphic displayed on a Thrower Troll when it throws a rock.
 */
val ROCK_LAUNCH = Graphic(275, 100, 0)

/**
 * Builds the rock that travels from a Thrower Troll to its target.
 */
val ROCK: (Mob, Mob) -> LocalProjectile = { npc, other ->
    LocalProjectile.followEntity(ctx)
        .setSourceEntity(npc)
        .setTargetEntity(other)
        .setId(276)
        .setTicksToStart(44)
        .setTicksToEnd(3)
        .setStartHeight(36)
        .setEndHeight(36)
        .setInitialSlope(8)
        .build()
}

// Thrower Trolls on Death Plateau and in Trollheim.
combat(1101, 1102, 1103, 1104, 1105, 1130, 1131, 1132, 1133, 1134) {
    attack {
        ranged(ROCK_LAUNCH, ROCK, range = 8) {
            if (rand(1 of 5)) {
                npc.speak("Urg!")
            }
            // The rock never misses. Only Protect from Missiles stops it.
            CombatDamageRequest.Builder(npc, other, CombatDamageType.RANGED)
                .setBaseAccuracy(1.0).build().resolve()
        }
    }
}
