package game.combat.npcHooks.mogre

import api.combat.npc.NpcCombatHandler.combat
import api.combat.player.PlayerCombatHandler.playerStopAttack
import api.predef.*
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy

/**
 * Connects all three native fishing spots to owned explosive throws and applies the Mogre's Slayer requirement
 * at both attack launch and queued hit delivery. Native melee remains crushing independently of equipment settings.
 *
 * @author lare96
 */
for (id in Mogres.SPOTS.keys) {
    useItem(Mogres.EXPLOSIVE).onObject(id, InteractionPolicy.UNSPECIFIED_BIF) {
        Mogres.throwExplosive(plr, gameObject, usedItemIndex)
    }
    object1(id, InteractionPolicy.UNSPECIFIED_BIF) {
        if (Mogres.registeredSpot(plr, gameObject) &&
            plr.position.isWithinDistance(gameObject.position, Mogres.MAX_THROW)) {
            plr.sendMessage("Something seems to have scared all the fish away...")
        }
    }
    object2(id, InteractionPolicy.UNSPECIFIED_BIF) {
        if (Mogres.registeredSpot(plr, gameObject) &&
            plr.position.isWithinDistance(gameObject.position, Mogres.MAX_THROW)) {
            plr.sendMessage("Something seems to have scared all the fish away...")
        }
    }
}

playerStopAttack { attack ->
    val target = attack.victim as? Npc
    val failure = if (target?.id == Mogres.NPC_ID) Mogres.failureMessage(this) else null
    if (failure == null) false else {
        sendMessage(failure)
        true
    }
}

combat(Mogres.NPC_ID) {
    attack {
        combat.setAttackBonusType(EquipmentBonus.CRUSH_ATTACK)
        melee()
    }
    defend {
        if (other is Player) {
            val failure = Mogres.failureMessage(other)
            if (failure != null) {
                damage = null
                other.sendMessage(failure)
            }
        }
    }
}
