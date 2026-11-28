package game.content.cooksAssistant

import api.predef.*
import io.luna.game.event.impl.DefaultTabOpenedEvent
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex

on(DefaultTabOpenedEvent::class).filter { tab == TabIndex.QUEST }.then { CooksAssistant.refresh(plr) }
button(CooksAssistant.JOURNAL_BUTTON) { CooksAssistant.journal(plr) }

npc1(CooksAssistant.COOK) {
    // The Treasure Trails first-click handler owns the Cook's anagram conversation.
    if (!plr.inventory.contains(CooksAssistant.COOK_ANAGRAM)) CooksAssistantDialogue.talk(plr)
}
