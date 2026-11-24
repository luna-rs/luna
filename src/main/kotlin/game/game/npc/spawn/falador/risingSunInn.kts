package game.npc.spawn.falador

import api.predef.*
import game.npc.spawn.Bartender
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * The barmaids of the Rising Sun Inn, Falador: Emily and Kaylee.
 */
val RISING_SUN_BARMAIDS = listOf(736, 3217)

/**
 * A pint of Asgarnian ale.
 */
val ASGARNIAN_ALE = 1905

/**
 * A pint of Wizard's mind bomb.
 */
val WIZARDS_MIND_BOMB = 1907

/**
 * A pint of Dwarven stout.
 */
val DWARVEN_STOUT = 1913

for (barmaid in RISING_SUN_BARMAIDS) {
    npc1(barmaid) { whatAles(plr, barmaid) }
}

/**
 * [barmaid] tells [plr] which ales the inn serves.
 */
fun whatAles(plr: Player, barmaid: Int) {
    plr.newDialogue()
        .player(Expression.QUIZZICAL, "Hi, what ales are you serving?")
        .npc(barmaid, Expression.HAPPY,
             "Well, you can either have a nice Asgarnian Ale, a", "Wizard's Mind Bomb or a Dwarven Stout.")
        .options("One Asgarnian Ale, please.", {
            order(it, barmaid, "One Asgarnian Ale, please.", "That'll be two gold.", ASGARNIAN_ALE, 2,
                  "You buy an Asgarnian Ale.")
        }, "I'll try the Mind Bomb.", {
            order(it, barmaid, "I'll try the Mind Bomb.", "That'll be two gold.", WIZARDS_MIND_BOMB, 2,
                  "You buy a pint of Wizard's Mind Bomb.")
        }, "Can I have a Dwarven Stout?", {
            order(it, barmaid, "Can I have a Dwarven Stout?", "That'll be three gold.", DWARVEN_STOUT, 3,
                  "You buy a pint of Dwarven Stout.")
        }, "I don't feel like any of those.", {
            it.newDialogue().player("I don't feel like any of those.").open()
        })
        .open()
}

/**
 * [plr] orders [drink] from [barmaid] for [price] coins.
 */
fun order(plr: Player, barmaid: Int, ask: String, reply: String, drink: Int, price: Int, bought: String) {
    plr.newDialogue()
        .player(Expression.HAPPY, ask)
        .npc(barmaid, Expression.HAPPY, reply)
        .then { Bartender.serve(it, drink, price, "Oh dear, I don't seem to have enough money.", bought) }
        .open()
}
