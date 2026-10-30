package game.combat.npcHooks

import api.combat.npc.NpcCombatHandler.combat
import api.combat.player.PlayerCombatHandler.playerStopAttack
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player

/**
 * Registers native harpie protection at both attack preparation and hit delivery, alongside crushing melee.
 * A late equipment change cannot bypass the lit-lantern requirement through an already launched projectile.
 *
 * @author lare96
 */
playerStopAttack { attack ->
    val target = attack.victim as? Npc
    val failure = if (target?.id == HarpieBugSwarms.NPC_ID) HarpieBugSwarms.failureMessage(this) else null
    if (failure == null) false
    else {
        sendMessage(failure)
        true
    }
}

combat(HarpieBugSwarms.NPC_ID) {
    attack {
        combat.setAttackBonusType(EquipmentBonus.CRUSH_ATTACK)
        melee()
    }
    defend {
        if (other is Player) {
            val failure = HarpieBugSwarms.failureMessage(other)
            if (failure != null) {
                damage = null
                other.sendMessage(failure)
            }
        }
    }
}
