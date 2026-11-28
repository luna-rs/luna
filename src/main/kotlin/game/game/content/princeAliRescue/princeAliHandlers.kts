package game.content.princeAliRescue

import api.predef.*
import io.luna.game.event.impl.DefaultTabOpenedEvent
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex

val quest = PrinceAliRescue

on(DefaultTabOpenedEvent::class).filter { tab == TabIndex.QUEST }.then { quest.refresh(plr) }
button(quest.JOURNAL_BUTTON) { quest.journal(plr) }

npc1(quest.HASSAN) { PrinceAliContacts.hassan(plr, targetNpc) }
npc1(quest.OSMAN) { PrinceAliContacts.osman(plr, targetNpc) }
npc1(quest.LEELA) { PrinceAliContacts.leela(plr, targetNpc) }
npc1(quest.KELI) { DraynorJail.keli(plr, targetNpc) }
npc1(quest.JOE) { DraynorJail.joe(plr, targetNpc) }
npc1(quest.ALI) { DraynorJail.ali(plr, targetNpc) }

useItem(quest.YELLOW_DYE).onItem(quest.WIG) { PrinceAliSupplies.dyeWig(plr) }
useItem(quest.BEER).onNpc(quest.JOE) { DraynorJail.offerBeer(plr, targetNpc) }
useItem(quest.ROPE).onNpc(quest.KELI) { DraynorJail.tieKeli(plr) }
useItem(quest.BRONZE_KEY).onObject(quest.CELL_DOOR) { DraynorJail.unlock(plr, gameObject) }
for (item in quest.disguise) {
    useItem(item).onNpc(quest.ALI) { DraynorJail.ali(plr, targetNpc) }
}
