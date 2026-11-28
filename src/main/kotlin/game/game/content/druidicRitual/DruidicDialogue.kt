/* Dialogue adapted from LostCityRS/Content area_taverly at 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50. */
package game.content.druidicRitual

import game.content.druidicRitual.DruidicRitual as Quest
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption

/**
 * Handles the druids' conversations and validates stage changes at the end of each hand-off.
 *
 * @author lare96
 */
object DruidicDialogue {
    const val SEEK_QUEST = "I'm in search of a quest."
    const val ACCEPT = "Ok, I will try and help."
    const val OFFER_HELP = "I've been sent to help purify the Varrock stone circle."
    const val LESSON = "Can you explain the fundamentals of Herblore?"
    private const val DECLINE = "No, that doesn't sound very interesting."
    private const val CIRCLE = "What about the stone circle full of dark wizards?"
    private const val GOODBYE = "I'll be on my way."

    fun kaqemeex(player: Player, npc: Npc) {
        val lesson = DialogueOption(LESSON) { teach(it, npc) }
        val dialogue = player.newDialogue()
        when (Quest.stage(player)) {
            Quest.NOT_STARTED -> dialogue.npc(npc.id, "What brings you to our holy monument?")
                .options(listOf(
                    DialogueOption("Who are you?") { circleHistory(it, npc, false) },
                    DialogueOption(SEEK_QUEST) { offerQuest(it, npc) },
                    DialogueOption("Did you build this?") { circleHistory(it, npc, true) },
                    lesson
                )).open()
            Quest.STARTED, Quest.SPOKE_TO_SANFEW -> dialogue
                .npc(npc.id, "My fellow druid Sanfew is in the village to the south. He needs your help with the ritual.")
                .options(listOf(lesson, DialogueOption(GOODBYE) { })).open()
            Quest.DELIVERED_MEATS -> dialogue
                .npc(npc.id, "Sanfew tells me you've helped prepare the ritual. As promised, I'll share what I know about Herblore.")
                .then {
                    if (near(it, npc)) Quest.finish(it) { p -> teach(p, npc) }
                }.open()
            else -> dialogue.npc(npc.id, "Hello again. How is the Herblore going?")
                .options(listOf(
                    DialogueOption("Very well, thank you.") {
                        it.newDialogue().npc(npc.id, "That is good to hear.").open()
                    },
                    DialogueOption("I need more practice at it.") {
                        it.newDialogue().npc(npc.id, "Keep at it. Practise with whichever herbs you can identify.").open()
                    },
                    lesson
                )).open()
        }
    }

    private fun circleHistory(player: Player, npc: Npc, built: Boolean) {
        player.newDialogue().npc(npc.id, if (built)
            "Our forefathers built circles like this centuries ago. We know of two that remain, but only one is still usable."
        else "We are the druids of Guthix. We worship at stone circles like this one.")
            .options(listOf(
                DialogueOption(CIRCLE) { offerQuest(it, npc) },
                DialogueOption(SEEK_QUEST) { offerQuest(it, npc) },
                DialogueOption("What does Guthix stand for?") {
                    it.newDialogue().npc(npc.id, "Guthix is the god of balance and nature. We honour his work in the world.").open()
                },
                DialogueOption(GOODBYE) { }
            )).open()
    }

    private fun offerQuest(player: Player, npc: Npc) {
        player.newDialogue()
            .npc(npc.id, "The circle south of Varrock used to be ours. Dark wizards put a curse on the stones.")
            .npc(npc.id, "We need someone to help us prepare a ritual to purify it. Will you help us?")
            .options(listOf(
                DialogueOption(ACCEPT) { accept(it, npc) },
                DialogueOption(DECLINE) { decline(it, npc) },
                DialogueOption("Is there anything in this for me?") {
                    it.newDialogue().npc(npc.id, "We druids value wisdom over wealth. I can share what I know about Herblore.")
                        .options(ACCEPT, { p -> accept(p, npc) }, DECLINE, { p -> decline(p, npc) }).open()
                }
            )).open()
    }

    private fun accept(player: Player, npc: Npc) {
        if (!near(player, npc)) return
        Quest.advance(player, Quest.NOT_STARTED, Quest.STARTED)
        player.newDialogue()
            .npc(npc.id, "Excellent. Speak to Sanfew in the village south of here. He knows what the ritual needs.")
            .open()
    }

    private fun decline(player: Player, npc: Npc) {
        player.newDialogue().npc(npc.id, "Very well, adventurer. Come back if you change your mind.").open()
    }

    fun sanfew(player: Player, npc: Npc) {
        val dialogue = player.newDialogue()
        when (Quest.stage(player)) {
            Quest.NOT_STARTED -> dialogue.npc(npc.id, "What can I do for you, young 'un?")
                .options("I've heard you druids can teach me Herblore.", {
                    it.newDialogue().npc(npc.id, "Ask Kaqemeex at the stone circle north of here. He's our best Herblore teacher.").open()
                }, GOODBYE, { }).open()
            Quest.STARTED -> dialogue.npc(npc.id, "What can I do for you, young 'un?")
                .options(OFFER_HELP, { explainRitual(it, npc) }, GOODBYE, { }).open()
            Quest.SPOKE_TO_SANFEW -> {
                dialogue.npc(npc.id, "Have you brought the ingredients for the potion?")
                if (Quest.hasOfferings(player)) {
                    dialogue.npc(npc.id, "Ah, all four! Hand them over, and I'll finish the potion to honour Guthix.")
                        .then {
                            if (!near(it, npc)) return@then
                            val text = if (Quest.deliver(it))
                                "Thank you! Go and see Kaqemeex at the stone circle."
                            else "Come back when you have all four enchanted meats with you."
                            it.newDialogue().npc(npc.id, text).open()
                        }.open()
                } else {
                    dialogue.npc(npc.id, "Come back when you have all four enchanted meats.")
                        .options("What did you need again?", { explainRitual(it, npc) }, GOODBYE, { }).open()
                }
            }
            else -> dialogue.npc(npc.id, "I can take care of the rest of the preparations. Thank you for your help.").open()
        }
    }

    private fun explainRitual(player: Player, npc: Npc) {
        player.newDialogue()
            .npc(npc.id, "To make the potion in Guthix's honour, I need raw beef, raw rat meat, raw bear meat and raw chicken.")
            .npc(npc.id, "Dip each meat separately in the Cauldron of Thunder. It's in the dungeon in the woods south of here.")
            .then { if (near(it, npc)) Quest.advance(it, Quest.STARTED, Quest.SPOKE_TO_SANFEW) }.open()
    }

    private fun teach(player: Player, npc: Npc) {
        if (!near(player, npc)) return
        player.newDialogue()
            .npc(npc.id, "Herblore is the art of making potions and poisons from herbs and other ingredients.")
            .npc(npc.id, "Identify the herb first. You can gather herbs or find them dropped by monsters.")
            .npc(npc.id, "Fill a vial with water and add a herb. Some ingredients are ground with a pestle and mortar.")
            .npc(npc.id, "For an attack potion, add a Guam leaf to a vial of water, then add an eye of newt.")
            .npc(npc.id, "Potions have different level requirements. The Herblore skill guide lists what you can make.")
            .open()
    }

    private fun near(player: Player, npc: Npc): Boolean =
        player.state == EntityState.ACTIVE && player.isAlive && npc.state == EntityState.ACTIVE &&
            player.z == npc.z && player.position.isWithinDistance(npc.position, 3)
}
