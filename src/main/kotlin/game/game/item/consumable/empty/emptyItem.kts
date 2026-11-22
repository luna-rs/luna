package game.item.consumable.empty

import api.predef.*
import io.luna.game.event.impl.ItemClickEvent.ItemFourthClickEvent
import io.luna.game.model.item.Item

val BOWL = 1923
val BURNT_STEW = 2005
val BURNT_CURRY = 2013

// Nearly every vessel has "Empty" as its fourth menu option.
on(ItemFourthClickEvent::class) { EmptyVessel.empty(plr, index, id, 3) }

// Spoilt cocktails and burnt bowl dishes have it first.
listOf(2094, 2096, 2098, 2100, 7090, 7092, 7094).forEach { id ->
    item1(id) { EmptyVessel.empty(plr, index, id, 0) }
}

mapOf(BURNT_STEW to "stew", BURNT_CURRY to "curry").forEach { (id, dish) ->
    item1(id) {
        if (plr.inventory[index]?.id == id) {
            plr.inventory[index] = Item(BOWL)
            plr.sendMessage("You empty the burnt $dish from the bowl.")
        }
    }
}
