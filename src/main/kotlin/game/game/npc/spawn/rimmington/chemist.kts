package game.npc.spawn.rimmington

import api.predef.*
import io.luna.game.model.mob.Player

/**
 * The chemist in Rimmington, who keeps the lamp oil still.
 */
val CHEMIST = 367

/**
 * Asks [plr] whether they've come about lamps.
 */
fun talk(plr: Player) {
    // TODO Once Biohazard is added, players at that point in the quest talk to him about touch paper instead.
    plr.newDialogue()
        .options("Yes.", { lamps(it) }, "No.", { hello(it) })
        .title("Do you want to talk about lamps?")
        .open()
}

/**
 * The chemist offers to explain how lamp oil is made.
 */
fun lamps(plr: Player) {
    plr.newDialogue()
        .player("Hi, I need fuel for a lamp.")
        .npc(CHEMIST, "Hello there, the fuel you need is lamp oil, do you need help making it?")
        .options("Yes please.", {
            it.newDialogue()
                .player("Yes please.")
                .npc(CHEMIST, "It's really quite simple. You use the small still in here. It's all set up, so " +
                    "there's no fiddling around with dials...")
                .npc(CHEMIST, "Just put ordinary swamp tar in, and then use a lantern or lamp to get the oil out.")
                .player("Thanks.")
                .open()
        }, "No thanks.", {
            it.newDialogue().player("No thanks.").open()
        })
        .open()
}

/**
 * The chemist is too busy for small talk.
 */
fun hello(plr: Player) {
    plr.newDialogue()
        .player("Hello.")
        .npc(CHEMIST, "Oh.. hello, how's it going?")
        .player("Good thanks.")
        .npc(CHEMIST, "Good to hear, sorry but I have a few things to do right now.")
        .player("Well I'd better let you get on then.")
        .open()
}

npc1(CHEMIST) { talk(plr) }
