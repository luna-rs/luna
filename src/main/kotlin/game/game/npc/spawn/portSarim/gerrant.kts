package game.npc.spawn.portSarim

import api.predef.*
import api.shop.dsl.ShopHandler
import game.content.heroesQuest.HeroesProofs
import game.content.heroesQuest.HeroesQuest
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.ShopInterface

val shopkeeperId = 558

ShopHandler.create("Gerrant's Fishy Business.") {
    buy = BuyPolicy.EXISTING
    restock = RestockPolicy.FAST
    currency = Currency.COINS

    sell {
        "Small fishing net" x 50
        "Fishing rod" x 50
        "Fly fishing rod" x 50
        "Harpoon" x 10
        "Lobster pot" x 50
        "Fishing bait" x 15_000
        "Feather" x 10_000
        "Raw shrimps" x 200
        "Raw sardine" x 200
        "Raw herring" x  200
        "Raw anchovies" x  200
        "Raw trout" x  200
        "Raw pike" x  200
        "Raw salmon" x  200
        "Raw tuna" x  200
        "Raw lobster" x  200
        "Raw swordfish" x  200
    }

    open {
        npc2 += shopkeeperId
    }
}

npc1(shopkeeperId, {
    plr.newDialogue()
        .npc(targetNpc.id, "Welcome! You can buy fishing equipment at my store.", "We'll also buy anything you catch off you.")
        .options(buildList {
            add(DialogueOption("Let's see what you've got then.") {
                plr.newDialogue()
                    .player("Let's see what you've got then.")
                    .then({it.overlays.open(ShopInterface(world, "Gerrant's Fishy Business."))})
                    .open()
            })
            add(DialogueOption("Sorry, I'm not interested.") {
                plr.newDialogue().player("Sorry, I'm not interested.").open()
            })
            if (HeroesQuest.isInProgress(plr)) {
                add(DialogueOption(HeroesProofs.ASK_ABOUT_EELS) { HeroesProofs.askAboutEels(it, targetNpc) })
            }
        })
        .open()
})
