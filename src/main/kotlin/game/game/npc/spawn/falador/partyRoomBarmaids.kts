package game.npc.spawn.falador

import api.predef.*
import game.npc.spawn.Bartender
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.dialogue.Expression

/**
 * Megan, the Party Room barmaid.
 */
val MEGAN = 661

/**
 * Lucy, the Party Room's second barmaid.
 */
val LUCY = 662

/**
 * A pint of beer.
 */
val BEER = 1917

/**
 * What a pint of beer costs.
 */
val BEER_PRICE = 2

/**
 * The dance Megan and the player share.
 */
val DANCE = Animation(784)

npc1(MEGAN) {
    val megan = targetNpc
    plr.newDialogue()
        .npc(MEGAN, Expression.HAPPY, "Hi! I'm Megan. Welcome to the Party Room!")
        .options("One beer please Megan!", {
            it.newDialogue()
                .player(Expression.HAPPY, "One beer please Megan!")
                .then { plr -> pourBeer(plr, MEGAN, "Megan has given you a beer.") }
                .open()
        }, "Can you dance, Megan?", {
            it.newDialogue()
                .player(Expression.CONFUSED, "Can you dance, Megan?")
                .npc(MEGAN, Expression.HAPPY, "Can I dance?!")
                .npc(MEGAN, Expression.LAUGHING, "CAN I dance?!!")
                .player(Expression.HAPPY, "Dance with me Megan!")
                .then { plr -> dance(plr, megan) }
                .open()
        }, "Do you have any news?", {
            it.newDialogue()
                .player("Do you have any news?")
                .npc(MEGAN, "Not at the moment. I've heard that the known world is",
                     "expanding as new places are discovered.")
                .npc(MEGAN, Expression.HAPPY, "These are exciting times indeed!")
                .open()
        })
        .open()
}

npc1(LUCY) {
    plr.newDialogue()
        .npc(LUCY, Expression.HAPPY, "Hi! I'm Lucy. Welcome to the Party Room!")
        .player(Expression.HAPPY, "One beer please Lucy!")
        .then { pourBeer(it, LUCY, null) }
        .open()
}

/**
 * [barmaid] pours [plr] a beer, showing [given] with it when she has something to say.
 */
fun pourBeer(plr: Player, barmaid: Int, given: String?) {
    val title = if (plr.appearance.isFemale) "ma'am" else "sir"
    plr.newDialogue()
        .npc(barmaid, Expression.HAPPY, "Coming right up $title! That's two gold please.")
        .then {
            when {
                !Bartender.canPay(it, BEER_PRICE) ->
                    it.newDialogue().player(Expression.SAD, "I'm sorry but I don't have enough money!").open()
                Bartender.sell(it, BEER, BEER_PRICE) && given != null ->
                    it.newDialogue().give(Item(BEER), given, false).open()
            }
        }
        .open()
}

/**
 * [plr] and [megan] dance together.
 */
fun dance(plr: Player, megan: Npc) {
    plr.overlays.closeWindows()
    plr.animation(DANCE)
    megan.animation(DANCE)
}
