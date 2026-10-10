package game.skill.runecrafting.essenceMine

import api.predef.*
import io.luna.game.model.Position
import io.luna.game.model.mob.dialogue.Expression

/**
 * Brimstail, the gnome wizard in his cave in the Tree Gnome Stronghold.
 */
val BRIMSTAIL = EssenceMineWizard.BRIMSTAIL.npcId

/**
 * The hollowed rock east of the Stronghold bridge, which leads down into Brimstail's cave.
 */
val HOLLOWED_ROCK = 194

/**
 * Where the hollowed rock leads: beside the ladder back up.
 */
val CAVE_LANDING = Position(2409, 9819)

npc1(BRIMSTAIL) {
    val brimstail = targetNpc
    // TODO Rune Mysteries: until it's complete, he skips the options and the player says "Nothing for now, thanks!"
    plr.newDialogue()
        .npc(BRIMSTAIL, "Hello adventurer, what can I do for you?")
        .options(
            "Can you teleport me to the Rune Essence?", {
                it.newDialogue()
                    .player(Expression.QUIZZICAL, "Can you teleport me to the Rune Essence?")
                    .npc(BRIMSTAIL, "Okay. Hold onto your hat!")
                    .then { plr -> EssenceMine.teleport(brimstail, plr) }
                    .open()
            },
            "Nothing for now, thanks!", {
                it.newDialogue()
                    .player("Nothing for now, thanks!")
                    .npc(BRIMSTAIL, "Ok. Just remember that a friend of a wizard is a friend", "of mine!")
                    .open()
            })
        .open()
}

npc2(BRIMSTAIL) {
    EssenceMine.teleport(targetNpc, plr)
}

object1(HOLLOWED_ROCK) {
    plr.move(CAVE_LANDING)
}
