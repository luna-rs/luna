package game.item.degradable.combat

import api.predef.*
import game.item.degradable.DegradableEquipmentHandler.CRYSTAL_ITEMS_CHARGES
import game.item.degradable.DegradableEquipmentHandler.charges
import game.item.degradable.DegradableEquipmentHandler.degrade
import game.item.degradable.DegradableItemType
import io.luna.game.event.impl.CombatDamageReceivedEvent
import io.luna.game.model.item.DynamicItem
import io.luna.game.model.item.Equipment

// Degrade crystal shield whenever a non-zero hit is received.
on(CombatDamageReceivedEvent::class) {
    if (damage.rawAmount > 0) {
        degrade(plr,
                Equipment.SHIELD,
                CRYSTAL_ITEMS_CHARGES,
                { it == DegradableItemType.CRYSTAL_SHIELD },
                { --it.charges <= 0 },
                {
                    if (it !is DynamicItem) "Your crystal shield reverts into a seed."
                    else "Your crystal shield has degraded a little."
                })
    }
}