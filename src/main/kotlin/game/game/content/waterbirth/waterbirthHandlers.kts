package game.content.waterbirth

import api.combat.death.DeathHookHandler
import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Mob

on(ServerLaunchEvent::class) { Waterbirth.start(world) }
for (route in Jarvald.Route.entries) for (id in route.crew) {
    npc1(id) { Jarvald.talk(plr, targetNpc) }
    npc3(id) { Jarvald.sail(plr, targetNpc) }
}
for (gate in Waterbirth.Gate.entries) object1(gate.id) { Waterbirth.operate(plr, gameObject) }
for (support in Waterbirth.Support.entries) {
    npc2(support.npc, { plr, target ->
        plr.combat.getNextAttack(target as Mob).also { plr.combat.firstAttack = it }.interactionPolicy
    }, false) {
        if (Waterbirth.support(targetNpc) == support && targetNpc.combat.isAttackable) plr.combat.attack(targetNpc)
    }
    combat(support.npc) {
        attack { Waterbirth.idleAttack(npc, other) }
        defend {
            if (Waterbirth.support(npc) == support && (other !is Player || damage == null ||
                    !Waterbirth.canStrike(support, other.position, damage!!.type, source.victim, npc))) {
                damage = null
                (other as? Player)?.sendMessage("This door does not seem to be openable from this side...")
                if (other.combat.target === npc) other.combat.target = null
            }
        }
    }
    DeathHookHandler.addNpcHook(support.npc) {
        val npc = victim
        if (Waterbirth.support(npc) == support) death {
            if (!Waterbirth.destroyed(npc) && npc.state == io.luna.game.model.EntityState.ACTIVE) npc.world.npcs.remove(npc)
        } else DeathHookHandler.defaultNpcHook?.invoke(this)
    }
}
