package game.combat.npcHooks.darkBeast

import api.combat.npc.NpcCombatHandler.combat
import api.combat.player.PlayerCombatHandler.playerStopAttack
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.Npc

/**
 * Registers native Slayer admission, delivered-hit confirmation and accepted retaliation for dark-beast combat.
 * Existing metadata supplies the reviewed later-correlated permanent aggression; no victim Slayer gate affects it.
 *
 * @author lare96
 */
playerStopAttack { attack ->
    val target = attack.victim as? Npc
    val failure = if (target?.id == DarkBeasts.NPC_ID) DarkBeasts.failureMessage(this) else null
    if (failure == null) false else {
        sendMessage(failure)
        true
    }
}

combat(DarkBeasts.NPC_ID) {
    attack {
        combat.setAttackBonusType(EquipmentBonus.CRUSH_ATTACK)
        DarkBeasts.attack(npc, other)
    }
    defend { damage = DarkBeasts.defend(npc, other, damage) }
    afterDamage { DarkBeasts.afterDamage(this, it) }
    afterRetaliate { other, action -> DarkBeasts.retaliate(this, other, action) }
}
