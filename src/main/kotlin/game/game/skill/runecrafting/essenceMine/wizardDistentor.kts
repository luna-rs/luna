package game.skill.runecrafting.essenceMine

import api.predef.*
import io.luna.game.model.mob.dialogue.Expression

/**
 * Wizard Distentor, the head of the Magic Guild in Yanille.
 */
val WIZARD_DISTENTOR = EssenceMineWizard.WIZARD_DISTENTOR.npcId

npc1(WIZARD_DISTENTOR) {
    val distentor = targetNpc
    // TODO Rune Mysteries: until it's complete, he skips the options and the player says "Nothing thanks, I'm just
    //  looking around."
    plr.newDialogue()
        .npc(WIZARD_DISTENTOR, "Welcome to the Magicians' Guild!")
        .player("Hello there.")
        .npc(WIZARD_DISTENTOR, Expression.QUIZZICAL, "What can I do for you?")
        .options(
            "Nothing thanks, I'm just looking around.", {
                it.newDialogue()
                    .player("Nothing thanks, I'm just looking around.")
                    .npc(WIZARD_DISTENTOR, "That's fine with me.")
                    .open()
            },
            "Can you teleport me to the Rune Essence?", {
                it.newDialogue()
                    .player(Expression.QUIZZICAL, "Can you teleport me to the Rune Essence?")
                    .then { plr -> EssenceMine.teleport(distentor, plr) }
                    .open()
            })
        .open()
}

npc2(WIZARD_DISTENTOR) {
    EssenceMine.teleport(targetNpc, plr)
}
