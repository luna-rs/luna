package game.content.romeoJuliet

import api.predef.*
import io.luna.game.event.impl.LoginEvent

on(LoginEvent::class) { RomeoJuliet.refresh(plr) }
button(RomeoJuliet.JOURNAL_BUTTON) { RomeoJuliet.journal(plr) }

npc1(RomeoJuliet.ROMEO) { RomeoJulietDialogue.romeo(plr, targetNpc) }
npc1(RomeoJuliet.JULIET_SPAWN) { RomeoJulietDialogue.juliet(plr, targetNpc) }
npc1(RomeoJuliet.FATHER_LAWRENCE) { RomeoJulietDialogue.father(plr, targetNpc) }
npc1(RomeoJuliet.APOTHECARY) { RomeoJulietDialogue.apothecary(plr, targetNpc) }

item1(RomeoJuliet.POTION) { plr.sendMessage("The Cadava potion is meant for Juliet.") }
item2(RomeoJuliet.POTION) { plr.sendMessage("You decide to save the Cadava potion for Juliet.") }
