package game.combat.npcHooks.wallBeast

import api.combat.npc.NpcCombatHandler.combat
import api.combat.player.PlayerCombatHandler.playerStopAttack
import api.predef.*
import io.luna.game.event.impl.PlayerTickEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.Npc

/**
 * Installs final-position wall contact and native crushing combat on the existing anchored hole resources.
 * Dormant forms cannot award damage experience or die through indirect damage before a legitimate reveal.
 *
 * @author lare96
 */
on(PlayerTickEvent::class) { WallBeasts.onPlayerTick(plr) }

on(ServerLaunchEvent::class) {
    world.npcs.forEach { WallBeasts.initialize(it) }
}

playerStopAttack { attack ->
    val npc = attack.victim as? Npc
    if (npc == null || npc.id != WallBeasts.DORMANT && npc.id != WallBeasts.ACTIVE) false else {
        val failure = WallBeasts.failureMessage(this, npc)
        if (failure != null) {
            sendMessage(failure)
            true
        } else !WallBeasts.permitsFight(this, npc)
    }
}

combat(WallBeasts.DORMANT) {
    poisonCanKill = false
    damageExperienceMultiplier = 0.0
    attack { WallBeasts.attack(npc, other) }
    defend { damage = null }
}

combat(WallBeasts.ACTIVE) {
    attack {
        combat.setAttackBonusType(EquipmentBonus.CRUSH_ATTACK)
        WallBeasts.attack(npc, other)
    }
    defend { damage = WallBeasts.defend(npc, other, damage) }
}
