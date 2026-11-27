package game.content.demonSlayer

import game.content.demonSlayer.DemonSlayer as Quest
import game.content.questRewards.QuestRewards
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption
import io.luna.game.model.mob.dialogue.Expression

/**
 * Native conversations for the investigation, three keys and Silverlight recovery.
 *
 * @author lare96
 */
object DemonSlayerDialogue {
    const val PAY_ARIS = "Okay, here you go."
    const val ASK_DELRITH = "Who's Delrith?"
    const val ACCEPT = "How can I stop him?"
    const val ASK_INCANTATION = "What is the incantation?"
    const val ASK_SWORD = "Where can I find Silverlight?"
    const val ARIS_SENT_ME = "Gypsy Aris sent me."
    const val ASK_KEYS = "Where are the three keys?"
    const val GIVE_KEYS = "I've found all three keys."
    const val LOST_SWORD = "I'm afraid I've lost Silverlight."
    const val BUY_SWORD = "Can I pay to borrow Silverlight?"
    const val TELL_ROVIN = "This is important."
    const val WARN_ROVIN = "A demon is coming to attack the city."
    const val ASK_TRAIBORN = "Sir Prysin gave you a key."
    const val GET_BONES = "I'll bring you the bones."
    const val OFFER_BONES = "I've brought you some bones."
    const val CLAIM_KEY = "Can you perform the ritual?"

    const val INCANTATION = "Carlem Aber Camerinthum Purchai Gabindo"

    fun aris(player: Player) {
        when (Quest.stage(player)) {
            0 -> player.newDialogue()
                .npc(Quest.ARIS, "Cross my palm with silver and I will tell your fortune.")
                .options(PAY_ARIS, ::fortune, "No, thank you.", ::close)
                .open()
            Quest.COMPLETE -> player.newDialogue().npc(Quest.ARIS, "Well done, young one. Varrock is safe again.").open()
            else -> arisAdvice(player)
        }
    }

    private fun fortune(player: Player) {
        if (Quest.stage(player) != 0) return
        if (!Quest.paidFortune(player)) {
            player.newDialogue().npc(Quest.ARIS, "Come back when you have a coin.").open()
            return
        }
        player.newDialogue()
            .npc(Quest.ARIS, "I see you holding a sword in my crystal ball.", "A shadow is rising behind you...")
            .npc(Quest.ARIS, Expression.SHOCKED, "It's Delrith! Delrith is coming!")
            .options(ASK_DELRITH, { p ->
                p.newDialogue()
                    .npc(Quest.ARIS, "A demon who nearly destroyed Varrock 150 years ago.",
                         "A hero named Wally stopped him with a sword called Silverlight.")
                    .npc(Quest.ARIS, "That's the sword I saw in your hands.")
                    .options(ACCEPT, ::accept, "I need a moment to think.", ::close)
                    .open()
            }, "I'll come back later.", ::close)
            .open()
    }

    private fun accept(player: Player) {
        player.newDialogue()
            .npc(Quest.ARIS, "Wally found Delrith at the stone circle south of Varrock.",
                 "He struck with Silverlight and recited an incantation.")
            .npc(Quest.ARIS, "Delrith is about to rise from that circle again.")
            .then { p ->
                Quest.advance(p, Quest.ARIS_SPOKEN_TO)
                arisAdvice(p)
            }
            .open()
    }

    private fun arisAdvice(player: Player) {
        player.newDialogue().npc(Quest.ARIS, "How can I help you stop Delrith?")
            .options(ASK_INCANTATION, { p ->
                p.newDialogue().npc(Quest.ARIS, "Say these words in this order:",
                    "$INCANTATION.").open()
            }, ASK_SWORD, { p ->
                p.newDialogue().npc(Quest.ARIS, "Silverlight was passed down through Wally's family.",
                    "Sir Prysin keeps it in Varrock Palace. Tell him I sent you.").open()
            }, "Where is Delrith?", { p ->
                p.newDialogue().npc(Quest.ARIS, "Look for the stone circle south of the city gates.").open()
            })
            .open()
    }

    fun prysin(player: Player, buySilverlight: (Player) -> Unit) {
        val dialogue = player.newDialogue()
        val hasSword = QuestRewards.owns(player, Quest.SILVERLIGHT)
        when (Quest.stage(player)) {
            Quest.ARIS_SPOKEN_TO -> {
                dialogue.npc(Quest.PRYSIN, "What brings you to the palace?")
                val introduction: (Player) -> Unit = { p ->
                    p.newDialogue()
                        .npc(Quest.PRYSIN, "If Aris has seen Delrith, I'd better help you.",
                            "Silverlight is locked in a box that needs three keys.")
                        .options(ASK_KEYS, { keys(it, true) }, "I'll return later.", ::close)
                        .open()
                }
                if (hasSword) dialogue.options(ARIS_SENT_ME, introduction, "Goodbye.", ::close)
                else dialogue.options(ARIS_SENT_ME, introduction, BUY_SWORD, buySilverlight, "Goodbye.", ::close)
            }
            in Quest.KEY_HUNT until Quest.SILVERLIGHT_RECLAIMED -> {
                dialogue.npc(Quest.PRYSIN, "How are you getting on with the three keys?")
                val options = mutableListOf<DialogueOption>()
                if (player.inventory.containsAll(*Quest.keys.toIntArray())) {
                    options += DialogueOption(GIVE_KEYS, ::giveKeys)
                }
                options += DialogueOption(ASK_KEYS) { p -> keys(p, false) }
                if (!hasSword) options += DialogueOption(BUY_SWORD, buySilverlight)
                options += DialogueOption("I'm still looking.", ::close)
                dialogue.options(options)
            }
            Quest.SILVERLIGHT_RECLAIMED -> {
                dialogue.npc(Quest.PRYSIN, "Have you dealt with Delrith yet?")
                if (hasSword) dialogue.npc(Quest.PRYSIN, "Take Silverlight to the stone circle south of Varrock.")
                else dialogue.options(LOST_SWORD, ::replaceSword, "I'm still working on it.", ::close)
            }
        }
        dialogue.open()
    }

    private fun keys(player: Player, start: Boolean) {
        player.newDialogue()
            .npc(Quest.PRYSIN, "Captain Rovin is upstairs in the palace guards' quarters.",
                "Traiborn has another key in the Wizards' Tower.")
            .npc(Quest.PRYSIN, "Mine fell into the kitchen drain. It leads to the sewer.")
            .then { if (start && Quest.stage(it) == Quest.ARIS_SPOKEN_TO) Quest.advance(it, Quest.KEY_HUNT) }
            .open()
    }

    private fun giveKeys(player: Player) {
        player.newDialogue().npc(Quest.PRYSIN, "Excellent. Now I can unlock the box.")
            .then { p ->
                if (Quest.exchangeKeys(p)) p.sendMessage("You hand over the three keys and Prysin opens the box.")
            }
            .open()
    }

    private fun replaceSword(player: Player) {
        player.newDialogue().npc(Quest.PRYSIN, "Someone returned the sword to me.", "Take better care of it this time.")
            .then { p ->
                if (Quest.stage(p) == Quest.SILVERLIGHT_RECLAIMED && !QuestRewards.owns(p, Quest.SILVERLIGHT)) {
                    QuestRewards.give(p, Quest.SILVERLIGHT)
                }
            }
            .open()
    }

    fun rovin(player: Player) {
        val dialogue = player.newDialogue().npc(Quest.ROVIN, "Only palace guards are allowed up here.")
        if (Quest.lookingForKeys(player) && !QuestRewards.owns(player, Quest.ROVIN_KEY)) {
            dialogue.options(TELL_ROVIN, { p ->
                p.newDialogue().npc(Quest.ROVIN, "What is so important?")
                    .options(WARN_ROVIN, ::rovinKey, "Never mind.", ::close).open()
            }, "I'll be leaving.", ::close)
        }
        dialogue.open()
    }

    private fun rovinKey(player: Player) {
        player.newDialogue()
            .npc(Quest.ROVIN, "I doubt my guards could face Delrith.")
            .npc(Quest.ROVIN, "If Prysin needs the key to Silverlight, here it is.")
            .then { p ->
                if (Quest.lookingForKeys(p) && !QuestRewards.owns(p, Quest.ROVIN_KEY) &&
                    QuestRewards.give(p, Quest.ROVIN_KEY)) p.sendMessage("Captain Rovin hands you a key.")
            }
            .open()
    }

    fun traiborn(player: Player) {
        val dialogue = player.newDialogue()
        val stage = Quest.stage(player)
        val bones = Quest.bonesRemaining(player)
        when {
            !Quest.lookingForKeys(player) || QuestRewards.owns(player, Quest.TRAIBORN_KEY) ->
                dialogue.npc(Quest.TRAIBORN, "Hello, young thingummywut.")
            stage == Quest.KEY_HUNT -> dialogue.npc(Quest.TRAIBORN, "Hello, young thingummywut.")
                .options(ASK_TRAIBORN, ::askTraiborn, "Goodbye.", ::close)
            bones == 0 -> dialogue.npc(Quest.TRAIBORN, "I have enough bones to summon the key.")
                .options(CLAIM_KEY, ::performRitual, "I'll come back.", ::close)
            else -> {
                dialogue.npc(Quest.TRAIBORN, "I need $bones more sets of bones for the ritual.")
                if (player.inventory.contains(Quest.BONES)) {
                    dialogue.options(OFFER_BONES, { p ->
                        Quest.offerBones(p) {
                            if (Quest.bonesRemaining(it) == 0) performRitual(it)
                            else it.newDialogue().npc(Quest.TRAIBORN, "I'll wait here while you find the rest.").open()
                        }
                    }, "I'll come back.", ::close)
                }
            }
        }
        dialogue.open()
    }

    private fun askTraiborn(player: Player) {
        player.newDialogue()
            .npc(Quest.TRAIBORN, "I remember a knight with a key. I put it somewhere safe.",
                "I'll need 25 sets of bones to bring it back.")
            .options(GET_BONES, { p ->
                if (Quest.stage(p) == Quest.KEY_HUNT) Quest.advance(p, Quest.FIND_BONES)
                close(p)
            }, "I'll think about it.", ::close)
            .open()
    }

    private fun performRitual(player: Player) {
        player.newDialogue()
            .npc(Quest.TRAIBORN, "Stand back while I cast the ritual.",
                "These bones should bring the key back to me.")
            .then { if (Quest.claimTraibornKey(it)) it.sendMessage("Traiborn hands you a key.") }
            .open()
    }

    internal fun incantation(player: Player, banish: (Player) -> Unit, retry: (Player) -> Unit) {
        val wrong: (Player) -> Unit = { p ->
            retry(p)
            p.newDialogue().text("The vortex collapses. That was the wrong incantation.").open()
        }
        player.newDialogue().player("Now what was that incantation again?")
            .options("Carlem Gabindo Purchai Zaree Camerinthum", wrong,
                "Purchai Zaree Gabindo Carlem Camerinthum", wrong,
                "Purchai Camerinthum Aber Gabindo Carlem", wrong,
                INCANTATION, { p ->
                    p.newDialogue()
                        .text("As you chant, the vortex pulls Delrith", "back to the dimension he came from.")
                        .then(banish)
                        .open()
                })
            .open()
    }

    private fun close(player: Player) {
        player.overlays.closeWindows()
    }
}
