package game.npc.spawn.tzhaar

import api.predef.*
import io.luna.game.model.mob.dialogue.DialogueUtils

/**
 * TzHaar-Ket-Zuh, the TzHaar city's banker. He has no booth: players bank with him directly.
 */
val KET_ZUH = 2619

npc1(KET_ZUH) {
    plr.newDialogue()
        .npc(KET_ZUH, *DialogueUtils.splitChatLines("Can I help you ${TzHaarNames.name(plr)}?"))
        .options("I'd like to access my bank account, please.", {
            it.newDialogue()
                .player("I'd like to access my bank account, please.")
                .then { it.bank.open() }
                .open()
        }, "What did you call me?", {
            TzHaarNames.whatDidYouCallMe(it, KET_ZUH,
                                         "Daft JalYt, I am TzHaar-Ket, one of the guardians of our city.",
                                         "There are the wise TzHaar-Mej who guide us, the swift TzHaar-Xil who " +
                                             "hunt for our food, and the skilled TzHaar-Hur who craft our homes " +
                                             "and tools.")
        })
        .open()
}

npc2(KET_ZUH) { plr.bank.open() }
