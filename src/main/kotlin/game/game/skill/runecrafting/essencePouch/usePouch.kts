package game.skill.runecrafting.essencePouch

import api.predef.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.DropItemEvent

for (pouch in EssencePouch.entries) {
    for (id in pouch.ids) {
        item1(id) { pouch.fill(plr, index) }
        item2(id) { pouch.empty(plr) }
        item3(id) { pouch.check(plr) }
    }
}

on(DropItemEvent::class, EventPriority.NORMAL) {
    val pouch = EssencePouch.ID_TO_POUCH[itemId]
    if (pouch != null && plr.inventory[index] == null && plr.attributes()[pouch.essence] > 0) {
        pouch.discardContents(plr)
        plr.sendMessage("The contents of the pouch fell out as you dropped it!")
    }
}
