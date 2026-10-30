package game.combat.npcHooks.dagannothKings

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

combat(2881) { attack { DagannothAttack(npc, other, DagannothAttack.Style.SUPREME) } }
combat(2882) { attack { DagannothAttack(npc, other, DagannothAttack.Style.PRIME) } }
combat(2883) { attack { melee(animationId = 2853) } }
combat(2892, 2894, 2896) {
    attack {
        // No style script survived for the third visible cache variant; Luna gives it both projectile families.
        val magic = npc.id == 2892 || npc.id == 2896 && rand().nextBoolean()
        DagannothAttack(npc, other, if (magic) DagannothAttack.Style.SPINOLYP_MAGIC
            else DagannothAttack.Style.SPINOLYP_RANGED)
    }
}
// Adjacent weak melee is documented later; max one and the two-tick cadence are explicit Luna choices.
combat(2891, 2893, 2895) { attack { melee(animationId = -1, maxHit = 1, speed = 2) } }
on(ServerLaunchEvent::class) { world.schedule(1) { Spinolyps.process() } }
