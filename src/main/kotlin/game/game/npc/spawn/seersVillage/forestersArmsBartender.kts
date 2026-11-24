package game.npc.spawn.seersVillage

import api.predef.*
import game.npc.spawn.Bartender
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * The bartender of the Foresters Arms, Seers' Village.
 */
val FORESTERS_ARMS_BARTENDER = 737

/**
 * A pint of beer.
 */
val BEER = 1917

/**
 * A bowl of the inn's home made stew.
 */
val STEW = 2003

/**
 * One of the inn's meat pies.
 */
val MEAT_PIE = 2327

npc1(FORESTERS_ARMS_BARTENDER) {
    plr.newDialogue()
        .npc(FORESTERS_ARMS_BARTENDER, Expression.HAPPY, "Good morning, what would you like?")
        .options("What do you have?", { whatDoYouHave(it) },
                 "Beer please.", { beer(it) },
                 "I don't really want anything thanks.", { noThanks(it) })
        .open()
}

/**
 * The bartender tells [plr] what the inn serves.
 */
fun whatDoYouHave(plr: Player) {
    plr.newDialogue()
        .player(Expression.QUIZZICAL, "What do you have?")
        .npc(FORESTERS_ARMS_BARTENDER, Expression.HAPPY,
             "Well we have beer, or if you want some food, we have", "our home made stew and meat pies.")
        .options("Beer please.", { beer(it) },
                 "I'll try the meat pie.", {
                     it.newDialogue()
                         .player(Expression.HAPPY, "I'll try the meat pie.")
                         .npc(FORESTERS_ARMS_BARTENDER, Expression.HAPPY, "Ok, that'll be 16 coins.")
                         .then { plr -> serve(plr, MEAT_PIE, 16) }
                         .open()
                 },
                 "Could I have some stew please?", {
                     it.newDialogue()
                         .player(Expression.QUIZZICAL, "Could I have some stew please?")
                         .npc(FORESTERS_ARMS_BARTENDER, Expression.HAPPY, "A bowl of stew, that'll be 20 coins please.")
                         .then { plr -> serve(plr, STEW, 20) }
                         .open()
                 },
                 "I don't really want anything thanks.", { noThanks(it) })
        .open()
}

/**
 * [plr] orders a beer.
 */
fun beer(plr: Player) {
    plr.newDialogue()
        .player(Expression.HAPPY, "Beer please.")
        .npc(FORESTERS_ARMS_BARTENDER, Expression.HAPPY, "One beer coming up. Ok, that'll be two coins.")
        .then { serve(it, BEER, 2) }
        .open()
}

/**
 * [plr] turns the bartender down.
 */
fun noThanks(plr: Player) {
    plr.newDialogue().player("I don't really want anything thanks.").open()
}

/**
 * The bartender hands [plr] one [id] for [price] coins, if they have room and can pay.
 */
fun serve(plr: Player, id: Int, price: Int) {
    when {
        plr.inventory.isFull -> plr.newDialogue()
            .player(Expression.SAD, "Oh dear. I don't seem to have enough space to carry", "it.")
            .open()
        !Bartender.canPay(plr, price) ->
            plr.newDialogue().player(Expression.SAD, "Oh dear. I don't seem to have enough money.").open()
        else -> Bartender.sell(plr, id, price)
    }
}
