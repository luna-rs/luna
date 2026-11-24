package game.content.questRewards

import api.predef.*
import game.content.questRewards.QuestRewards.owns
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * Sir Prysin, in Varrock Palace.
 */
val SIR_PRYSIN = 883

/**
 * The sword that Sir Prysin keeps.
 */
val SILVERLIGHT = 2402

/**
 * What Sir Prysin asks for Silverlight.
 */
val SILVERLIGHT_PRICE = 500

npc1(SIR_PRYSIN) {
    // TODO Demon Slayer: before and during the quest he talks about Delrith and the three keys instead. Until the
    //  quest exists every player has finished it.
    val dialogue = plr.newDialogue()
        .npc(SIR_PRYSIN, Expression.HAPPY, "Hello. I've heard you stopped the demon, well done!")
    if (owns(plr, SILVERLIGHT)) {
        dialogue.then { boast(it) }
    } else {
        dialogue.options("Yes, that's right.", { boast(it) },
                         "Yes, although I'm afraid I've lost Silverlight.", { lostSilverlight(it) })
    }
    dialogue.open()
}

/**
 * [plr] takes Sir Prysin's praise.
 */
fun boast(plr: Player) {
    plr.newDialogue()
        .player("Yes, that's right.")
        .npc(SIR_PRYSIN, Expression.HAPPY, "A good job well done.")
        .player(Expression.HAPPY, "Thank you.")
        .open()
}

/**
 * Sir Prysin offers [plr] Silverlight back.
 */
fun lostSilverlight(plr: Player) {
    plr.newDialogue()
        .player("Yes, although I'm afraid I've lost Silverlight.")
        .npc(SIR_PRYSIN, Expression.ANGRY, "Yes, news of your carelessness is as widespread as",
             "news of your victory. Fortunately for you, Silverlight", "has come back into my possession.")
        .options("Phew, that's a relief!", {
            it.newDialogue().player("Phew, that's a relief!").open()
        }, "Is there any chance of me borrowing it again?", { borrow(it) })
        .open()
}

/**
 * Sir Prysin sells [plr] Silverlight.
 */
fun borrow(plr: Player) {
    plr.newDialogue()
        .player("Is there any chance of me borrowing it again?")
        .npc(SIR_PRYSIN, "I won't give it away that easily again. It's far too",
             "important to be treated so disrespectfully.")
        .npc(SIR_PRYSIN, "If you wish to make use of Silverlight again, it will cost",
             "you 500 gold pieces. Maybe that will encourage you to", "look after it.")
        .options("No way, it's not worth that much.", {
            it.newDialogue().player("No way, it's not worth that much.").open()
        }, "Okay, I'll pay.", {
            it.newDialogue().player("Okay, I'll pay.").then { plr ->
                if (!QuestRewards.canPay(plr, SILVERLIGHT_PRICE)) {
                    plr.newDialogue().npc(SIR_PRYSIN, Expression.ANGRY, "You don't even have enough money.").open()
                } else if (!owns(plr, SILVERLIGHT) && QuestRewards.give(plr, SILVERLIGHT, SILVERLIGHT_PRICE)) {
                    plr.newDialogue().npc(SIR_PRYSIN, "May you make good use of it.").open()
                }
            }.open()
        })
        .open()
}
