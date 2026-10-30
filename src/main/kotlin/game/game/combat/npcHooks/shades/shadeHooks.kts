package game.combat.npcHooks.shades

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent

/**
 * Installs the recovered Lost City shade combat, pre-hit awakening and shared lifecycle on existing spawns.
 * Source guesses and Luna's admission rules are documented by [MorttonShades].
 *
 * @author lare96
 */
combat(*(MorttonShades.FORMS.keys + MorttonShades.FORMS.values).toIntArray()) {
    attack { MorttonShades.attack(npc, other) }
    defend {
        if (MorttonShades.beforeHit(npc, other, damage)) {
            animationId = null
        }
    }
    afterDamage { MorttonShades.afterHit(this, it) }
    afterRetaliate { other, _ -> MorttonShades.afterRetaliate(this, other) }
}

on(ServerLaunchEvent::class) {
    world.schedule(1) { MorttonShades.process() }
}
