package game.content.shieldOfArrav

import api.predef.*
import game.content.heroesQuest.HeroesGang
import game.content.shieldOfArrav.ShieldOfArrav as Quest
import game.obj.doors.QuestDoors
import game.obj.entrances.Entrances
import io.luna.game.event.impl.DefaultTabOpenedEvent
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex

on(DefaultTabOpenedEvent::class).filter { tab == TabIndex.QUEST }.then { Quest.refresh(plr) }
button(Quest.JOURNAL_BUTTON) { Quest.journal(plr) }

npc1(Quest.RELDO) { Quest.reldo(plr, targetNpc) }
npc1(Quest.BARAEK) { Quest.baraek(plr, targetNpc) }
npc1(Quest.TRAMP) { Quest.tramp(plr, targetNpc) }
npc1(Quest.STRAVEN) {
    if (!HeroesGang.speakToGangLeader(plr, targetNpc, forPhoenix = true)) Quest.straven(plr, targetNpc)
}
npc1(Quest.KATRINE) {
    if (!HeroesGang.speakToGangLeader(plr, targetNpc, forPhoenix = false)) Quest.katrine(plr, targetNpc)
}
npc1(Quest.CURATOR) { Quest.curator(plr, targetNpc) }
npc1(Quest.ROALD) { Quest.roald(plr, targetNpc) }
useItem(Quest.REPORT).onNpc(Quest.STRAVEN) { Quest.straven(plr, targetNpc) }
useItem(Quest.CERTIFICATE).onNpc(Quest.ROALD) { Quest.roald(plr, targetNpc) }
useItem(Quest.STORE_KEY).onObject(Quest.WEAPONS_STORE_DOOR) {
    if (plr.inventory[usedItemIndex]?.id == Quest.STORE_KEY) QuestDoors.cross(plr, gameObject)
}

object2(Quest.BOOKCASE) { Quest.searchBookcase(plr) }
item1(Quest.BOOK) { Quest.readBook(plr) }
object1(Quest.PHOENIX_CHEST) { Quest.open(gameObject, Quest.PHOENIX_CHEST_OPEN) }
object1(Quest.PHOENIX_CHEST_OPEN) { Quest.searchHalf(plr, phoenixSide = true) }
object2(Quest.PHOENIX_CHEST_OPEN) { Entrances.replace(gameObject, Quest.PHOENIX_CHEST) }
object1(Quest.BLACK_ARM_CUPBOARD) { Quest.open(gameObject, Quest.BLACK_ARM_CUPBOARD_OPEN) }
object1(Quest.BLACK_ARM_CUPBOARD_OPEN) { Entrances.replace(gameObject, Quest.BLACK_ARM_CUPBOARD) }
object2(Quest.BLACK_ARM_CUPBOARD_OPEN) { Quest.searchHalf(plr, phoenixSide = false) }
