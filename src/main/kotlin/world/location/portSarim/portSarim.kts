package world.location.portSarim

import api.predef.*
import api.predef.ext.*
import api.shop.dsl.ShopHandler
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag
import io.luna.game.model.mob.dialogue.Expression
import world.player.advanceLevel.LevelUpInterface

ShopHandler.create("Gerrant's Fishy Business.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.DEFAULT
    currency = Currency.COINS

    sell {
        "Bronze battleaxe" x 4
        "Iron battleaxe" x 3
        "Steel battleaxe" x 2
        "Black battleaxe" x 1
        "Mithril battleaxe" x 1
        "Adamant battleaxe" x 1
    }

    open {
        npc2 += 559
    }
}

ShopHandler.create("Gerrant's Fishy Business.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.FAST
    currency = Currency.COINS

    sell {
        "Small fishing net" x 5
        "Fishing rod" x 5
        "Fly fishing rod" x 5
        "Harpoon" x 1
        "Lobster pot" x 5
        "Fishing bait" x 1500
        "Feather" x 1000
        "Raw shrimps" x 0
        "Raw sardine" x 0
        "Raw herring" x 0
        "Raw anchovies" x 0
        "Raw trout" x 0
        "Raw pike" x 0
        "Raw salmon" x 0
        "Raw tuna" x 0
        "Raw lobster" x 0
        "Raw swordfish" x 0
    }

    open {
        npc2 += 558
    }
}

/**
 * Brian dialogue
 */
npc1(559, {
    plr.newDialogue()
        .options(
            "So, are you selling something?", {
                plr.newDialogue()
                    .player("So, are you selling something?")
                    .npc(559, "Yep, take a lok at these great axes!")
                    .then({plr.interfaces.openShop("Brian's Battleaxe Bazaar.")})
                    .open()
            },
            "'Ello.", {
                plr.newDialogue()
                    .player("'Ello.")
                    .npc(559, "'Ello!")
                    .open()
            })
        .open()
})

/**
 * Gerrant dialogue
 */
npc1(558, {
    plr.newDialogue()
        .npc(558, "Wecome! You can buy fishing equipment at my store.", "We'll also buy anything you catch off you.")
        .options(
            "Let's see what you've got then.", {
                plr.newDialogue().player("Let's see what you've got then.").then({plr.interfaces.openShop("Gerrant's Fishy Business.")}).open()
            },
            "Sorry, I'm not interested.", {
                plr.newDialogue().player("Sorry, I'm not interested.").open()
            })
        .open()
})

on(ServerLaunchEvent::class) {
    world.addNpc(559, 3029, 3249)   // Brian
    world.addNpc(558, 3015, 3225)   // Gerrant
}