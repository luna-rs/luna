package game.npc.spawn.lumbridge

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption
import io.luna.game.model.mob.dialogue.Expression

/**
 * The candle seller outside the Lumbridge Swamp Caves.
 */
val CANDLE_SELLER = 1834

/**
 * The lit candle he sells.
 */
val LIT_CANDLE = 33

/**
 * What he charges for it.
 */
val CANDLE_PRICE = 1000

/**
 * Whether the candle seller has told this player how lanterns are made.
 */
var Player.knowsHowToMakeLanterns by Attr.boolean().persist("knows_how_to_make_lanterns")

/**
 * Asks [plr] whether they want a lit candle, with an option about lanterns once he has explained them.
 */
fun offer(plr: Player) {
    val options = arrayListOf(
        DialogueOption("Yes please.") { yesPlease(it) },
        DialogueOption("One thousand gold?!") { haggle(it) },
        DialogueOption("No thanks, I'd rather curse the darkness.") { curse(it) }
    )
    if (plr.knowsHowToMakeLanterns) {
        options += DialogueOption("How do you make lanterns?") { lanterns(it) }
    }
    plr.newDialogue()
        .npc(CANDLE_SELLER, "Do you want a lit candle for 1000 gold?")
        .options(options)
        .open()
}

/**
 * [plr] agrees to buy a candle.
 */
fun yesPlease(plr: Player) {
    plr.newDialogue().player("Yes please.").then { buy(it) }.open()
}

/**
 * Sells [plr] a lit candle if they can pay.
 */
fun buy(plr: Player) {
    val coins = Item(995, CANDLE_PRICE)
    if (!plr.inventory.contains(coins)) {
        plr.newDialogue()
            .player(Expression.SAD, "But I don't have that kind of money on me.")
            .npc(CANDLE_SELLER, "Well then, no candle for you!")
            .open()
        return
    }
    if (plr.inventory.computeAmountForId(995) > CANDLE_PRICE && plr.inventory.isFull) {
        plr.sendMessage("You don't have enough inventory space.")
        return
    }
    plr.inventory.remove(coins)
    plr.inventory.add(Item(LIT_CANDLE))
    plr.newDialogue()
        .npc(CANDLE_SELLER, "Here you go then.")
        .npc(CANDLE_SELLER, Expression.SHIFTY, "I should warn you, though, it can be dangerous to take a naked flame " +
            "down there. You'd be better off making a lantern.")
        .options("What's so dangerous about a naked flame?", {
            it.newDialogue()
                .player("What's so dangerous about a naked flame?")
                .npc(CANDLE_SELLER, Expression.LAUGHING, "Heh heh... You'll find out.")
                .open()
        }, "How do you make lanterns?", { lanterns(it) }, "Thanks, bye.", {
            it.newDialogue().player("Thanks, bye.").open()
        })
        .open()
}

/**
 * [plr] balks at the price.
 */
fun haggle(plr: Player) {
    plr.newDialogue()
        .player(Expression.SHOCKED, "One thousand gold?!")
        .npc(CANDLE_SELLER, "Look, you're not going to be able to survive down that hole without a light source.")
        .npc(CANDLE_SELLER, "So you could go off to the candle shop to buy one more cheaply. You could even make your " +
            "own lantern, which is a lot better.")
        .npc(CANDLE_SELLER, Expression.LAUGHING, "But I bet you want to find out what's down there right now, don't " +
            "you? And you can pay me 1000 gold for the privilege!")
        .options("All right, you win, I'll buy a candle.", { giveIn(it) }, "No way.", {
            it.newDialogue().player(Expression.ANGRY, "No way.").open()
        }, "How do you make lanterns?", { lanterns(it) })
        .open()
}

/**
 * [plr] gives in and buys a candle after all.
 */
fun giveIn(plr: Player) {
    plr.newDialogue().player(Expression.SAD, "All right, you win, I'll buy a candle.").then { buy(it) }.open()
}

/**
 * [plr] turns the candle down.
 */
fun curse(plr: Player) {
    plr.newDialogue().player("No thanks, I'd rather curse the darkness.").open()
}

/**
 * The candle seller explains how lanterns are made, which he remembers from then on.
 */
fun lanterns(plr: Player) {
    plr.knowsHowToMakeLanterns = true
    plr.newDialogue()
        .player("How do you make lanterns?")
        .npc(CANDLE_SELLER, "Out of glass. The more advanced lanterns have a metal component as well.")
        .npc(CANDLE_SELLER, "Firstly you can make a simple candle lantern out of glass. It's just like a candle, but " +
            "the flame isn't exposed, so it's safer.")
        .npc(CANDLE_SELLER, "Then you can make an oil lamp, which is brighter but has an exposed flame. But if you " +
            "make an iron frame for it you can turn it into an oil lantern.")
        .npc(CANDLE_SELLER, "Finally there's the bullseye lantern. You'll need to make a frame out of steel and add a " +
            "glass lens.")
        .npc(CANDLE_SELLER, "Once you've made your lamp or lantern, you'll need to make lamp oil for it. The chemist " +
            "near Rimmington has a machine for that.")
        .npc(CANDLE_SELLER, "For any light source, you'll need a tinderbox to light it. Keep your tinderbox handy in " +
            "case it goes out!")
        .npc(CANDLE_SELLER, Expression.HAPPY, "But if all that's too complicated, you can buy a candle right here for " +
            "1000 gold!")
        .options("All right, you win, I'll buy a candle.", { giveIn(it) },
                 "No thanks, I'd rather curse the darkness.", { curse(it) })
        .open()
}

npc1(CANDLE_SELLER) { offer(plr) }
