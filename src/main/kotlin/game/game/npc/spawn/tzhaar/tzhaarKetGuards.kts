package game.npc.spawn.tzhaar

import api.predef.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueUtils

/**
 * The TzHaar-Ket who guard the closed parts of the TzHaar city. They don't fight, and they don't let JalYt past.
 */
val GUARDS = intArrayOf(2624, 2625)

/**
 * Opens a dialogue that starts with [guard] saying [line].
 */
fun guardSays(plr: Player, guard: Int, line: String) =
    plr.newDialogue().npc(guard, *DialogueUtils.splitChatLines(line))

fun greet(plr: Player, guard: Int) {
    guardSays(plr, guard, "You need help ${TzHaarNames.name(plr)}?")
        .options("Why can't I go past here?", { whyNotPast(it, guard) },
                 "What are you guarding?", { guarding(it, guard) },
                 "What did you call me?", {
                     TzHaarNames.whatDidYouCallMe(it, guard,
                                                  "Silly JalYt, I am TzHaar-Ket, one of the guardians of our city.",
                                                  "There are the wise TzHaar-Mej who guide us, the swift TzHaar-Xil " +
                                                      "who hunt for our food, and the skilled TzHaar-Hur who craft our " +
                                                      "homes and tools.")
                 },
                 "No I'm fine thanks.", { it.newDialogue().player("No I'm fine thanks.").open() })
        .open()
}

fun whyNotPast(plr: Player, guard: Int) {
    plr.newDialogue()
        .player("Why can't I go past here?")
        .npc(guard, "This area closed to JalYt.")
        .options("Why's that?", { whyThat(it, guard) },
                 "What are you guarding?", { guarding(it, guard) },
                 "Fair enough.", { it.newDialogue().player("Fair enough.").open() })
        .open()
}

fun whyThat(plr: Player, guard: Int) {
    plr.newDialogue()
        .player("Why's that?")
        .npc(guard, *DialogueUtils.splitChatLines("JalYt are not trusted. Nor are capable of withstanding the heat. " +
                                                      "The features of the city are a secret."))
        .options("That's a shame.", { it.newDialogue().player("That's a shame.").open() },
                 "What if I could prove I am capable?", { prove(it, guard) },
                 "I'm a hardened adventurer, nothing can stop me!", {
                     it.newDialogue()
                         .player("I'm a hardened adventurer, nothing can stop me!")
                         .npc(guard, *DialogueUtils.splitChatLines("Back off ${TzHaarNames.name(it)}, trust me when I " +
                                                                       "say our obsidian bodies are harder."))
                         .open()
                 })
        .open()
}

fun prove(plr: Player, guard: Int) {
    plr.newDialogue()
        .player("What if I could prove that I am capable?")
        .npc(guard, *DialogueUtils.splitChatLines("${TzHaarNames.name(plr)}, I suppose that is perfectly " +
                                                      "acceptable, but it would take lots to convince."))
        .options("How about a large stack of gold?", {
            it.newDialogue()
                .player("How about a large stack of coins.")
                .npc(guard, "Gold is bad currency, I have no use for JalYt coins.")
                .open()
        }, "Sorry, I don't think I am capable.", {
            it.newDialogue().player("Sorry, I don't think I am capable.").open()
        })
        .open()
}

fun guarding(plr: Player, guard: Int) {
    val name = TzHaarNames.name(plr)
    plr.newDialogue()
        .player("What are you guarding?")
        .npc(guard, *DialogueUtils.splitChatLines("$name, you are not trusted, this is secret area. Less questions! " +
                                                      "I will say that it's the inner core of the city though."))
        .options("Oh well, Goodbye.", { it.newDialogue().player("Oh well, goodbye.").open() },
                 "Oh well, $name over and out.", {
                     it.newDialogue()
                         .player(*DialogueUtils.splitChatLines("Oh well, $name over and out."))
                         .npc(guard, *DialogueUtils.splitChatLines("Haha funny JalYt try to speak language, why do " +
                                                                       "you put that accent on when attempting it?"))
                         .player(*DialogueUtils.splitChatLines("maybe it helps you to understand what I'm saying. " +
                                                                   "See you around."))
                         .open()
                 })
        .open()
}

for (guard in GUARDS) {
    npc1(guard) { greet(plr, guard) }
}
