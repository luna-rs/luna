package game.item.degradable

import api.predef.*
import game.item.degradable.DegradableItems.CRYSTAL_ITEMS_CHARGES
import game.item.degradable.DegradableItems.charges
import game.item.degradable.DegradableItems.degrade
import io.luna.game.event.impl.CombatDamageReceivedEvent
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item

// Degrade crystal shield whenever a non-zero hit is received.
on(CombatDamageReceivedEvent::class) {
    if(damage.rawAmount > 0) {
        degrade(plr,
                Equipment.SHIELD,
                CRYSTAL_ITEMS_CHARGES,
                DegradableItemType.CRYSTAL_SHIELD,
                { --it.charges <= 0 },
                {
                    if (it == null) {
                        plr.giveItem(Item(4207)) // TODO Does seed appear in shield slot?
                        return@degrade "Your crystal shield reverts into a seed."
                    } else {
                        return@degrade "Your crystal shield has degraded a little."
                    }
                })
    }
}