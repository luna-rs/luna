package game.content.runeMysteries

import api.predef.*
import game.skill.runecrafting.essenceMine.EssenceMineWizard
import io.luna.game.event.impl.DefaultTabOpenedEvent
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex

on(DefaultTabOpenedEvent::class).filter { tab == TabIndex.QUEST }.then { RuneMysteries.refresh(plr) }
button(RuneMysteries.JOURNAL_BUTTON) { RuneMysteries.journal(plr) }

useItem(RuneMysteries.RESEARCH_PACKAGE).onNpc(EssenceMineWizard.AUBURY.npcId) {
    RuneMysteriesDialogue.deliverPackage(plr, targetNpc)
}

useItem(RuneMysteries.RESEARCH_NOTES).onNpc(EssenceMineWizard.SEDRIDOR.npcId) {
    RuneMysteriesDialogue.deliverNotes(plr, targetNpc)
}
