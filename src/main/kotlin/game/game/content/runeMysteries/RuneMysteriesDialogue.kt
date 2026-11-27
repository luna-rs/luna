package game.content.runeMysteries

import game.content.runeMysteries.RuneMysteries as Quest
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption

/**
 * The Duke, Sedridor and Aubury's quest conversations and item handovers.
 *
 * @author lare96
 */
object RuneMysteriesDialogue {
    const val ASK_FOR_QUEST = "Have you any quests for me?"
    const val ACCEPT_QUEST = "Sure, no problem."
    const val SEEK_HEAD_WIZARD = "I'm looking for the head wizard."
    const val HAND_OVER_TALISMAN = "Ok, here you are."
    const val ASK_ABOUT_RESEARCH = "Can I help with your research?"
    const val ACCEPT_PACKAGE = "Yes, certainly."
    const val ASK_FOR_PACKAGE = "About the research package..."
    const val OFFER_PACKAGE = "I have been sent here with a package for you."
    const val ASK_FOR_NOTES = "Have you looked at the research package?"
    const val ASK_FOR_COPY = "Could I have a copy of your notes?"
    const val RETURN_NOTES = "I've brought Aubury's research notes."

    fun dukeOption(duke: Npc) = DialogueOption(ASK_FOR_QUEST) { player ->
        when (Quest.stage(player)) {
            Quest.NOT_STARTED -> offerQuest(player, duke)
            Quest.STARTED -> {
                if (owns(player, Quest.AIR_TALISMAN)) {
                    player.newDialogue().npc(duke.id, "Have you taken the talisman to the Wizards' Tower yet?").open()
                } else {
                    player.newDialogue()
                        .npc(duke.id, "You've lost it? One of my servants found another.",
                            "Take this to the head wizard, and keep it safe.")
                            .npc(duke.id, "Try the basement of the Wizards' Tower.")
                        .then { if (!owns(it, Quest.AIR_TALISMAN)) give(it, duke, Quest.STARTED, Quest.AIR_TALISMAN) }
                        .open()
                }
            }
            Quest.COMPLETE -> player.newDialogue().npc(duke.id, "No, all is well for me.").open()
            else -> player.newDialogue().npc(duke.id, "The wizards have the talisman now. How is their research going?").open()
        }
    }

    private fun offerQuest(player: Player, duke: Npc) {
        player.newDialogue()
            .npc(duke.id, "One of my subjects found a strange talisman. I've never seen anything quite like it.")
            .npc(duke.id, "Would you take it to the head wizard at the Wizards' Tower, south-west of Lumbridge?")
            .options(ACCEPT_QUEST, {
                it.newDialogue().npc(duke.id, "Thank you. I'm sure the head wizard will want to see it.")
                    .npc(duke.id, "You'll find his tower south-west of the castle.")
                    .then { p ->
                        if (give(p, duke, Quest.NOT_STARTED, Quest.AIR_TALISMAN)) Quest.advance(p, Quest.STARTED)
                    }.open()
            }, "Not right now.", {
                it.newDialogue().npc(duke.id, "Very well. Ask me again if you change your mind.").open()
            }).open()
    }

    fun sedridorOptions(player: Player, wizard: Npc): List<DialogueOption> {
        val lore = DialogueOption("What are you doing down here?") {
            it.newDialogue()
                .npc(wizard.id, "I'm studying what's left of the old Wizards' Tower. It was destroyed in a fire.")
                .npc(wizard.id, "The wizards of Zamorak burnt the tower. We lost much of what we knew about rune stones.")
                .npc(wizard.id, "I hope these ruins will tell us how our predecessors made runes.").open()
        }
        return when (Quest.stage(player)) {
            Quest.NOT_STARTED -> listOf(lore)
            Quest.STARTED -> listOf(lore, DialogueOption(SEEK_HEAD_WIZARD) { discussTalisman(it, wizard) })
            Quest.TALISMAN_DELIVERED -> listOf(DialogueOption(ASK_ABOUT_RESEARCH) { offerPackage(it, wizard) })
            Quest.PACKAGE_RECEIVED -> listOf(DialogueOption(ASK_FOR_PACKAGE) { replacePackage(it, wizard) })
            Quest.PACKAGE_DELIVERED -> listOf(DialogueOption(ASK_FOR_PACKAGE) {
                it.newDialogue().npc(wizard.id, "Go back to Aubury and see what he made of the package.").open()
            })
            Quest.NOTES_RECEIVED -> listOf(DialogueOption(RETURN_NOTES) { deliverNotes(it, wizard) })
            else -> emptyList()
        }
    }

    private fun discussTalisman(player: Player, wizard: Npc) {
        player.newDialogue()
            .npc(wizard.id, "What business do you have with the head wizard?")
            .player("The Duke of Lumbridge sent me with a talisman he found.")
            .npc(wizard.id, "Did he now? Let's have a look at it.")
            .options(HAND_OVER_TALISMAN, { acceptTalisman(it, wizard) },
                "No, I'll only give it to the head wizard.", {
                    it.newDialogue()
                        .npc(wizard.id, "Then let me prove who I am. Your name is ${it.username}, isn't it?")
                        .npc(wizard.id, "You learn a few tricks as head wizard. Now, may I see the talisman?")
                        .then { p -> acceptTalisman(p, wizard) }.open()
                }).open()
    }

    private fun acceptTalisman(player: Player, wizard: Npc) {
        if (!canHandOver(player, wizard, Quest.STARTED)) return
        if (!player.inventory.remove(Quest.AIR_TALISMAN)) {
            player.newDialogue().npc(wizard.id, "Come back when you have the talisman with you.").open()
            return
        }
        Quest.advance(player, Quest.TALISMAN_DELIVERED)
        offerPackage(player, wizard)
    }

    private fun offerPackage(player: Player, wizard: Npc) {
        player.newDialogue()
            .npc(wizard.id, "This talisman might be the piece we've been missing.")
            .npc(wizard.id, "While I study it, will you take this sealed package to Aubury, who runs the rune shop in Varrock?")
            .npc(wizard.id, "I need his thoughts on my research. Please bring back whatever he gives you.")
            .options(ACCEPT_PACKAGE, {
                it.newDialogue().npc(wizard.id, "Head north through Draynor Village, then east from the Barbarian Village to Varrock.")
                    .npc(wizard.id, "Ask for Aubury at the rune shop.")
                    .then { p ->
                        if (give(p, wizard, Quest.TALISMAN_DELIVERED, Quest.RESEARCH_PACKAGE)) {
                            Quest.advance(p, Quest.PACKAGE_RECEIVED)
                        }
                    }.open()
            }, "No, I'm busy.", {
                it.newDialogue().npc(wizard.id, "Come back when you have some time. I'll keep studying the talisman.").open()
            }).open()
    }

    private fun replacePackage(player: Player, wizard: Npc) {
        if (owns(player, Quest.RESEARCH_PACKAGE)) {
            player.newDialogue().npc(wizard.id, "Please take the package to Aubury in Varrock as soon as you can.").open()
            return
        }
        player.newDialogue()
            .npc(wizard.id, "You lost it? Hold on. I should be able to bring it back.")
            .npc(wizard.id, "Here it is, seal intact. See that Aubury gets it this time.")
            .then {
                if (!owns(it, Quest.RESEARCH_PACKAGE)) give(it, wizard, Quest.PACKAGE_RECEIVED, Quest.RESEARCH_PACKAGE)
            }.open()
    }

    fun auburyOption(player: Player, aubury: Npc): DialogueOption? = when (Quest.stage(player)) {
        Quest.PACKAGE_RECEIVED -> if (player.inventory.contains(Quest.RESEARCH_PACKAGE)) {
            DialogueOption(OFFER_PACKAGE) { deliverPackage(it, aubury) }
        } else null
        Quest.PACKAGE_DELIVERED -> DialogueOption(ASK_FOR_NOTES) { giveNotes(it, aubury, false) }
        Quest.NOTES_RECEIVED -> if (!owns(player, Quest.RESEARCH_NOTES)) {
            DialogueOption(ASK_FOR_COPY) { giveNotes(it, aubury, true) }
        } else null
        else -> null
    }

    fun deliverPackage(player: Player, aubury: Npc) {
        if (!canHandOver(player, aubury, Quest.PACKAGE_RECEIVED)) return
        player.newDialogue()
            .npc(aubury.id, "A package from the head wizard? Please, let me see it.")
            .npc(aubury.id, "He wouldn't send someone here unless it was important.")
            .then {
                if (canHandOver(it, aubury, Quest.PACKAGE_RECEIVED) && it.inventory.remove(Quest.RESEARCH_PACKAGE)) {
                    Quest.advance(it, Quest.PACKAGE_DELIVERED)
                    it.newDialogue().npc(aubury.id, "This is incredible. Give me a moment to read it, then speak to me again.")
                        .open()
                }
            }.open()
    }

    private fun giveNotes(player: Player, aubury: Npc, replacement: Boolean) {
        val expected = if (replacement) Quest.NOTES_RECEIVED else Quest.PACKAGE_DELIVERED
        player.newDialogue()
            .npc(aubury.id, if (replacement) "Luckily I made a copy. It's written in code, so it should be safe."
                else "The talisman and Sedridor's research fit what I'd learned about rune essence.")
            .npc(aubury.id, "Take these notes back to Sedridor. He can explain what it all means.")
            .then {
                if ((!replacement || !owns(it, Quest.RESEARCH_NOTES)) &&
                    give(it, aubury, expected, Quest.RESEARCH_NOTES) && !replacement) {
                    Quest.advance(it, Quest.NOTES_RECEIVED)
                }
            }.open()
    }

    fun deliverNotes(player: Player, wizard: Npc) {
        if (!canHandOver(player, wizard, Quest.NOTES_RECEIVED)) return
        if (!player.inventory.contains(Quest.RESEARCH_NOTES)) {
            val reminder = if (player.bank.contains(Quest.RESEARCH_NOTES)) "Please bring the notes back from your bank."
                else "Ask Aubury if he still has a copy of his notes."
            player.newDialogue().npc(wizard.id, reminder).open()
            return
        }
        player.newDialogue()
            .npc(wizard.id, "Before I look at Aubury's notes, let me tell you what we've found.")
            .npc(wizard.id, "The old wizards made rune stones by bringing pieces of rune essence to elemental altars.")
            .npc(wizard.id, "I found a scroll about the essence in these ruins. Aubury found how to teleport to it.")
            .npc(wizard.id, "Your talisman points the way to the Air Altar. Other talismans can lead you to the other altars.")
            .npc(wizard.id, "I'll take Aubury's notes now. Here is your air talisman back, and thank you for your help.")
            .then { if (canHandOver(it, wizard, Quest.NOTES_RECEIVED)) Quest.finish(it) }
            .open()
    }

    private fun owns(player: Player, item: Int) =
        player.inventory.contains(item) || player.bank.contains(item) || player.equipment.contains(item)

    private fun give(player: Player, npc: Npc, expected: Int, item: Int): Boolean {
        if (!canHandOver(player, npc, expected)) return false
        if (player.inventory.add(item)) return true
        player.sendMessage("You need a free inventory slot.")
        return false
    }

    private fun canHandOver(player: Player, npc: Npc, expected: Int) =
        Quest.stage(player) == expected && player.isAlive && npc.isAlive && npc.state == EntityState.ACTIVE &&
            player.z == npc.z && player.position.isWithinDistance(npc.position, 3)
}
