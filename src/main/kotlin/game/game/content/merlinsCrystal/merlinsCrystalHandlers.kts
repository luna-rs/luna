package game.content.merlinsCrystal

import api.combat.npc.NpcCombatHandler.combat
import api.predef.*
import game.content.merlinsCrystal.MerlinsCrystal as Quest
import io.luna.game.event.impl.DefaultTabOpenedEvent
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex

on(DefaultTabOpenedEvent::class).filter { tab == TabIndex.QUEST }.then { Quest.refresh(plr) }
button(Quest.JOURNAL_BUTTON) { Quest.journal(plr) }

npc1(Quest.ARTHUR) { Quest.arthur(plr, targetNpc) }
npc1(Quest.GAWAIN) { Quest.gawain(plr, targetNpc) }
npc1(Quest.LANCELOT) { Quest.lancelot(plr, targetNpc) }
npc1(Quest.MORGAN) { Quest.morgan(plr, targetNpc) }
npc1(Quest.BEGGAR) { Quest.beggar(plr, targetNpc) }
npc1(Quest.MERLIN) {
    if (Quest.stage(plr) >= Quest.FREED_MERLIN) {
        plr.newDialogue().npc(Quest.MERLIN, "You broke the spell! Tell King Arthur that I'm free.").open()
    }
}

object2(Quest.ALTAR) { Quest.checkAltar(plr) }
object2(MerlinVoyage.CATHERBY_CRATE) { MerlinVoyage.offer(plr, gameObject, fromCatherby = true) }
object2(MerlinVoyage.KEEP_CRATE) { MerlinVoyage.offer(plr, gameObject, fromCatherby = false) }
object1(Quest.CRYSTAL) { Quest.smash(plr, useItem = false) }
useItem(Quest.EXCALIBUR).onObject(Quest.CRYSTAL) {
    if (plr.inventory[usedItemIndex]?.id == Quest.EXCALIBUR) Quest.smash(plr, useItem = true)
}

combat(Quest.MORDRED) {
    defend { damage = Quest.mordredDefence(npc, damage) }
}
