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
    // Stop hooks run every combat tick, so only the tick that fires the shot uses a charge.
    if (combat.ammoDef.type == AmmoType.CRYSTAL_ARROW && (combat.isAttackReady || it.isIgnoreAttackDelay)) {
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
    } else {
        // Not a crystal bow shot, ignore this hook.
        false
    }
}