package game.content.impCatcher

import api.predef.*
import io.luna.game.event.impl.DefaultTabOpenedEvent
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex

on(DefaultTabOpenedEvent::class).filter { tab == TabIndex.QUEST }.then { ImpCatcher.refresh(plr) }
button(ImpCatcher.JOURNAL_BUTTON) { ImpCatcher.journal(plr) }
npc1(ImpCatcher.MIZGOG) { ImpCatcherDialogue.talk(plr) }
