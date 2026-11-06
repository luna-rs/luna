package game.content.fightPit

import api.predef.*
import game.npc.spawn.tzhaar.TzHaarNames
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder
import io.luna.game.model.mob.dialogue.DialogueUtils

/**
 * TzHaar-Mej-Kah, who runs the Fight Pit.
 */
val MEJ_KAH = FightPit.MEJ_KAH

/**
 * Adds [text] as Mej-Kah's, over as many pages as it takes.
 */
fun DialogueQueueBuilder.mejKah(text: String): DialogueQueueBuilder {
    var dialogue = this
    for (page in DialogueUtils.splitChatLines(text).toList().chunked(4)) {
        dialogue = dialogue.npc(MEJ_KAH, *page.toTypedArray())
    }
    return dialogue
}

fun rules(plr: Player) {
    plr.newDialogue()
        .player("Are there any rules?")
        .mejKah("No rules, you use whatever you want. Last person standing wins and is declared champion, they " +
                    "stay in the pit for next fight.")
        .options("Do I win anything?", {
            it.newDialogue()
                .player("Do I win anything?")
                .mejKah("You ask a lot of questions. Champion gets TokKul as reward, more fights the more TokKul " +
                            "they get.")
                .player("...")
                .mejKah("Before you ask, TokKul is like your Coins.")
                .mejKah("Gold is like you JalYt, soft and easily broken, we use hard rock forged in fire like " +
                            "TzHaar!")
                .open()
        }, "Sounds good.", { it.newDialogue().player("Sounds good.").open() })
        .open()
}

fun whatIsThisPlace(plr: Player) {
    plr.newDialogue()
        .player("What is this place?")
        .mejKah("This is the fight pit, TzHaar-Xil made it for their sport but many JalYt come here to fight too. " +
                    "If you are wanting to fight then enter the cage, you will be summoned when next round is " +
                    "ready to begin.")
        .options("Are there any rules?", { rules(it) },
                 "Ok thanks.", { it.newDialogue().player("Ok thanks.").open() })
        .open()
}

fun whatAreYou(plr: Player) {
    plr.newDialogue()
        .player("What are you then?")
        .mejKah("Foolish JalYt, I am TzHaar-Mej, one of the mystics of this city.")
        .options("What other types are there?", {
            it.newDialogue()
                .player("What other types are there?")
                .mejKah("There are the mighty TzHaar-Ket who guard us, the swift TzHaar-Xil who hunt for our " +
                            "food, and the skilled TzHaar-Hur who craft our homes and tools.")
                .open()
        }, "Ah ok then.", { it.newDialogue().player("Ah ok then.").open() })
        .open()
}

npc1(MEJ_KAH) {
    plr.newDialogue()
        .mejKah("You want help ${TzHaarNames.name(plr)}?")
        .options("What is this place?", { whatIsThisPlace(it) },
                 "Who's the current champion?", {
                     it.newDialogue()
                         .player("Who's the current champion?")
                         .mejKah("Ah that would be ${FightPit.championName}!")
                         .open()
                 },
                 "What did you call me?", { TzHaarNames.whatDidYouCallMe(it, MEJ_KAH) { p -> whatAreYou(p) } },
                 "No I'm fine thanks.", { it.newDialogue().player("No I'm fine thanks.").open() })
        .open()
}
