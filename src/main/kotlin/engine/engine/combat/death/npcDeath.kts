package engine.combat.death

import api.combat.death.DeathHookHandler
import api.predef.*
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Animation.AnimationPriority

/**
 * Builds and registers the default death hook for all NPCs.
 */
DeathHookHandler.setDefaultHook(Npc::class) {
    preDeath {
        victim.combatDef.ifPresent { victim.animation(Animation(it.deathAnimation, AnimationPriority.HIGH)) }
    }

    death {
        drop()
        world.npcs.remove(victim)
    }

    postDeath {
        // NPC respawned implicitly because it was removed in 'death'.
    }
}
