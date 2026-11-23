package game.skill.slayer

import api.predef.*
import game.skill.slayer.Slayer.activeSlayerTask
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder
import io.luna.game.model.mob.dialogue.Expression
import java.util.function.Consumer

/**
 * The enchanted gem.
 */
val ENCHANTED_GEM = 4155

/**
 * The questions the gem can put to a Slayer Master.
 */
enum class Question(val text: String) {
    PROGRESS("How am I doing so far?"),
    WHO("Who are you?"),
    WHERE("Where are you?"),
    TIPS("Got any tips for me?")
}

/**
 * The master who gave the current task, or with no task the highest master the player can take one from.
 */
fun contactedMaster(plr: Player) =
    plr.activeSlayerTask?.assignee
        ?: SlayerMaster.entries.last { plr.combatLevel >= it.combatLevel && plr.slayer.level >= it.slayerLevel }

/**
 * Where [master] can be found.
 */
fun location(master: SlayerMaster) =
    when (master) {
        SlayerMaster.TURAEL -> "in Burthorpe"
        SlayerMaster.MAZCHNA -> "in Canifis"
        SlayerMaster.VANNAKA -> "under Edgeville"
        SlayerMaster.CHAELDAR -> "in Zanaris"
        SlayerMaster.DURADEL -> "in Shilo Village"
    }

/**
 * Asks [master] [question], then offers the other questions.
 */
fun ask(plr: Player, master: SlayerMaster, question: Question) {
    val dialogue = plr.newDialogue().player(Expression.QUIZZICAL, question.text)
    val task = plr.activeSlayerTask
    when (question) {
        Question.PROGRESS ->
            if (task == null) {
                dialogue.npc(master.id, Expression.CONFUSED, "You need something new to hunt. Come and see me",
                             "when you can and I'll give you a new task.")
            } else {
                dialogue.npc(master.id, Expression.HAPPY, "You're currently assigned to kill ${task.task.plural};",
                             "only ${task.remaining} more to go.")
            }
        Question.WHO ->
            dialogue.npc(master.id, Expression.HAPPY, "My name's ${npcDef(master.id).name}, I'm the Slayer Master best",
                         "able to train you.")
        Question.WHERE ->
            dialogue.npc(master.id, Expression.NEUTRAL, "You'll find me ${location(master)}.",
                         "I'll be here when you need a new task.")
        Question.TIPS -> {
            if (task == null) {
                dialogue.npc(master.id, Expression.NEUTRAL, "You need something new to hunt.")
            } else {
                dialogue.npc(master.id, Expression.NEUTRAL, *task.task.tip.split("\n").toTypedArray())
            }
            dialogue.player(Expression.HAPPY, "Great, thanks!")
        }
    }
    offer(dialogue, master, Question.entries - question, "That's all thanks.").open()
}

/**
 * Adds an options dialogue with [questions] and a closing [farewell] to [dialogue].
 */
fun offer(dialogue: DialogueQueueBuilder, master: SlayerMaster, questions: List<Question>,
          farewell: String): DialogueQueueBuilder {
    fun answer(question: Question) = Consumer<Player> { ask(it, master, question) }
    val close = Consumer<Player> { it.newDialogue().player(Expression.NEUTRAL, farewell).open() }
    return if (questions.size == 4) {
        dialogue.options(questions[0].text, answer(questions[0]),
                         questions[1].text, answer(questions[1]),
                         questions[2].text, answer(questions[2]),
                         questions[3].text, answer(questions[3]),
                         farewell, close)
    } else {
        dialogue.options(questions[0].text, answer(questions[0]),
                         questions[1].text, answer(questions[1]),
                         questions[2].text, answer(questions[2]),
                         farewell, close)
    }
}

// Activate the gem to contact a Slayer Master.
item1(ENCHANTED_GEM) {
    val master = contactedMaster(plr)
    val dialogue = plr.newDialogue()
        .npc(master.id, Expression.HAPPY, "Hello there ${plr.username}, what can I help you with?")
    offer(dialogue, master, Question.entries, "Nothing really.").open()
}
