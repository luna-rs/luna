package game.npc.spawn.varrock

import api.predef.*
import game.npc.spawn.Bartender
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * The bartender of the Jolly Boar Inn, north-east of Varrock.
 */
val JOLLY_BOAR_BARTENDER = 731

/**
 * The bartender of the Dancing Donkey, south-east Varrock.
 */
val DANCING_DONKEY_BARTENDER = 732

/**
 * The bartender of the Blue Moon Inn, south of the palace.
 */
val BLUE_MOON_BARTENDER = 733

/**
 * A pint of beer.
 */
val BEER = 1917

/**
 * What a pint of beer costs.
 */
val BEER_PRICE = 2

npc1(JOLLY_BOAR_BARTENDER) {
    plr.newDialogue()
        .npc(JOLLY_BOAR_BARTENDER, Expression.HAPPY, "Can I help you?")
        .options("I'll have a beer please.", {
            it.newDialogue()
                .player(Expression.HAPPY, "I'll have a pint of beer please.")
                .npc(JOLLY_BOAR_BARTENDER, Expression.HAPPY, "Ok, that'll be two coins please.")
                .then { plr -> buyBeer(plr, "Oh dear, I don't seem to have enough money.") }
                .open()
        }, "Any hints where I can go adventuring?", {
            it.newDialogue()
                .player(Expression.HAPPY, "Any hints on where I can go adventuring?")
                .npc(JOLLY_BOAR_BARTENDER, Expression.QUIZZICAL, "Ooh, now. Let me see...")
                .npc(JOLLY_BOAR_BARTENDER, Expression.QUIZZICAL,
                     "Well there is the Varrock sewers. There are tales of",
                     "untold horrors coming out at night and stealing babies", "from houses.")
                .player(Expression.HAPPY, "Sounds perfect! Where's the entrance?")
                .npc(JOLLY_BOAR_BARTENDER, Expression.QUIZZICAL, "It's just to the east of the palace.")
                .open()
        }, "Heard any good gossip?", {
            it.newDialogue()
                .player(Expression.HAPPY, "Heard any gossip?")
                .npc(JOLLY_BOAR_BARTENDER, Expression.CONFUSED,
                     "I'm not that well up on the gossip out here. I've heard",
                     "that the bartender in the Blue Moon Inn has gone a",
                     "little crazy, he keeps claiming he is part of something", "called an online game.")
                .npc(JOLLY_BOAR_BARTENDER, Expression.CONFUSED,
                     "What that means, I don't know. That's probably old", "news by now though.")
                .open()
        })
        .open()
}

npc1(DANCING_DONKEY_BARTENDER) {
    plr.newDialogue()
        .player(Expression.HAPPY, "Hello.")
        .npc(DANCING_DONKEY_BARTENDER, Expression.HAPPY,
             "Good day to you, brave adventurer. Can I get you a", "refreshing beer?")
        .options("Yes please!", { donkeyYes(it) },
                 "No thanks.", { donkeyNo(it) },
                 "How much?", {
                     it.newDialogue()
                         .player(Expression.QUIZZICAL, "How much?")
                         .npc(DANCING_DONKEY_BARTENDER, Expression.HAPPY,
                              "Two gold pieces a pint. So, what do you say?")
                         .options("Yes please!", { plr -> donkeyYes(plr) }, "No thanks.", { plr -> donkeyNo(plr) })
                         .open()
                 })
        .open()
}

npc1(BLUE_MOON_BARTENDER) {
    if (Bartender.holdsClueFor(plr, BLUE_MOON_BARTENDER)) return@npc1
    plr.newDialogue()
        .npc(BLUE_MOON_BARTENDER, Expression.QUIZZICAL, "What can I do yer for?")
        .options("A glass of your finest ale please.", {
            it.newDialogue()
                .player(Expression.HAPPY, "A glass of your finest ale please.")
                .npc(BLUE_MOON_BARTENDER, Expression.HAPPY, "No problemo. That'll be 2 coins.")
                .then { plr -> buyBeer(plr, "Oh dear. I don't seem to have enough money.") }
                .open()
        }, "Can you recommend where an adventurer might make his fortune?", {
            it.newDialogue()
                .player(Expression.QUIZZICAL, "Can you recommend where an adventurer might make", "his fortune?")
                .npc(BLUE_MOON_BARTENDER, Expression.SHIFTY,
                     "Ooh I don't know if I should be giving away",
                     "information, makes the computer game too easy.")
                .options("Oh ah well...", { plr ->
                    plr.newDialogue().player(Expression.CONFUSED, "Oh ah well...").open()
                }, "Computer game? What are you talking about?", { plr ->
                    plr.newDialogue()
                        .player(Expression.CONFUSED, "Computer game?", "What are you talking about?")
                        .npc(BLUE_MOON_BARTENDER, Expression.SHIFTY,
                             "This world around us...", "is a computer game... called RuneScape.")
                        .player(Expression.CONFUSED,
                                "Nope, still don't understand what you are talking about.", "What's a computer?")
                        .npc(BLUE_MOON_BARTENDER, Expression.SHIFTY,
                             "It's a sort of magic box thing,", "which can do all sorts of stuff.")
                        .player(Expression.ANGRY, "I give up.", "You're obviously completely mad!")
                        .open()
                }, "Just a small clue?", { plr ->
                    plr.newDialogue()
                        .player(Expression.QUIZZICAL, "Just a small clue?")
                        .npc(BLUE_MOON_BARTENDER, Expression.SHIFTY,
                             "Go and talk to the bartender at the Jolly Boar Inn,",
                             "he doesn't seem to mind giving away clues.")
                        .open()
                })
                .open()
        }, "Do you know where I can get some good equipment?", {
            it.newDialogue()
                .player(Expression.QUIZZICAL, "Do you know where I can get some good equipment?")
                .npc(BLUE_MOON_BARTENDER, "Well, there's the sword shop across the road,",
                     "or there's also all sorts of shops up around the market.")
                .open()
        })
        .open()
}

/**
 * Sells [plr] a pint of beer, or has them say [poorLine] if they can't afford it.
 */
fun buyBeer(plr: Player, poorLine: String) =
    Bartender.serve(plr, BEER, BEER_PRICE, poorLine, "You buy a pint of beer.")

/**
 * [plr] takes the Dancing Donkey's beer.
 */
fun donkeyYes(plr: Player) {
    plr.newDialogue()
        .player(Expression.HAPPY, "Yes please!")
        .npc(DANCING_DONKEY_BARTENDER, Expression.HAPPY, "Ok then, that's two gold coins please.")
        .then {
            when {
                !Bartender.canPay(it, BEER_PRICE) -> it.sendMessage("You don't have enough gold!")
                Bartender.sell(it, BEER, BEER_PRICE) -> {
                    it.newDialogue()
                        .npc(DANCING_DONKEY_BARTENDER, Expression.HAPPY, "Cheers!")
                        .player(Expression.HAPPY, "Cheers!")
                        .open()
                    it.sendMessage("You buy a pint of beer.")
                }
            }
        }
        .open()
}

/**
 * [plr] turns down the Dancing Donkey's beer.
 */
fun donkeyNo(plr: Player) {
    plr.newDialogue()
        .player("No thanks.")
        .npc(DANCING_DONKEY_BARTENDER, Expression.CONFUSED, "Let me know if you change your mind.")
        .open()
}
