package game.npc.spawn.falador

import api.predef.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * The [Npc] id for Wyson the gardener.
 */
val WYSON_ID = 36

/**
 * The coins item ID.
 */
val COINS = 995

/**
 * The woad leaf item ID.
 */
val WOAD_LEAF = 1793

/**
 * Opens the dialogue where Wyson turns down an offer that is too low.
 */
fun lowOffer(plr: Player, npc: Npc, offer: String) {
    plr.newDialogue()
        .player(offer)
        .npc(
            npc.id,
            Expression.ANGRY,
            "No no, that's far too little.",
            "Woad leaves are hard to get.",
            "I used to have plenty but someone",
            "kept stealing them off me!"
        )
        .open()
}

/**
 * Sells [plr] woad leaves for [price] coins, if they can afford it.
 */
fun buyLeaves(plr: Player, npc: Npc, price: Int, leaves: Int) {
    if (plr.inventory.computeAmountForId(COINS) < price) {
        plr.newDialogue()
            .player(Expression.SAD, "I don't have enough coins to buy the leaves.", "I'll come back later.")
            .open()
    } else if (plr.inventory.remove(Item(COINS, price))) {
        plr.giveItem(Item(WOAD_LEAF, leaves))
        plr.sendMessage("You give Wyson $price coins.")
        plr.sendMessage(
            if (leaves == 1) "Wyson the gardener gives you a woad leaf."
            else "Wyson the gardener gives you some woad leaves."
        )
    }
}

/**
 * Opens the dialogue where the player haggles with Wyson over the price of woad leaves.
 */
fun woadDialogue(plr: Player, npc: Npc) {
    plr.newDialogue()
        .player("I'm looking for woad leaves.")
        .npc(npc.id, Expression.SHIFTY, "How much are you willing to pay?")
        .options(
            "How about 5 coins?", { lowOffer(it, npc, "How about 5 coins?") },
            "How about 10 coins?", { lowOffer(it, npc, "How about 10 coins?") },
            "How about 15 coins?",
            {
                plr.newDialogue()
                    .player("How about 15 coins?")
                    .npc(npc.id, "Mmmm... okay, sounds fair.")
                    .then { buyLeaves(it, npc, 15, 1) }
                    .open()
            },
            "How about 20 coins?",
            {
                plr.newDialogue()
                    .player("How about 20 coins?")
                    .npc(npc.id, Expression.HAPPY, "Okay, that's more than fair.")
                    .then {
                        if (it.inventory.computeAmountForId(COINS) < 20) {
                            buyLeaves(it, npc, 20, 2)
                        } else {
                            it.newDialogue()
                                .npc(npc.id, Expression.HAPPY, "Here, have two, you're a generous person.")
                                .then { buyLeaves(it, npc, 20, 2) }
                                .open()
                        }
                    }
                    .open()
            }
        ).open()
}

/*
 * Handles Wyson the gardener's first npc option dialogue.
 *
 * The player can ask Wyson for woad leaves, which he sells for 15 or 20 coins, or decline.
 */
npc1(WYSON_ID) {
    val npc = targetNpc
    plr.newDialogue()
        .npc(npc.id, "I'm the gardener around here.", "Do you have any gardening that needs doing?")
        .options(
            "I'm looking for woad leaves.", { woadDialogue(it, npc) },
            "Not right now, thanks.",
            {
                plr.newDialogue()
                    .player("Not right now, thanks.")
                    .open()
            }
        ).open()
}
