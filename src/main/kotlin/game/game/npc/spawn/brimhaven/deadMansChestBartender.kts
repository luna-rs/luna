package game.npc.spawn.brimhaven

import api.predef.*
import game.npc.spawn.Bartender
import io.luna.game.model.mob.dialogue.Expression

/**
 * The bartender of the Dead Man's Chest, Brimhaven.
 */
val DEAD_MANS_CHEST_BARTENDER = 735

/**
 * A pint of grog.
 */
val GROG = 1915

/**
 * What a pint of grog costs.
 */
val GROG_PRICE = 3

/**
 * A bottle of Karamjan rum.
 */
val KARAMJAN_RUM = 431

/**
 * What a bottle of rum costs.
 */
val RUM_PRICE = 27

npc1(DEAD_MANS_CHEST_BARTENDER) {
    plr.newDialogue()
        .npc(DEAD_MANS_CHEST_BARTENDER, Expression.LAUGHING, "Yohoho me hearty what would you like to drink?")
        .options("Nothing, thank you.", {
            it.newDialogue().player("Nothing, thank you.").open()
        }, "A pint of Grog please.", {
            it.newDialogue()
                .player(Expression.HAPPY, "A pint of Grog please.")
                .npc(DEAD_MANS_CHEST_BARTENDER, Expression.HAPPY, "One grog coming right up, that'll be three coins.")
                .then { plr ->
                    Bartender.serve(plr, GROG, GROG_PRICE, "Oh dear. I don't seem to have enough money.",
                                    "You buy a pint of Grog.")
                }
                .open()
        }, "A bottle of rum please.", {
            it.newDialogue()
                .player(Expression.HAPPY, "A bottle of rum please.")
                .npc(DEAD_MANS_CHEST_BARTENDER, Expression.HAPPY, "That'll be 27 coins.")
                .then { plr ->
                    Bartender.serve(plr, KARAMJAN_RUM, RUM_PRICE, "Oh dear. I don't seem to have enough money.",
                                    "You buy a bottle of rum.")
                }
                .open()
        })
        .open()
}
