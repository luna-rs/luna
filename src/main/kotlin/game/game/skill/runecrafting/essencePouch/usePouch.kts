package game.skill.runecrafting.essencePouch

import api.predef.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.DropItemEvent
import io.luna.game.event.impl.EquipItemEvent
import io.luna.game.event.impl.LoginEvent

for (pouch in EssencePouch.entries) {
    for (id in pouch.ids) {
        item1(id) { pouch.fill(plr, index) }
        item3(id) { pouch.check(plr) }
    }
}

// The client sends every item's second option as the equip packet, so "Empty" arrives here.
on(EquipItemEvent::class, EventPriority.NORMAL) {
    EssencePouch.ID_TO_POUCH[itemId]?.empty(plr)
}

on(DropItemEvent::class, EventPriority.NORMAL) {
    val pouch = EssencePouch.ID_TO_POUCH[itemId]
    if (pouch != null && plr.inventory[index] == null && plr.attributes()[pouch.essence] > 0) {
        pouch.discardContents(plr)
        plr.sendMessage("The contents of the pouch fell out as you dropped it!")
    }
}

on(LoginEvent::class) {
    // Only attributes read during a session are saved again, so read the pouches now or a session that never
    // touches them would lose their essence and wear.
    for (pouch in EssencePouch.entries) {
        plr.attributes()[pouch.essence]
        plr.attributes()[pouch.essenceId]
        plr.attributes()[pouch.wear]
    }
}
