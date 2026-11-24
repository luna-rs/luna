package game.npc.spawn.edgeville

import api.predef.*
import game.content.treasureTrails.TrailClues
import game.player.Sound
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.dialogue.Expression

/**
 * The monks of the monastery south of Edgeville.
 */
val MONK = 803

/**
 * Abbot Langley, head of the monastery.
 */
val ABBOT_LANGLEY = 801

/**
 * The spell a monk casts to heal a player.
 */
val CAST_HEAL = Animation(709)

/**
 * The light that settles on a player a monk heals.
 */
val HEAL_LIGHT = Graphic(84, 120)

npc1(MONK) {
    val monk = targetNpc
    plr.newDialogue()
        .npc(MONK, "Greetings traveller.")
        .options("Can you heal me? I'm injured.", {
            it.newDialogue()
                .player(Expression.QUIZZICAL, "Can you heal me? I'm injured.")
                .npc(MONK, "Ok.")
                .then { plr -> monkHeals(plr, monk) }
                .open()
        }, "Isn't this place built a bit out the way?", {
            outOfTheWay(it, MONK)
        }, "How do I get further into the monastery?", {
            it.newDialogue()
                .player(Expression.QUIZZICAL, "How do I get further into the monastery?")
                .npc(MONK, "You'll need to talk to Abbot Langley about that. He's",
                     "usually to be found walking the halls of the monastery.")
                .open()
        })
        .open()
}

npc1(ABBOT_LANGLEY) {
    if (TrailClues.all.any { it.npcId == ABBOT_LANGLEY && plr.inventory.contains(it.id) }) return@npc1
    // TODO Prayer Guild: players who haven't joined the order also see "How do I get further into the monastery?",
    //  where 31 Prayer lets them join. Add it once the monastery ladder (2641) only lets members up.
    plr.newDialogue()
        .npc(ABBOT_LANGLEY, Expression.QUIZZICAL, "Greetings traveller.")
        .options("Can you heal me? I'm injured.", {
            it.newDialogue()
                .player(Expression.HAPPY, "Can you heal me? I'm injured.")
                .npc(ABBOT_LANGLEY, Expression.QUIZZICAL, "Ok.")
                .then { plr ->
                    heal(plr, 4, 12)
                    plr.newDialogue()
                        .text("Abbot Langley places his hands on your head.", "You feel a little better.")
                        .open()
                }
                .open()
        }, "Isn't this place built a bit out the way?", {
            outOfTheWay(it, ABBOT_LANGLEY)
        })
        .open()
}

/**
 * [monk] casts a healing spell on [plr].
 */
fun monkHeals(plr: Player, monk: Npc) {
    plr.overlays.closeWindows()
    plr.playSound(Sound.HEAL)
    plr.graphic(HEAL_LIGHT)
    monk.animation(CAST_HEAL)
    heal(plr, 2, 20)
    plr.sendMessage("You feel a little better.")
}

/**
 * Heals [plr] by [amount] hitpoints plus [percent]% of their hitpoints level, up to that level.
 */
fun heal(plr: Player, amount: Int, percent: Int) {
    plr.hitpoints.adjustLevel(amount + plr.hitpoints.staticLevel * percent / 100)
}

/**
 * [npc] tells [plr] why the monastery is so far from anywhere.
 */
fun outOfTheWay(plr: Player, npc: Int) {
    plr.newDialogue()
        .player(Expression.QUIZZICAL, "Isn't this place built a bit out the way?")
        .npc(npc, "We like it that way actually! We get disturbed less. We",
             "still get rather a large amount of travellers looking for", "sanctuary and healing here as it is!")
        .open()
}
