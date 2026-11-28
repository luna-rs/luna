package game.content.impCatcher

import game.content.impCatcher.ImpCatcher as Quest
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * Handles Wizard Mizgog's request, progress checks and return of his beads.
 *
 * @author lare96
 */
object ImpCatcherDialogue {
    const val ASK_FOR_QUEST = "Give me a quest!"
    const val ASK_POLITELY = "Give me a quest please."
    const val ACCEPT = "I'll try."
    const val ALL_BEADS = "I've got all four beads."
    private const val QUIET = "Most of your friends are pretty quiet aren't they?"
    private const val MORE_QUESTS = "Got any more quests?"
    private const val THREAT = "Give me a quest or else!"
    private const val IMPATIENT = "Just stop messing around and give me a quest!"

    fun talk(player: Player) {
        when (Quest.stage(player)) {
            0 -> player.newDialogue().npc(Quest.MIZGOG, "Hello there.")
                .options(ASK_FOR_QUEST, ::ask, QUIET, ::quiet).open()
            Quest.STARTED -> checkBeads(player)
            Quest.COMPLETE -> player.newDialogue().npc(Quest.MIZGOG, "Hello there.")
                .options(MORE_QUESTS, { it.newDialogue().player(MORE_QUESTS)
                    .npc(Quest.MIZGOG, "No, everything is good with the world today.").open() },
                    QUIET, ::quiet).open()
        }
    }

    private fun ask(player: Player) {
        player.newDialogue().player(ASK_FOR_QUEST)
            .npc(Quest.MIZGOG, Expression.HAPPY, "Give me a quest what?")
            .options(ASK_POLITELY, ::request, THREAT, {
                it.newDialogue().player(THREAT)
                    .npc(Quest.MIZGOG, Expression.HAPPY, "Or else what? You'll attack me?")
                    .npc(Quest.MIZGOG, "Hahaha!").open()
            }, IMPATIENT, {
                it.newDialogue().player(IMPATIENT)
                    .npc(Quest.MIZGOG, Expression.HAPPY, "Ah, now you're assuming I have one to give.").open()
            }).open()
    }

    private fun request(player: Player) {
        player.newDialogue().player(ASK_POLITELY)
            .npc(Quest.MIZGOG, Expression.HAPPY, "Well, seeing as you asked nicely, I could do with some help.")
            .npc(Quest.MIZGOG, Expression.SAD, "The wizard Grayzag next door decided he didn't like me.",
                "He cast a summoning spell and summoned hundreds of little imps.")
            .npc(Quest.MIZGOG, "They stole eggs, balls of string and all sorts of things.",
                "But they also stole my four magical beads.")
            .npc(Quest.MIZGOG, "There was a red one, a yellow one, a black one and a white one.",
                "The imps have spread all over the kingdom. Could you get my beads back?")
            .player(ACCEPT)
            .then(Quest::start)
            .open()
    }

    private fun checkBeads(player: Player) {
        val dialogue = player.newDialogue().npc(Quest.MIZGOG, Expression.HAPPY, "So how are you doing finding my beads?")
        when (Quest.missingBeads(player).size) {
            0 -> dialogue.player(ALL_BEADS)
                .npc(Quest.MIZGOG, Expression.HAPPY, "Give them here and I'll sort out a reward.")
                .then(Quest::finish)
            Quest.beads.size -> dialogue.player("I have not found any yet.")
                .npc(Quest.MIZGOG, "I've lost a white bead, a red bead, a black bead and a yellow bead.",
                    "Go kill some imps!")
            else -> dialogue.player("I have found some of your beads.")
                .npc(Quest.MIZGOG, "Come back when you have them all.",
                    "The beads I need are red, yellow, black and white. Go chase some imps!")
        }
        dialogue.open()
    }

    private fun quiet(player: Player) {
        player.newDialogue().player(QUIET)
            .npc(Quest.MIZGOG, "Yes, they've mostly got their heads in the clouds, thinking about magic.").open()
    }
}
