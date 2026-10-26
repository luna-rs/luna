package game.npc.spawn.catherby

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * The [Npc] id for Arhein.
 */
val ARHEIN_ID = 563

/**
 * The name of the shop.
 */
val SHOP_NAME = "Arhein Store"

/*
 * Registers Arhein's general store in Catherby.
 */
ShopHandler.create(SHOP_NAME) {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.FAST
    currency = Currency.COINS

    sell {
        "Bucket" x 30
        "Bronze pickaxe" x 10
        "Bowl" x 10
        "Cake tin" x 10
        "Tinderbox" x 10
        "Chisel" x 10
        "Hammer" x 10
        "Rope" x 10
        "Pot" x 30
        "Knife" x 10
    }

    open {
        npc2 += ARHEIN_ID
    }
}

/**
 * Opens the dialogue where the player asks Arhein where he delivers to.
 */
fun deliverDialogue(plr: Player, npc: Npc) {
    plr.newDialogue()
        .player("Where do you deliver to?")
        .npc(npc.id, Expression.HAPPY, "Oh, various places up and down the coast.", "Mostly Karamja and Port Sarim.")
        .options(
            "I don't suppose I could get a lift anywhere?",
            {
                plr.newDialogue()
                    .player("I don't suppose I could get a lift anywhere?")
                    .npc(npc.id, Expression.HAPPY, "Sorry pal, but I'm afraid I'm not quite ready to sail yet.")
                    .npc(
                        npc.id,
                        Expression.HAPPY,
                        "I'm waiting on a big delivery of candles",
                        "which I need to deliver further along the coast."
                    )
                    .open()
            },
            "Well, good luck with your business.",
            {
                plr.newDialogue()
                    .player("Well, good luck with your business.")
                    .npc(npc.id, Expression.HAPPY, "Thanks buddy!")
                    .open()
            }
        ).open()
}

/**
 * Opens the dialogue where the player asks Arhein if he is rich.
 */
fun richDialogue(plr: Player, npc: Npc) {
    plr.newDialogue()
        .player("Are you rich then?")
        .npc(
            npc.id,
            Expression.HAPPY,
            "Business is going reasonably well...",
            "I wouldn't say I was the richest of merchants ever,",
            "but I'm doing fairly well all things considered."
        )
        .open()
}

/*
 * Handles Arhein's first npc option dialogue.
 *
 * The player can open the shop, decline it, or ask about Arhein's ship. Easy clue scroll progress for Arhein is
 * handled separately by the treasure trails plugin.
 */
npc1(ARHEIN_ID) {
    val npc = targetNpc
    plr.newDialogue()
        .npc(npc.id, Expression.HAPPY, "Hello! Would you like to trade?")
        .options(
            "Yes.",
            {
                plr.newDialogue()
                    .player("Sure.")
                    .then { it.overlays.open(ShopInterface(world, SHOP_NAME)) }
                    .open()
            },
            "No thank you.",
            {
                plr.newDialogue()
                    .player("No thank you.")
                    .open()
            },
            "Is that your ship?",
            {
                plr.newDialogue()
                    .player(Expression.QUIZZICAL, "Is that your ship?")
                    .npc(
                        npc.id,
                        Expression.HAPPY,
                        "Yes, I use it to make deliveries to my customers",
                        "up and down the coast. These crates here are all",
                        "ready for my next trip."
                    )
                    .options(
                        "Where do you deliver to?", { deliverDialogue(it, npc) },
                        "Are you rich then?", { richDialogue(it, npc) }
                    ).open()
            }
        ).open()
}
