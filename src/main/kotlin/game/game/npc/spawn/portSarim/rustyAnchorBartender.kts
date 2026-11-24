package game.npc.spawn.portSarim

import api.predef.*
import game.npc.spawn.Bartender
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * The bartender of the Rusty Anchor, Port Sarim.
 */
val RUSTY_ANCHOR_BARTENDER = 734

/**
 * A pint of beer.
 */
val BEER = 1917

/**
 * What a pint of beer costs.
 */
val BEER_PRICE = 2

npc1(RUSTY_ANCHOR_BARTENDER) {
    if (Bartender.holdsClueFor(plr, RUSTY_ANCHOR_BARTENDER)) return@npc1
    plr.newDialogue()
        .options("Could I buy a beer please?", { buyBeer(it) },
                 "Not very busy in here today, is it?", { notBusy(it) })
        .open()
}

/**
 * The bartender sells [plr] a pint of beer.
 */
fun buyBeer(plr: Player) {
    plr.newDialogue()
        .player(Expression.HAPPY, "Could I buy a beer please?")
        .npc(RUSTY_ANCHOR_BARTENDER, Expression.HAPPY, "Sure, that will be 2 gold coins please.")
        .then {
            if (!Bartender.canPay(it, BEER_PRICE)) {
                it.newDialogue().player(Expression.SAD, "I don't have enough coins.").open()
            } else {
                it.newDialogue()
                    .player(Expression.HAPPY, "Ok, here you go.")
                    .then { plr ->
                        if (Bartender.sell(plr, BEER, BEER_PRICE)) {
                            plr.newDialogue().text("You buy a pint of beer!").open()
                        }
                    }
                    .open()
            }
        }
        .open()
}

/**
 * The bartender passes on the goblins' quarrel to [plr].
 */
fun notBusy(plr: Player) {
    // TODO Goblin Diplomacy: this conversation starts the quest. Once the quest exists, start it here, and after that
    //  replace this option with "Have you heard any more rumours in here?" ("No, it hasn't been very busy lately.").
    plr.newDialogue()
        .player("Not very busy in here today, is it?")
        .npc(RUSTY_ANCHOR_BARTENDER, Expression.SHIFTY,
             "No, it was earlier. There was a guy in here saying the",
             "goblins up by the mountain are arguing again. Of all", "things, about the colour of their armour.")
        .npc(RUSTY_ANCHOR_BARTENDER, Expression.ANGRY,
             "Knowing the goblins, it could easily turn into a full",
             "blown war. Which wouldn't be good. Goblin wars make", "such a mess of the countryside.")
        .player("Well, if I have time, I'll see if I can go and knock some", "sense into them.")
        .open()
}
