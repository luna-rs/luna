package game.content.sheepShearer

import api.predef.*
import game.skill.crafting.shearing.SheepShearing
import io.luna.game.event.impl.DefaultTabOpenedEvent
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex

on(DefaultTabOpenedEvent::class).filter { tab == TabIndex.QUEST }.then { SheepShearer.refresh(plr) }
button(SheepShearer.JOURNAL_BUTTON) { SheepShearer.journal(plr) }
npc1(SheepShearer.FRED) { SheepShearerDialogue.talk(plr) }
npc1(SheepShearerDialogue.THE_THING) { SheepShearerDialogue.shearThing(plr, targetNpc) }
useItem(SheepShearing.SHEARS).onNpc(SheepShearerDialogue.THE_THING) {
    SheepShearerDialogue.shearThing(plr, targetNpc)
}
