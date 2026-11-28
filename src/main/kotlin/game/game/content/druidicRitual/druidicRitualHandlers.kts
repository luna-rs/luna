package game.content.druidicRitual

import api.predef.*
import io.luna.game.event.impl.DefaultTabOpenedEvent
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex

on(DefaultTabOpenedEvent::class).filter { tab == TabIndex.QUEST }.then { DruidicRitual.refresh(plr) }
button(DruidicRitual.JOURNAL_BUTTON) { DruidicRitual.journal(plr) }
npc1(DruidicRitual.KAQEMEEX) { DruidicDialogue.kaqemeex(plr, targetNpc) }
npc1(DruidicRitual.SANFEW) { DruidicDialogue.sanfew(plr, targetNpc) }

for ((raw, enchanted) in DruidicRitual.meats) {
    useItem(raw).onObject(DruidicRitual.CAULDRON) { DruidicRitual.dip(plr, usedItemIndex, raw, enchanted) }
}
