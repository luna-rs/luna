package game.content.questRewards

import api.predef.*
import game.content.questRewards.QuestRewards.owns
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * The Lady of the Lake, south of Taverley.
 */
val LADY_OF_THE_LAKE = 250

/**
 * The sword that the Lady keeps.
 */
val EXCALIBUR = 35

/**
 * What the Lady asks for Excalibur.
 */
val EXCALIBUR_PRICE = 500

npc1(LADY_OF_THE_LAKE) {
    // TODO Merlin's Crystal: before the quest reaches Morgan Le Faye she never offers Excalibur, and during it she sets
    //  the jeweller's test instead of selling it. Until the quest exists every player has finished it.
    val dialogue = plr.newDialogue()
        .npc(LADY_OF_THE_LAKE, "Good day to you ${if (plr.appearance.isFemale) "madam" else "sir"}.")
    if (owns(plr, EXCALIBUR)) {
        dialogue.options("Who are you?", { whoAreYou(it) },
                         "Good day.", { it.newDialogue().player("Good day.").open() })
    } else {
        dialogue.options("Who are you?", { whoAreYou(it) },
                         "Good day.", { it.newDialogue().player("Good day.").open() },
                         "I seek the sword Excalibur.", { seekExcalibur(it) })
    }
    dialogue.open()
}

/**
 * The Lady introduces herself to [plr].
 */
fun whoAreYou(plr: Player) {
    plr.newDialogue()
        .player("Who are you?")
        .npc(LADY_OF_THE_LAKE, "I am the Lady of the Lake.")
        .open()
}

/**
 * The Lady sells [plr] Excalibur back.
 */
fun seekExcalibur(plr: Player) {
    plr.newDialogue()
        .player(Expression.CONFUSED, "I seek the sword Excalibur.")
        .npc(LADY_OF_THE_LAKE, "Aye, I have that artefact in my possession.")
        .npc(LADY_OF_THE_LAKE, "'Tis very valuable, and not an artefact to be given", "away lightly.")
        .npc(LADY_OF_THE_LAKE, "I would want to give it away only to one who is worthy", "and good.")
        .npc(LADY_OF_THE_LAKE, "...But you have already proved thyself to be worthy of",
             "wielding it once already. I shall return it to you if you", "can prove yourself to still be worthy.")
        .player(Expression.QUIZZICAL, "...And how can I do that?")
        .npc(LADY_OF_THE_LAKE, "Why, by proving yourself to be above material goods.")
        .player(Expression.QUIZZICAL, "...And how can I do that?")
        .npc(LADY_OF_THE_LAKE, Expression.HAPPY, "500 gold coins ought to do it.")
        .then {
            if (!QuestRewards.canPay(it, EXCALIBUR_PRICE)) {
                it.newDialogue()
                    .player(Expression.SAD, "I don't have that kind of money...")
                    .npc(LADY_OF_THE_LAKE, "Well, come back when you do.")
                    .open()
            } else if (!owns(it, EXCALIBUR) && QuestRewards.give(it, EXCALIBUR, EXCALIBUR_PRICE)) {
                it.newDialogue()
                    .player(Expression.HAPPY, "Ok, here you go.")
                    .npc(LADY_OF_THE_LAKE, Expression.HAPPY, "You are still worthy to wield Excalibur! And thanks for",
                         "the cash! I felt like getting a new haircut!")
                    .open()
            }
        }
        .open()
}
