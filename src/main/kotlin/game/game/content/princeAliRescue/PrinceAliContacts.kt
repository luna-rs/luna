package game.content.princeAliRescue

import game.content.princeAliRescue.PrinceAliRescue as Quest
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player

/**
 * Hassan's assignment, Osman's briefing and the key hand-off through Leela.
 *
 * @author lare96
 */
object PrinceAliContacts {
    const val OFFER_HELP = "Can I help you?"
    const val FIRST_TASK = "What is the first thing I must do?"
    const val SECOND_TASK = "What is the second thing you need?"
    const val GET_SUPPLIES = "I had better go find some things."
    const val REPLACE_KEY = "Here are 15 coins for a new key."
    private const val ASK_WATER = "It's too hot here. How do you stand it?"

    fun hassan(player: Player, npc: Npc) {
        val dialogue = player.newDialogue()
        when (Quest.stage(player)) {
            0 -> dialogue.npc(Quest.HASSAN, "Greetings. I am Hassan, chancellor to the Emir of Al Kharid.")
                .options(OFFER_HELP, { p ->
                    p.newDialogue().npc(Quest.HASSAN, "Yes, we could use your help. Speak to Osman, the spymaster.",
                        "Come back to me when the job is done and I will pay you.")
                        .then { if (Quest.nearby(it, npc)) Quest.advance(it, 0, Quest.STARTED) }.open()
                }, ASK_WATER, { water(it, npc) }, "Do you mind if I fight your warriors?", { p ->
                    p.newDialogue().npc(Quest.HASSAN, "You're welcome to try. The elite guard are harder to kill.").open()
                })
            Quest.STARTED -> dialogue.npc(Quest.HASSAN, "Have you found Osman? He has the details of the job.")
            Quest.ALI_RESCUED -> dialogue.npc(Quest.HASSAN, "The Emir is grateful that you rescued his son.",
                "I am authorised to pay you 700 coins.")
                .then { if (Quest.nearby(it, npc)) Quest.finish(it) }
            Quest.COMPLETE -> dialogue.npc(Quest.HASSAN, "You are a friend of Al Kharid. Thank you for rescuing the prince.")
            else -> dialogue.npc(Quest.HASSAN, "I understand the spymaster has hired you.",
                "I will pay you once the prince is safe.")
        }
        if (Quest.stage(player) != 0 && Quest.stage(player) != Quest.ALI_RESCUED) {
            dialogue.options(ASK_WATER, { water(it, npc) }, "I'll be on my way.", { })
        }
        dialogue.open()
    }

    private fun water(player: Player, npc: Npc) {
        player.newDialogue().npc(Quest.HASSAN, "We have plenty of water. Here, have a drink.")
            .then {
                val water = Item(Quest.JUG_OF_WATER)
                if (Quest.nearby(it, npc) && it.inventory.hasSpaceFor(water)) {
                    it.inventory.add(water)
                    it.newDialogue().give(water, "Hassan hands you a jug of water.", false).open()
                }
            }.open()
    }

    fun osman(player: Player, npc: Npc) {
        when (Quest.stage(player)) {
            0 -> player.newDialogue().npc(Quest.OSMAN, "Rumours about the prince? Take them to Hassan.").open()
            Quest.STARTED -> player.newDialogue().npc(Quest.OSMAN, "Lady Keli has taken Prince Ali captive.",
                "There are two things you need for his rescue.")
                .options(FIRST_TASK, { briefing(it, npc, true) }, SECOND_TASK, { briefing(it, npc, false) }).open()
            Quest.ALI_RESCUED -> player.newDialogue().npc(Quest.OSMAN, "The prince is safe. Hassan will pay you.").open()
            Quest.COMPLETE -> player.newDialogue().npc(Quest.OSMAN, "Well done. I shall remember your work.").open()
            else -> osmanKey(player, npc)
        }
    }

    private fun briefing(player: Player, npc: Npc, disguise: Boolean) {
        val dialogue = player.newDialogue()
        if (disguise) {
            dialogue.npc(Quest.OSMAN, "Only Lady Keli can move freely about the jail.",
                "Tie her up with rope and disguise the prince as her.",
                "You need a pink skirt, a blonde wig and something to colour his skin.")
        } else {
            dialogue.npc(Quest.OSMAN, "Keli keeps the only key on a chain around her neck.",
                "Get her to show it to you, then take an impression in soft clay.",
                "Bring me the impression and a bronze bar.")
        }
        dialogue.npc(Quest.OSMAN, "My daughter Leela found the jail. Look for her near Draynor Village.")
            .options(if (disguise) SECOND_TASK else FIRST_TASK, { briefing(it, npc, !disguise) },
                GET_SUPPLIES, { p ->
                    p.newDialogue().npc(Quest.OSMAN, "Good luck. You will need Leela's help.")
                        .then { if (Quest.nearby(it, npc)) Quest.advance(it, Quest.STARTED, Quest.BRIEFED) }.open()
                }).open()
    }

    private fun osmanKey(player: Player, npc: Npc) {
        val status = Quest.keyStatus(player)
        when {
            status == 0 && player.inventory.containsAll(Quest.KEY_PRINT, Quest.BRONZE_BAR) ->
                player.newDialogue().npc(Quest.OSMAN, "Good work. I can have a copy made from that impression.",
                    "Take the finished key from Leela.")
                    .then {
                        if (Quest.nearby(it, npc) && Quest.helping(it) && Quest.keyStatus(it) == 0 &&
                            it.inventory.containsAll(Quest.KEY_PRINT, Quest.BRONZE_BAR)) {
                            it.inventory.removeAll(listOf(Item(Quest.KEY_PRINT), Item(Quest.BRONZE_BAR)))
                            Quest.setKeyStatus(it, Quest.KEY_ORDERED)
                            it.sendMessage("Osman takes the key print and the bronze bar.")
                        }
                    }.open()
            status == Quest.KEY_COLLECTED && !Quest.hasKey(player) -> replacement(player, npc)
            status == Quest.KEY_ORDERED ->
                player.newDialogue().npc(Quest.OSMAN, "Your key is with Leela near Draynor.").open()
            status == Quest.KEY_COLLECTED ->
                player.newDialogue().npc(Quest.OSMAN, "You already have a key. Leela will tell you what to do next.").open()
            else -> player.newDialogue().npc(Quest.OSMAN, "Bring Keli's key impression and a bronze bar.",
                "Leela can tell you what you need for the disguise.").open()
        }
    }

    fun leela(player: Player, npc: Npc) {
        if (!Quest.helping(player)) {
            player.newDialogue().npc(Quest.LEELA, if (Quest.stage(player) >= Quest.ALI_RESCUED)
                "Thank you. The prince is safe with us." else "My business here is my own, adventurer.").open()
            return
        }
        when {
            Quest.keyStatus(player) == Quest.KEY_ORDERED ->
                player.newDialogue().npc(Quest.LEELA, "My father sent this key for you. Be careful with it.")
                    .then { p ->
                        if (Quest.nearby(p, npc) && Quest.helping(p) && Quest.keyStatus(p) == Quest.KEY_ORDERED) {
                            if (p.inventory.hasSpaceFor(Item(Quest.BRONZE_KEY))) {
                                p.inventory.add(Item(Quest.BRONZE_KEY))
                                Quest.setKeyStatus(p, Quest.KEY_COLLECTED)
                                leelaPlan(p, npc, "I have given you the duplicate key.")
                            } else {
                                p.sendMessage("Make room in your inventory for the key.")
                            }
                        }
                    }.open()
            Quest.keyStatus(player) == Quest.KEY_COLLECTED && !Quest.hasKey(player) -> replacement(player, npc)
            else -> leelaPlan(player, npc)
        }
    }

    private fun leelaPlan(player: Player, npc: Npc, preface: String? = null) {
        val dialogue = player.newDialogue()
        if (preface != null) dialogue.npc(Quest.LEELA, preface)
        when (Quest.stage(player)) {
            Quest.BRIEFED -> {
                if (Quest.hasDisguiseAndKey(player)) {
                    dialogue.npc(Quest.LEELA, "You have the equipment. Deal with Joe, the guard outside the cell.",
                        "Try talking to him to find a weakness.")
                        .then {
                            if (Quest.nearby(it, npc) && Quest.hasDisguiseAndKey(it)) {
                                Quest.advance(it, Quest.BRIEFED, Quest.PREPARED)
                            }
                        }
                } else {
                    dialogue.npc(Quest.LEELA, "Osman told me you'd come. What do you need to know?")
                        .options("What should I use for a disguise?", { p ->
                            p.newDialogue().npc(Quest.LEELA, "Get a pink skirt, a wig from Ned and skin paste from Aggie.",
                                "Dye the wig blonde. You will also need rope to tie Keli up.").open()
                        }, "How do I copy the key?", { p ->
                            p.newDialogue().npc(Quest.LEELA, "Get Keli to show you the key and press it into soft clay.",
                                "Take the imprint and some bronze to my father. I'll collect the copy for you.").open()
                        }, "What can I do about the guard?", { p ->
                            p.newDialogue().npc(Quest.LEELA, "Talk to him when you have the disguise and the key.").open()
                        }, "I'll get the equipment.", { })
                }
            }
            Quest.PREPARED -> dialogue.npc(Quest.LEELA, "Joe likes a drink. Three beers should be enough.",
                "Give them to him together. The effects wear off quickly.")
            Quest.JOE_DRUNK -> dialogue.npc(Quest.LEELA, "Now Joe is distracted, use your rope to tie up Keli.")
            Quest.KELI_TIED -> dialogue.npc(Quest.LEELA, "Unlock the cell and give the prince his disguise.")
        }
        dialogue.open()
    }

    private fun replacement(player: Player, npc: Npc) {
        val speaker = npc.id
        player.newDialogue().npc(speaker, "Lost your key? A replacement costs 15 coins.")
            .options(REPLACE_KEY, { p ->
                if (!Quest.nearby(p, npc) || !Quest.helping(p) || Quest.keyStatus(p) != Quest.KEY_COLLECTED ||
                    Quest.hasKey(p)) return@options
                val payment = Item(Quest.COINS, Quest.KEY_REPLACEMENT_PRICE)
                val inventory = p.inventory
                when {
                    !inventory.contains(payment) ->
                        p.newDialogue().npc(speaker, "Come back when you have 15 coins.").open()
                    speaker == Quest.LEELA && !inventory.hasSpaceFor(Item(Quest.BRONZE_KEY)) &&
                        inventory.computeAmountForId(Quest.COINS) != payment.amount ->
                        p.sendMessage("Make room in your inventory for the key.")
                    speaker == Quest.LEELA -> {
                        inventory.updateAll(listOf(Item(Quest.BRONZE_KEY)), listOf(payment))
                        p.newDialogue().npc(speaker, "Here is another key. Keep it safe.").open()
                    }
                    else -> {
                        inventory.remove(payment)
                        Quest.setKeyStatus(p, Quest.KEY_ORDERED)
                        p.newDialogue().npc(speaker, "Collect the replacement from Leela.").open()
                    }
                }
            }, "I'll come back later.", { }).open()
    }
}
