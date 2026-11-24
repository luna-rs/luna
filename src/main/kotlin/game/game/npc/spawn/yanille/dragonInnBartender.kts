package game.npc.spawn.yanille

import api.predef.*
import game.npc.spawn.Bartender
import io.luna.game.model.mob.dialogue.Expression

/**
 * The bartender of the Dragon Inn, Yanille.
 */
val DRAGON_INN_BARTENDER = 739

/**
 * A pint of Dragon bitter.
 */
val DRAGON_BITTER = 1911

/**
 * A pint of Greenman's ale.
 */
val GREENMANS_ALE = 1909

npc1(DRAGON_INN_BARTENDER) {
    plr.newDialogue()
        .npc(DRAGON_INN_BARTENDER, Expression.QUIZZICAL, "What can I get you?")
        .player(Expression.QUIZZICAL, "What's on the menu?")
        .npc(DRAGON_INN_BARTENDER, Expression.HAPPY, "Dragon Bitter and Greenman's Ale.")
        .options("I'll give it a miss I think.", {
            it.newDialogue()
                .player("I'll give it a miss I think.")
                .npc(DRAGON_INN_BARTENDER, Expression.HAPPY, "Come back when you're a little thirstier.")
                .open()
        }, "I'll try the Dragon Bitter.", {
            it.newDialogue()
                .player(Expression.HAPPY, "I'll try the Dragon Bitter.")
                .npc(DRAGON_INN_BARTENDER, Expression.HAPPY, "Ok, that'll be two coins.")
                .then { plr ->
                    Bartender.serve(plr, DRAGON_BITTER, 2, "Oh dear. I don't seem to have enough money.",
                                    "You buy a pint of Dragon Bitter.")
                }
                .open()
        }, "Can I have some Greenman's Ale?", {
            it.newDialogue()
                .player(Expression.QUIZZICAL, "Can I have some Greenman's Ale?")
                .npc(DRAGON_INN_BARTENDER, Expression.HAPPY, "Ok, that'll be ten coins.")
                .then { plr ->
                    Bartender.serve(plr, GREENMANS_ALE, 10, "Oh dear. I don't seem to have enough money.",
                                    "You buy a pint of Greenman's Ale.")
                }
                .open()
        })
        .open()
}
