package game.combat.npcHooks.apeAtollSkeleton

import api.combat.death.DeathHookHandler
import api.predef.*
import io.luna.game.event.impl.RegionChangedEvent
import io.luna.game.model.mob.Player

// The rest of a skeleton's death is the default one, which drops its bones.
DeathHookHandler.addNpcHook(ApeAtollSkeletons.SKELETON) {
    death {
        val killer = source
        if (killer is Player) ApeAtollSkeletons.killed(killer)
    }
    DeathHookHandler.defaultNpcHook?.invoke(this)
}

on(RegionChangedEvent::class) { ApeAtollSkeletons.crumble(plr) }
