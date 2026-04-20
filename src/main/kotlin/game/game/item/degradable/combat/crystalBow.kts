package game.item.degradable.combat

import api.combat.player.PlayerCombatHandler.playerStopAttack
import api.predef.*
import game.item.degradable.DegradableEquipmentHandler.CRYSTAL_ITEMS_CHARGES
import game.item.degradable.DegradableEquipmentHandler.charges
import game.item.degradable.DegradableEquipmentHandler.degrade
import game.item.degradable.DegradableItemType
import io.luna.game.model.item.DynamicItem
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.combat.AmmoType

playerStopAttack {
    if (combat.ammoDef.type == AmmoType.CRYSTAL_ARROW) {
        if (equipment.weapon?.id == 4212) {
            // 0 charges but in bow state, terminate combat.
            sendMessage("This crystal bow needs to be charged before you can use it.")
            true
        } else {
            // Item still has remaining charges.
            degrade(this,
                    Equipment.WEAPON,
                    CRYSTAL_ITEMS_CHARGES,
                    { it == DegradableItemType.CRYSTAL_BOW },
                    { --it.charges <= 0 },
                    {
                        if (it !is DynamicItem) "Your crystal bow reverts into a seed."
                        else "Your crystal bow has degraded a little."
                    })
            false
        }
    } else {
        // We're not using a crystal bow, ignore this hook.
        false
    }
}