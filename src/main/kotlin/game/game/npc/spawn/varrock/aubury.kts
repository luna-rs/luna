package game.npc.spawn.varrock

import api.predef.*
import api.shop.dsl.ShopHandler
import game.skill.runecrafting.essenceMine.EssenceMine
import io.luna.game.model.Position
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType

/**
 * The [Npc] id for Aubury.
 */
val AUBURY_ID = 553

/**
 * The name of the shop.
 */
val SHOP_NAME = "Aubury's Rune Shop."

/*
 * Registers Aubury's rune shop.
 */
ShopHandler.create(SHOP_NAME) {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.FAST
    currency = Currency.COINS

    sell {
        "Air rune" x 5000
        "Fire rune" x 5000
        "Water rune" x 5000
        "Earth rune" x 5000
        "Mind rune" x 5000
        "Body rune" x 5000
        "Chaos rune" x 250
        "Death rune" x 250
    }

    open {
        npc2 += AUBURY_ID
    }
}

/*
 * Handles Aubury's third npc option.
 *
 * This option is used as a direct Rune Essence mine teleport shortcut, bypassing the dialogue path and immediately
 * submitting or queueing the player into Aubury's tele-other action.
 */
npc3(id = AUBURY_ID, interaction = { _, _ -> InteractionPolicy(InteractionType.SIZE, Position.VIEWING_DISTANCE / 2) }) {
    EssenceMine.teleport(targetNpc, plr)
}

/*
 * Handles Aubury's first npc option dialogue.
 *
 * The player can either open the rune shop, decline the shop, or ask Aubury to teleport them to the Rune Essence mine.
 */
npc1(AUBURY_ID) {
    plr.newDialogue()
        .npc(targetNpc.id, "Do you want to buy some runes?")
        .options(
            "Yes please!",
            {
                plr.overlays.open(ShopInterface(world, SHOP_NAME))
            },
            "Oh, it's a rune shop. No thank you, then.",
            {
                plr.newDialogue()
                    .player("Oh, it's a rune shop. No thank you, then.")
                    .npc(
                        targetNpc.id,
                        "Well, if you find someone who does want",
                        "runes, please send them my way."
                    )
                    .open()
            },
            "Can you teleport me to the Rune Essence?",
            {
                plr.newDialogue()
                    .player("Can you teleport me to the Rune Essence?")
                    .npc(
                        targetNpc.id,
                        "Of course. By the way, if you end up making",
                        "any runes from the essence you mine, I'll",
                        "happily buy them from you."
                    ).then {
                        EssenceMine.teleport(targetNpc, plr)
                    }
                    .open()
            }
        ).open()
}
