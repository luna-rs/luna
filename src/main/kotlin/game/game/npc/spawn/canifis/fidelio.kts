package game.npc.spawn.canifis

import api.predef.*
import api.shop.dsl.ShopHandler
import game.npc.spawn.GeneralStore
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.mob.dialogue.Expression

/**
 * The general store in Canifis, run by Fidelio.
 */
ShopHandler.create("General Store") {
    buy = BuyPolicy.ALL
    restock = RestockPolicy.BALANCED
    currency = Currency.COINS
    merchantsAccessOnly()

    sell {
        "Needle" x 2 every 100
        "Thread" x 50 every 100
        "Pot" x 3 every 100
        "Bucket" x 2 every 100
        "Jug" x 2 every 100
        "Tinderbox" x 2 every 100
        "Chisel" x 2 every 100
        "Hammer" x 2 every 100
        "Sample bottle" x 100
        "Knife" x 100
    }

    open {
        npc2 += 1040
    }
}

npc1(1040) {
    plr.newDialogue()
        .player(Expression.HAPPY, "Hello there.")
        .npc(1040,
             "H-hello. You l-look like a s-stranger to these p-parts.",
             "Would you l-like to buy something? I h-have some s-",
             "special offers at the m-minute...some s-sample bottles for",
             "s-storing s-snail slime.")
        .options("Yes, please.", {
            it.newDialogue()
                .player("Yes, please.")
                .then { GeneralStore.open(it, "General Store") }
                .open()
        }, "No thanks.", {
            it.newDialogue()
                .player("No thanks.")
                .npc(1040, Expression.SAD,
                     "(sigh) Th-that's okay. Nobody ever w-wants to buy my",
                     "wares. Oh, s-sure, if it was food or c-clothes they would",
                     "though!")
                .open()
        }, "Why are your prices so high?", {
            it.newDialogue()
                .player(Expression.ANGRY, "Why are your prices so high?")
                .npc(1040, Expression.SAD,
                     "As you p-probably know, the h-humans hate our kind",
                     "and k-keep us trapped here. To get my s-stocks I have",
                     "to sneak into the human lands in s-secret!")
                .npc(1040, Expression.SAD,
                     "All the s-secrecy and d-danger has played h-havoc with",
                     "my nerves!")
                .player(Expression.ANGRY, "But all your stuff is overpriced junk!")
                .npc(1040, Expression.ANGRY,
                     "N-not at all! They are v-valuable human artefacts",
                     "smuggled here at g-great personal risk!")
                .open()
        })
        .open()
}
