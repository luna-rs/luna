package game.skill.runecrafting.essenceMine

import api.predef.*
import io.luna.game.model.mob.dialogue.Expression

/**
 * Sedridor, the head wizard in the Wizards' Tower basement.
 */
val SEDRIDOR = EssenceMineWizard.SEDRIDOR.npcId

npc1(SEDRIDOR) {
    val sedridor = targetNpc
    // TODO Rune Mysteries: until it's complete, Sedridor offers "What are you doing down here?" and runs the quest
    //  instead of the teleport.
    plr.newDialogue()
        .npc(SEDRIDOR, "Welcome adventurer, to the world renowned", "Wizards' Tower. How may I help you?")
        .options(
            "Nothing thanks, I'm just looking around.", {
                it.newDialogue()
                    .player("Nothing thanks, I'm just looking around.")
                    .npc(SEDRIDOR, Expression.CONFUSED, "Well, take care adventurer. You stand on the ruins of",
                         "the destroyed Wizards' Tower. Strange and powerful", "magicks lurk here.")
                    .open()
            },
            "Can you teleport me to the Rune Essence?", {
                it.newDialogue()
                    .player(Expression.QUIZZICAL, "Can you teleport me to the Rune Essence?")
                    .then { plr -> EssenceMine.teleport(sedridor, plr) }
                    .open()
            })
        .open()
}

npc2(SEDRIDOR) {
    EssenceMine.teleport(targetNpc, plr)
}
