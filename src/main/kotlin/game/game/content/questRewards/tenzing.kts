package game.content.questRewards

import api.predef.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Tenzing the Sherpa, in his hut west of Burthorpe.
 */
val TENZING = 1071

/**
 * The boots that Tenzing sells.
 */
val CLIMBING_BOOTS = 3105

/**
 * What Tenzing charges for a pair of climbing boots.
 */
val BOOTS_PRICE = 12

npc1(TENZING) {
    // TODO Death Plateau: before the quest is done he talks about the trolls and the secret way instead. Troll
    //  Romance: while it is started "Do you know where I can find Trollweiss?" replaces "Nice place you have here.".
    //  Until both quests exist every player has finished Death Plateau and not started Troll Romance.
    ask(plr)
}

/**
 * Offers [plr] Tenzing's topics, and asks again after each answer until they say goodbye.
 */
fun ask(plr: Player) {
    plr.newDialogue()
        .options("Can I buy some Climbing boots?", { buyBoots(it) },
                 "What does a Sherpa do?", {
                     it.newDialogue()
                         .player("What does a Sherpa do?")
                         .npc(TENZING, "We are expert guides that take adventurers, such as",
                              "yourself, on mountaineering expeditions.")
                         .then { plr -> askAgain(plr) }
                         .open()
                 },
                 "How did you find out about the secret way?", {
                     it.newDialogue()
                         .player("How did you find out about the secret way?")
                         .npc(TENZING, "I used to take adventurers up Death Plateau and",
                              "further north before the trolls came. I know these", "mountains well.")
                         .then { plr -> askAgain(plr) }
                         .open()
                 },
                 "Nice place you have here.", {
                     it.newDialogue()
                         .player("Nice place you have here.")
                         .npc(TENZING, "Thanks, I built it myself! I'm usually self sufficient but",
                              "I can't earn any money with the trolls camped on", "Death Plateau.")
                         .player("Now that the Imperial Guard know about the secret",
                                 "way they will be able to destroy the trolls!")
                         .npc(TENZING, "Let us hope so traveller!")
                         .then { plr -> askAgain(plr) }
                         .open()
                 },
                 "Nothing, thanks!", { it.newDialogue().player("Nothing, thanks!").open() })
        .open()
}

/**
 * Tenzing asks [plr] whether there is anything else.
 */
fun askAgain(plr: Player) {
    plr.newDialogue().npc(TENZING, "Was there anything else?").then { ask(it) }.open()
}

/**
 * Tenzing sells [plr] a pair of climbing boots.
 */
fun buyBoots(plr: Player) {
    plr.newDialogue()
        .player("Can I buy some Climbing boots?")
        .npc(TENZING, "Sure, I'll sell you some in your size for 12 gold.")
        .options("OK, sounds good.", {
            it.newDialogue().player("OK, sounds good.").then { plr ->
                when {
                    !QuestRewards.canPay(plr, BOOTS_PRICE) ->
                        plr.newDialogue().player("I don't have the money on me.").then { askAgain(it) }.open()
                    QuestRewards.give(plr, CLIMBING_BOOTS, BOOTS_PRICE) -> {
                        plr.sendMessage("Tenzing has given you some Climbing boots.")
                        plr.newDialogue()
                            .give(Item(CLIMBING_BOOTS), "Tenzing has given you some Climbing boots.", false)
                            .then { askAgain(it) }
                            .open()
                    }
                }
            }.open()
        }, "No, thanks.", {
            it.newDialogue().player("No, thanks.").then { plr -> askAgain(plr) }.open()
        })
        .open()
}
