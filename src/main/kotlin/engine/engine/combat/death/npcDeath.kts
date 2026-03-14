package engine.combat.death

import api.combat.death.DeathHookHandler
import api.predef.*
import game.skill.slayer.Slayer
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Animation.AnimationPriority

/**
 * Builds and registers the default death hook for all NPCs.
 */
DeathHookHandler.setDefaultHook(Npc::class) {
    preDeath {
        victim.animation(Animation(victim.combatDef.deathAnimation, AnimationPriority.IMMUTABLE))
    }

    death {
        if(source is Player) {
            Slayer.record(source, victim.id)
        }
        drop()
        world.npcs.remove(victim)
    }

    postDeath {
        // NPC respawned implicitly because it was removed in 'death'.
    }
}
