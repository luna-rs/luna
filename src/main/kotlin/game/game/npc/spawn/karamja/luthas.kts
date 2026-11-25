package game.npc.spawn.karamja

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import game.player.Sound
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder
import io.luna.game.model.mob.dialogue.Expression

/**
 * Luthas, who runs the banana plantation on Musa Point.
 */
val LUTHAS = 379

/**
 * The export crate Luthas pays players to fill.
 */
val BANANA_CRATE = 2072

val BANANA = 1963
val KARAMJA_RUM = 431

/**
 * How many bananas fill the crate, and what Luthas pays for it.
 */
val CRATE_SIZE = 10
val PAYMENT = 30

/**
 * Whether Luthas has given the player the job of filling the crate.
 */
var Player.bananaCrateJob by Attr.boolean().persist("banana_crate_job")

/**
 * How many bananas the player has packed into the crate.
 */
var Player.bananaCrateBananas by Attr.int().persist("banana_crate_bananas")

// Saved attributes are only kept if they're read during the session (luna-rs/luna#610).
on(LoginEvent::class) {
    plr.bananaCrateJob
    plr.bananaCrateBananas
}

fun employment(plr: Player) {
    plr.bananaCrateJob = true
    plr.newDialogue()
        .player(Expression.QUIZZICAL, "Could you offer me employment on your plantation?")
        .npc(LUTHAS, Expression.HAPPY, "Yes, I can sort something out. There's a crate ready to be loaded onto the ship.")
        .npc(LUTHAS, Expression.HAPPY, "If you could fill it up with bananas, I'll pay you 30 gold.")
        .open()
}

fun customs(plr: Player) {
    plr.newDialogue()
        .player(Expression.SHIFTY, "That customs officer is annoying isn't she?")
        .npc(LUTHAS, "Well I know her pretty well. She doesn't cause me any trouble any more.")
        .npc(LUTHAS, "She doesn't even search my export crates any more. She knows they only contain bananas.")
        .open()
}

fun whereDelivered(plr: Player) {
    plr.newDialogue()
        .player(Expression.QUIZZICAL, "So where are these bananas going to be delivered to?")
        .npc(LUTHAS, "I sell them to Wydin who runs a grocery store in Port Sarim.")
        .open()
}

/**
 * Adds Luthas's four-way question about the delivery, the customs officer and [first] and [second].
 */
fun DialogueQueueBuilder.fourWay(first: String, firstAction: (Player) -> Unit,
                                 second: String, secondAction: (Player) -> Unit): DialogueQueueBuilder =
    options(first, firstAction, second, secondAction,
            "So where are these bananas going to be delivered to?", ::whereDelivered,
            "That customs officer is annoying isn't she?", ::customs)

npc1(LUTHAS) {
    when {
        !plr.bananaCrateJob -> plr.newDialogue()
            .npc(LUTHAS, Expression.HAPPY, "Hello I'm Luthas, I run the banana plantation here.")
            .options("Could you offer me employment on your plantation?", ::employment,
                     "That customs officer is annoying isn't she?", ::customs)
            .open()

        plr.bananaCrateBananas >= CRATE_SIZE -> plr.newDialogue()
            .player(Expression.HAPPY, "I've filled a crate with bananas.")
            .npc(LUTHAS, Expression.HAPPY, "Well done, here's your payment.")
            .then {
                it.bananaCrateJob = false
                it.bananaCrateBananas = 0
                it.giveItem(Item(995, PAYMENT))
                it.sendMessage("Luthas hands you $PAYMENT coins.")
                it.newDialogue().fourWay("Will you pay me for another crate full?", { p ->
                    p.bananaCrateJob = true
                    p.newDialogue()
                        .player(Expression.QUIZZICAL, "Will you pay me for another crate full?")
                        .npc(LUTHAS, Expression.HAPPY, "Yes certainly.")
                        .npc(LUTHAS, "If you go outside you should see the old crate has been loaded on to the ship, " +
                            "and there is another empty crate in its place.")
                        .open()
                }, "Thank you, I'll be on my way", { p ->
                    p.newDialogue().player(Expression.HAPPY, "Thank you, I'll be on my way.").open()
                }).open()
            }
            .open()

        else -> plr.newDialogue()
            .npc(LUTHAS, Expression.QUIZZICAL, "Have you completed your task yet?")
            .fourWay("What did I have to do again?", { p ->
                p.newDialogue()
                    .player(Expression.QUIZZICAL, "What did I have to do again?")
                    .npc(LUTHAS, Expression.HAPPY, "There's a crate ready to be loaded onto the ship. If you could fill " +
                        "it up with bananas, I'll pay you 30 gold.")
                    .open()
            }, "No, the crate isn't full yet.", { p ->
                p.newDialogue()
                    .player(Expression.SAD, "No, the crate isn't full yet...")
                    .npc(LUTHAS, Expression.ANGRY, "Well come back when it is.")
                    .open()
            })
            .open()
    }
}

object1(BANANA_CRATE) {
    val bananas = plr.bananaCrateBananas
    when (bananas) {
        0 -> plr.sendMessage("The crate is completely empty.")
        CRATE_SIZE -> plr.sendMessage("The crate is full of bananas.")
        else -> plr.sendMessage("The crate has $bananas ${if (bananas == 1) "banana" else "bananas"} inside.")
    }
}

useItem(BANANA).onObject(BANANA_CRATE) {
    when {
        !plr.bananaCrateJob -> plr.sendMessage("Why would I want to do that?")
        plr.bananaCrateBananas >= CRATE_SIZE -> plr.sendMessage("The crate is already full.")
        plr.inventory.remove(Item(BANANA)) -> {
            plr.bananaCrateBananas++
            plr.playSound(Sound.PUT_DOWN)
            plr.animation(Animation(832))
            plr.newDialogue().text("You pack a banana into the crate.").open()
        }
    }
}

// TODO Pirate's Treasure: during the quest the player stashes a bottle of Karamja rum under the bananas.
useItem(KARAMJA_RUM).onObject(BANANA_CRATE) {
    if (!plr.bananaCrateJob) {
        plr.sendMessage("Why would I want to do that?")
    } else {
        plr.sendMessage("I see no reason to do that.")
    }
}
