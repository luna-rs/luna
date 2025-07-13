package world.player.skill.slayer

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.npcDef
import api.predef.numF
import api.predef.rand
import api.predef.slayer
import io.luna.game.model.def.NpcCombatDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder
import io.luna.game.model.mob.dialogue.Expression

/**
 * Holds important constants and utility functions related to the Slayer skill.
 */
object Slayer {

    /**
     * If specialized slayer equipment is needed in order to fight specific slayer monsters.
     */
    const val EQUIPMENT_NEEDED = true

    /**
     * The XP multiplier specifically for slayer.
     */
    private val XP_MULTIPLIER = 1.0

    /**
     * An attribute representing a player's active slayer task.
     */
    var Player.activeSlayerTask: ActiveSlayerTask? by Attr.nullableObj(ActiveSlayerTask::class)
        .persist("active_slayer_task")

    /**
     * An attribute representing a player's completed task count.
     */
    var Player.completedSlayerTasks: Int by Attr.int().persist("completed_slayer_tasks")

    /**
     * An attribute representing if tasks will be checked for difficulty before being assigned.
     */
    var Player.difficultyChecking: Boolean by Attr.boolean(true).persist("slayer_difficulty_checking")

    /**
     * Decrement `1` from the remaining amount of tasks and adds XP for [npcId]. If the remaining amount of monsters is
     * equal or lower to `0`, the task will be completed.
     */
    fun record(plr: Player, npcId: Int) {
        val activeTask = plr.activeSlayerTask
        val taskType = activeTask?.task
        if (taskType != null && taskType.npcs.contains(npcId)) {
            addXp(plr, npcId)
            if (activeTask.decrement()) {
                completeTask(plr)
            }
        }
    }

    /**
     * Performs the process of completing a slayer task for [plr], and resets the current task.
     */
    private fun completeTask(plr: Player) {
        val completed = ++plr.completedSlayerTasks
        val count = if (completed == 1) "1 task" else "$completed tasks"
        plr.sendMessage("You've completed $count in a row; return to a Slayer master.")
        plr.activeSlayerTask = null
    }

    /**
     * Gives [plr] the slayer experience for killing an NPC with ID [npcId].
     */
    private fun addXp(plr: Player, npcId: Int) {
        val addXp = NpcCombatDefinition.ALL.get(npcId)
            .map { it.hitpoints * XP_MULTIPLIER }
            .orElseThrow { IllegalStateException("No combat definition for [npcId=$npcId].") }
        if (addXp > 0.0) {
            plr.slayer.addExperience(addXp)
        }
    }

    /**
     * Assigns an **eligible** task to [plr] from [master], possibly `null`.
     */
    fun assignTask(plr: Player, master: SlayerMaster): ActiveSlayerTask? {
        val taskList = master.tasks.filter {
            plr.slayer.level >= it.type.level && (!plr.difficultyChecking || it.type.difficulty(plr))
        }
        if (taskList.isEmpty()) {
            return null
        }
        var totalWeight = 0
        val ranges = ArrayList<Pair<SlayerTask, IntRange>>()
        for (task in taskList) {
            ranges += Pair(task, totalWeight until totalWeight + task.weight)
            totalWeight += task.weight
        }
        val selected = rand().nextInt(totalWeight)
        val task = ranges.find { it.second.contains(selected) }?.first
        return if (task == null) null else ActiveSlayerTask(task.type, master, task.amount.random())
    }

    /**
     * Opens the slayer equipment shop.
     */
    fun openShop(plr: Player) {
        plr.interfaces.openShop("Slayer Equipment")
    }

    /**
     * Opens the main dialogue for the "Talk-to" option on slayer masters.
     */
    fun openDialogue(plr: Player, master: SlayerMaster) {
        plr.newDialogue().npc(master.id, Expression.DEFAULT, "'Ello, and what are you after then?")
            .options("I need another assignment.", { openAssignmentDialogue(it, master) },
                "Have you anything to trade?", { openShopDialogue(it, master) },
                "Let's talk about the difficulty of my assignments.", { openDifficultyDialogue(it, master) },
                "Err... Nothing...", { it.newDialogue().player("Err... Nothing...").open() }).open()
    }

    /**
     * Opens the task assignment portion of the dialogue for [plr] from the perspective of [master].
     */
    private fun openAssignmentDialogue(plr: Player, master: SlayerMaster) {
        val dialogue = plr.newDialogue().player("I need another assignment.")

        fun assignTaskDialogue(lastDialogue: DialogueQueueBuilder?) {
            var last = lastDialogue
            if (last == null) {
                last = plr.newDialogue()
            }
            val selectedTask = assignTask(plr, master)
            if (selectedTask == null) {
                last!!.npc(master.id, Expression.DEFAULT, "Sorry, I can't find any task to assign...").open()
            } else {
                plr.activeSlayerTask = selectedTask
                last!!.npc(
                    master.id,
                    Expression.DEFAULT,
                    "Excellent, you're doing great.",
                    "Your new task is to kill ${selectedTask.remaining} ${selectedTask.task.plural}."
                )
                    .options("Got any tips for me?",
                        {
                            val tip = selectedTask.task.tip.split("\n")
                            val currentDialogue = it.newDialogue().player("Got any tips for me?")
                            val nextDialogue = run {
                                if (tip.size > 1) {
                                    currentDialogue.npc(
                                        master.id,
                                        Expression.DEFAULT,
                                        *tip.toTypedArray()
                                    )
                                } else {
                                    currentDialogue.npc(
                                        master.id,
                                        Expression.DEFAULT,
                                        tip[0]
                                    )
                                }
                            }
                            nextDialogue.player("Great, thanks!").open()
                        },
                        "Okay, great!",
                        {
                            it.newDialogue().player("Okay, great!")
                                .npc(
                                    master.id, Expression.DEFAULT,
                                    "Good luck! Don't forget to come back when you need a new assignment."
                                )
                                .open()
                        })
                    .open()
            }
        }

        if (plr.activeSlayerTask == null) {
            if ((plr.combatLevel >= master.combatLevel && plr.slayer.level >= master.slayerLevel) && master != SlayerMaster.DURADEL) {
                // No slayer task, but we have high levels.
                val nextMasterId = SlayerMaster.computeNextBestMaster(master).id
                val nextMasterName = npcDef(nextMasterId).name
                dialogue.npc(
                    master.id,
                    Expression.DEFAULT,
                    "You're actually very strong, are you sure you",
                    "don't want $nextMasterName to assign you a task?"
                )
                    .options("No that's okay, I'll take a task from you.",
                        {
                            it.newDialogue().player("No that's okay, I'll take a task from you.")
                                .then { assignTaskDialogue(null) }.open()
                        },
                        "Oh okay then, I'll go talk to $nextMasterName.",
                        {
                            it.newDialogue().player("Oh okay then, I'll go talk to $nextMasterName.").open()
                        })
                    .open()
            } else {
                // No slayer task.
                assignTaskDialogue(dialogue)
            }
        } else {
            val activeTask = plr.activeSlayerTask!!.task
            val remaining = plr.activeSlayerTask!!.remaining
            if (master == SlayerMaster.TURAEL && !master.types.contains(activeTask)) {
                dialogue.npc(
                    master.id,
                    Expression.DEFAULT,
                    "You're still hunting ${activeTask.plural}, with $remaining to go."
                )
                    .npc(
                        master.id,
                        Expression.DEFAULT,
                        "Although, it's not an assignment that I'd normally",
                        "give... I guess I could give you a new assignment, if",
                        "you'd like."
                    )
                    .npc(
                        master.id,
                        Expression.DEFAULT,
                        "If you do get a new one, you will reset your task",
                        "streak of ${numF(plr.completedSlayerTasks)}. Is that okay?"
                    )
                    .options("Yes, please.", {
                        plr.activeSlayerTask = null
                        plr.completedSlayerTasks = 0
                        it.interfaces.close()
                    },
                        "No, thanks.", { it.interfaces.close() })
                    .open()
            } else if (plr.slayer.level < activeTask.level || !activeTask.difficulty(plr)) {
                dialogue.npc(
                    master.id,
                    Expression.DEFAULT,
                    "You're still hunting ${activeTask.plural}, with $remaining to go."
                )
                    .npc(
                        master.id,
                        Expression.DEFAULT,
                        "I don't think that's a suitable task for you.",
                        "Shall I cancel it? This will not wipe your task streaks."
                    )
                    .options("Yes, please cancel it.", {
                        it.newDialogue().player("Yes, please cancel it.")
                            .npc(
                                master.id, Expression.DEFAULT, "Alright, consider the task cancelled.",
                                "You can now get a new assignment when you want one."
                            )
                            .then { plr.activeSlayerTask = null }
                    },
                        "No, thanks, I want to try doing it.", { it.interfaces.close() }).open()
            } else {
                dialogue.npc(
                    master.id,
                    Expression.DEFAULT,
                    "You're still hunting ${activeTask.plural}, you have $remaining to go.",
                    "Come back when you've finished your task."
                )
                    .open()
            }
        }
    }

    /**
     * Opens the shop portion of the dialogue for [plr] from the perspective of [master].
     */
    private fun openShopDialogue(plr: Player, master: SlayerMaster) {
        plr.newDialogue().player("Have you anything to trade?")
            .npc(master.id, Expression.DEFAULT, "I have a wide variety of Slayer equipment for sale.")
            .then { openShop(plr) }.open()
    }

    /**
     * Opens the difficulty selection portion of the dialogue for [plr] from the perspective of [master].
     */
    private fun openDifficultyDialogue(plr: Player, master: SlayerMaster) {
        val dialogue = plr.newDialogue().player("Let's talk about the difficulty of my assignments.")
        if (!plr.difficultyChecking) {
            dialogue.npc(
                master.id, Expression.DEFAULT, "The Slayer Masters may currently assign you any",
                "task in our lists, regardless of your combat level."
            )
                .options("That's fine - I can handle any task.", {
                    it.newDialogue().player("That's fine - I can handle any task.")
                        .npc(master.id, Expression.DEFAULT, "That's the spirit.").open()
                },
                    "In future, please don't give anything too tough.", {
                        it.newDialogue()
                            .player("In future, please don't give anything too tough.")
                            .npc(
                                master.id,
                                Expression.DEFAULT,
                                "Okay, from now on, all the Slayer Masters will take",
                                "your combat level into account when choosing tasks",
                                "for you, so you shouldn't get anything too hard."
                            )
                            .then { plr.difficultyChecking = true }.open()
                    }).open()
        } else {
            dialogue.npc(
                master.id,
                Expression.DEFAULT,
                "The Slayer Masters will take your combat level into",
                "account when choosing tasks for you, so you shouldn't",
                "get anything too hard."
            )
                .options("That's fine - I don't want anything too tough.", {
                    it.newDialogue().player("That's fine - I don't want anything too tough.")
                        .npc(master.id, Expression.DEFAULT, "Okay, we'll keep checking your combat level.").open()
                },
                    "Stop checking my combat level - I can take anything!", {
                        it.newDialogue().player("Stop checking my combat level - I can take anything!")
                            .npc(
                                master.id,
                                Expression.DEFAULT,
                                "Okay, from now on, all the Slayer Masters will",
                                "assign you anything from their lists, regardless",
                                "of your combat level."
                            )
                            .then { plr.difficultyChecking = false }.open()
                    }).open()
        }
    }
}