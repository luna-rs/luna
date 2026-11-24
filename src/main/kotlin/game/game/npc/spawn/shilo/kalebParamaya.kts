package game.npc.spawn.shilo

import api.predef.*
import game.npc.spawn.Bartender
import io.luna.game.model.mob.Player

/**
 * Kaleb Paramaya, who runs the Paramaya hostel in Shilo Village.
 */
val KALEB_PARAMAYA = 512

/**
 * A jug of wine.
 */
val JUG_OF_WINE = 1993

/**
 * A pint of beer.
 */
val BEER = 1917

npc1(KALEB_PARAMAYA) {
    // TODO Kaleb also sells dormitory tickets ("Buy a nights rest: 35 Gold", "Buy a pack of 5 Dorm tickets: 175
    //  Gold", ticket 619). Add them once the hostel ladder (2268) takes a ticket before letting players up.
    plr.newDialogue()
        .player("Hello.")
        .npc(KALEB_PARAMAYA, "Hello Bwana, What can I do for you today?")
        .options("Can you tell me a bit about this this place?", {
            it.newDialogue()
                .player("Can you tell me a bit about this this place?")
                .npc(KALEB_PARAMAYA, "Of course Bwana, you look like a traveller!")
                .player("Yes I am actually!")
                .npc(KALEB_PARAMAYA, "Well, I am a traveller myself, and I have set up this",
                     "hostel for adventurers and travellers who are weary", "from their journey.")
                .npc(KALEB_PARAMAYA, "There is a dormitory upstairs if you are tired, it costs",
                     "35 gold pieces which covers the cost of laundry and", "cleaning.")
                .open()
        }, "Buy some wine: 1 Gold.", {
            buy(it, JUG_OF_WINE, 1, "You purchase a jug of wine.")
        }, "Buy some Beer: 2 Gold.", {
            buy(it, BEER, 2, "You purchase a frothy glass of beer.")
        })
        .open()
}

/**
 * Kaleb sells [plr] one [id] for [price] coins.
 */
fun buy(plr: Player, id: Int, price: Int, bought: String) {
    plr.newDialogue()
        .npc(KALEB_PARAMAYA, "Very good ${if (plr.appearance.isFemale) "madam" else "sir"}!")
        .then {
            when {
                !Bartender.canPay(it, price) ->
                    it.newDialogue().npc(KALEB_PARAMAYA, "Sorry Bwana, you don't have enough money.").open()
                Bartender.sell(it, id, price) -> it.sendMessage(bought)
            }
        }
        .open()
}
