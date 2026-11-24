package game.content.petCats

import api.predef.*
import io.luna.game.event.impl.LogoutEvent
import io.luna.game.event.impl.PlayerTickEvent

// Register the saved cat state before any player files are read.
val petCats = PetCats

on(LogoutEvent::class) { petCats.logout(plr) }
on(PlayerTickEvent::class) { petCats.tick(plr) }

for (id in PetCat.NPCS) {
    npc1(id, false) { petCats.pickUp(plr, targetNpc) }
    npc2(id, false) { petCats.talk(plr, targetNpc) }
    npc3(id, false) { petCats.interact(plr, targetNpc) }
    for (item in PetCats.FISH + PetCats.BUCKET_OF_MILK + PetCats.BALL_OF_WOOL) {
        useItem(item).onNpc(id) { petCats.useOn(plr, targetNpc, usedItemIndex) }
    }
}
