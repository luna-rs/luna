package game.combat.npcHooks.killerwatt

import api.combat.npc.NpcCombatHandler.combat
import api.combat.player.PlayerCombatHandler.playerStopAttack
import api.predef.*
import io.luna.game.event.impl.NpcClickEvent.AttackNpcEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.interact.InteractionPolicy

/**
 * Installs native click-wake before reach-based attacks, with shared manual/secondary-hit and Slayer-level guards.
 * Both original cache IDs retain native kill credit; no bot-only activation or new NPC placements are introduced.
 *
 * @author lare96
 */
on(AttackNpcEvent::class, interaction = InteractionPolicy.UNSPECIFIED_BIF) {
    if (targetNpc.id == Killerwatts.BALL || targetNpc.id == Killerwatts.ACTIVE) Killerwatts.wake(plr, targetNpc)
}

on(ServerLaunchEvent::class) {
    world.npcs.forEach { Killerwatts.initialize(it) }
}

playerStopAttack { attack ->
    val npc = attack.victim as? Npc
    if (npc == null || npc.id != Killerwatts.BALL && npc.id != Killerwatts.ACTIVE) false else {
        val failure = Killerwatts.failureMessage(this)
        if (failure != null) {
            sendMessage(failure)
            true
        } else npc.id == Killerwatts.BALL && !Killerwatts.wake(this, npc)
    }
}

combat(Killerwatts.BALL, Killerwatts.ACTIVE) {
    attack {
        combat.setAttackBonusType(EquipmentBonus.STAB_ATTACK)
        Killerwatts.attack(npc, other)
    }
    defend { damage = Killerwatts.defend(npc, other, damage) }
    afterDamage { Killerwatts.afterDamage(this, it) }
    afterRetaliate { other, action -> Killerwatts.retaliate(this, other, action) }
}
