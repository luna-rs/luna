package game.content.rogueTrader

import api.predef.*
import game.content.rogueTrader.RuneCasket.Companion.casketPuzzle
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.mob.varp.Varbit

on(LoginEvent::class) {
    plr.sendVarbit(Varbit(RogueTrader.FEUD_VARBIT, RogueTrader.FEUD_FINISHED))
}

npc1(RogueTrader.ALI) { AliDialogue.talk(plr) }
npc1(RogueTrader.SIAMUN) { Suppliers.siamun(plr) }
npc1(RogueTrader.BLACKJACK_SELLER) { Suppliers.blackjackSeller(plr, targetNpc) }
npc1(RogueTrader.STREET_URCHIN) { Suppliers.urchin(plr, targetNpc) }

for (casket in RuneCasket.values()) {
    for (cell in casket.cells) {
        button(cell) {
            val puzzle = plr.casketPuzzle ?: return@button
            if (puzzle.casket == casket) {
                puzzle.place(cell - casket.firstCell)
                puzzle.show(plr)
            }
        }
    }
    casket.pickers.forEachIndexed { index, picker ->
        button(picker) {
            val puzzle = plr.casketPuzzle ?: return@button
            if (puzzle.casket == casket) {
                puzzle.selected = index + 1
            }
        }
    }
    button(casket.removeButton) {
        val puzzle = plr.casketPuzzle ?: return@button
        if (puzzle.casket == casket) {
            puzzle.selected = 0
        }
    }
    button(casket.openButton) {
        val puzzle = plr.casketPuzzle ?: return@button
        if (puzzle.casket != casket) {
            return@button
        }
        if (puzzle.solved()) {
            plr.overlays.closeWindows()
            casket.open(plr)
        } else {
            plr.sendMessage("The lock won't open.")
        }
    }
    button(casket.closeButton) {
        if (plr.casketPuzzle?.casket == casket) {
            plr.overlays.closeWindows()
        }
    }
}
