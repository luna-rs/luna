package game.content.hardwoodGrove

import api.predef.*
import api.predef.ext.*
import api.shop.dsl.ShopHandler
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop

for (id in 9038..9039) {
    object1(id) { HardwoodGrove.useDoor(plr, gameObject, false) }
    object2(id) { HardwoodGrove.useDoor(plr, gameObject, true) }
}
for (id in 2528..2530) npc1(id) { HardwoodGrove.talkToMurcaily(plr, targetNpc) }
for (id in 2519..2521) {
    npc1(id) { HardwoodGrove.talkToGabooty(plr, targetNpc) }
    npc3(id) { HardwoodGrove.openShop(plr, targetNpc, HardwoodGrove.COOPERATIVE) }
    npc4(id) { HardwoodGrove.openShop(plr, targetNpc, HardwoodGrove.DRINKS) }
}

// The 377 cache supplies the item values. Stock counts and restocking use Luna's local shop policy.
ShopHandler.create(HardwoodGrove.COOPERATIVE) {
    currency = Currency.TRADING_STICKS
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    stockPricing = Shop.StockPricing(0.6, 0.03)
    sell {
        for (id in 6341..6379 step 2) id x 3
        6313 x 3
        6315 x 3
        6317 x 3
        6311 x 0
        1625 x 0
        1627 x 0
        1629 x 0
        1609 x 0
        1611 x 0
        1613 x 0
    }
}
ShopHandler.create(HardwoodGrove.DRINKS) {
    currency = Currency.TRADING_STICKS
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.SLOW
    stockPricing = Shop.StockPricing(0.6, 0.03)
    sell { 2048 x 0; 2054 x 0; 2064 x 0; 2074 x 0; 2080 x 0; 2084 x 0; 2092 x 0 }
}

// Native anchors are from LostCity's m44_48 (Murcaily) and m43_47 (Gabooty).
on(ServerLaunchEvent::class) {
    HardwoodGrove.initializeMerchants()
}
