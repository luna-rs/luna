package game.npc.spawn.ardougne

import api.predef.*
import game.npc.spawn.Bartender
import io.luna.game.model.mob.dialogue.Expression

/**
 * The bartender of the Flying Horse Inn, East Ardougne.
 */
val FLYING_HORSE_BARTENDER = 738

/**
 * A pint of beer.
 */
val BEER = 1917

npc1(FLYING_HORSE_BARTENDER) {
    plr.newDialogue()
        .npc(FLYING_HORSE_BARTENDER, Expression.QUIZZICAL, "Would you like to buy a drink?")
        .player(Expression.QUIZZICAL, "What do you serve?")
        .npc(FLYING_HORSE_BARTENDER, Expression.HAPPY, "Beer!")
        .options("I'll have a beer then.", {
            it.newDialogue()
                .player(Expression.HAPPY, "I'll have a beer then.")
                .npc(FLYING_HORSE_BARTENDER, Expression.HAPPY, "Ok, that'll be two coins.")
                .then { plr ->
                    Bartender.serve(plr, BEER, 2, "Oh dear. I don't seem to have enough money.",
                                    "You buy a pint of Beer.")
                }
                .open()
        }, "I'll not have anything then.", {
            it.newDialogue().player("I'll not have anything then.").open()
        })
        .open()
}
