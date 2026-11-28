/* Mechanics adapted from LostCityRS/Content quest_blackarmgang at 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50. */
package game.content.shieldOfArrav

import api.predef.ext.playJingle
import game.content.questRewards.QuestRewards.owns
import api.quest.QuestProgress
import game.obj.entrances.Entrances
import game.player.Jingles
import game.player.QuestJournalInterface
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.`object`.GameObject
import io.luna.net.msg.out.WidgetColourMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * The two native Shield of Arrav stories, with shared museum and royal handovers.
 *
 * @author lare96
 */
object ShieldOfArrav {
    const val RELDO = 647
    const val RELDO_PORTRAIT = 2661
    const val BARAEK = 547
    const val TRAMP = 641
    const val KATRINE = 642
    const val STRAVEN = 644
    const val JONNY = 645
    const val CURATOR = 646
    const val ROALD = 648
    const val BOOKCASE = 2402
    const val PHOENIX_CHEST = 2403
    const val PHOENIX_CHEST_OPEN = 2404
    const val BLACK_ARM_CUPBOARD = 2400
    const val BLACK_ARM_CUPBOARD_OPEN = 2401
    const val WEAPONS_STORE_DOOR = 2398
    const val BOOK = 757
    const val STORE_KEY = 759
    const val REPORT = 761
    const val PHOENIX_HALF = 763
    const val BLACK_ARM_HALF = 765
    const val CROSSBOW = 767
    const val CERTIFICATE = 769
    const val COINS = 995
    const val JOURNAL_BUTTON = 7345
    const val READ_BOOK = 2
    const val ASKED_RELDO = 3
    const val PAID_BARAEK = 4
    const val ASSIGNED_REPORT = 8
    const val PHOENIX_JOINED = 9
    const val PHOENIX_COMPLETE = 10
    const val ASSIGNED_CROSSBOWS = 2
    const val BLACK_ARM_JOINED = 3
    const val BLACK_ARM_COMPLETE = 4
    const val ASK_QUEST = "I'm looking for a quest."
    const val ASK_GANG = "Do you know where the Phoenix Gang is?"
    const val PAY_BARAEK = "I'll pay 20 coins."
    const val JOIN_PHOENIX = "I'd like to join the Phoenix Gang."
    const val FIND_ALLEY = "What's down the alley?"
    const val JOIN_BLACK_ARM = "I'd like to join the Black Arm Gang."
    const val BRIBE = 20
    private const val REWARD = 600
    private val phoenix = QuestProgress(PersistentVarp.SHIELD_OF_ARRAV_PHOENIX, PHOENIX_COMPLETE, 1)
    private val blackArm = QuestProgress(PersistentVarp.SHIELD_OF_ARRAV_BLACK_ARM, BLACK_ARM_COMPLETE, 1)

    fun phoenixStage(player: Player): Int = phoenix.stage(player)

    fun blackArmStage(player: Player): Int = blackArm.stage(player)

    fun completed(player: Player): Boolean =
        phoenixStage(player) == PHOENIX_COMPLETE || blackArmStage(player) == BLACK_ARM_COMPLETE

    fun refresh(player: Player) {
        val colour = when {
            completed(player) -> 0x00ff00
            phoenixStage(player) > 0 || blackArmStage(player) > 0 -> 0xffff00
            else -> 0xff0000
        }
        player.queue(WidgetColourMessageWriter(JOURNAL_BUTTON, colour))
    }

    private fun advance(player: Player, progress: QuestProgress, from: Int, to: Int) {
        if (progress.stage(player) == from && progress.advance(player, to)) refresh(player)
    }

    private fun nearby(player: Player, npc: Npc): Boolean =
        player.isAlive && !player.isLocked && npc.state == EntityState.ACTIVE &&
            npc.isAlive && player.z == npc.z && player.position.isWithinDistance(npc.position, 3)

    fun reldo(player: Player, npc: Npc) {
        val dialogue = player.newDialogue()
        when (phoenixStage(player)) {
            0 -> dialogue.npc(RELDO_PORTRAIT, "Can I help you find something?")
                .options(ASK_QUEST, {
                    it.newDialogue().npc(RELDO_PORTRAIT, "Try the book about the Shield of Arrav.",
                        "One of the cases along the west wall should hold it.")
                        .then { reader -> if (nearby(reader, npc)) advance(reader, phoenix, 0, 1) }.open()
                }, "Just looking.", {})
            1 -> dialogue.npc(RELDO_PORTRAIT, "Look in the bookcase along the west wall.")
            READ_BOOK -> dialogue.npc(RELDO_PORTRAIT, "I don't know where the thieves went.",
                "Ask Baraek, the fur trader in the market.")
                .then { if (nearby(it, npc)) advance(it, phoenix, READ_BOOK, ASKED_RELDO) }
            else -> dialogue.npc(RELDO_PORTRAIT, "Baraek may know more about the Phoenix Gang.")
        }
        dialogue.open()
    }

    fun searchBookcase(player: Player) {
        if (phoenixStage(player) == 1 && !owns(player, BOOK)) {
            if (player.inventory.add(BOOK)) player.sendMessage("You find a book about the Shield of Arrav.")
            else player.sendMessage("You need room for the book.")
        } else player.sendMessage("The bookcase holds a collection of old books.")
    }

    fun readBook(player: Player) {
        val journal = QuestJournalInterface("The Shield of Arrav")
        journal.addLine("Arrav was a hero of Avarrocka. His shield was kept in the")
        journal.addLine("city for generations until thieves stole it from the museum.")
        journal.addLine("The Phoenix Gang took it. A feud split the gang and left")
        journal.addLine("the gang's former allies in possession of half the shield.")
        journal.addLine("The king offered a reward for the shield's safe return.")
        advance(player, phoenix, 1, READ_BOOK)
        player.overlays.open(journal)
    }

    fun baraek(player: Player, npc: Npc) {
        val dialogue = player.newDialogue().npc(BARAEK, "Interested in buying or selling furs?")
        if (phoenixStage(player) == ASKED_RELDO && blackArmStage(player) < BLACK_ARM_JOINED) {
            dialogue.options(ASK_GANG, {
                it.newDialogue().npc(BARAEK, "That information could get me into trouble. It will cost you $BRIBE coins.")
                    .options(PAY_BARAEK, { buyer ->
                        if (nearby(buyer, npc) && phoenixStage(buyer) == ASKED_RELDO) {
                            if (buyer.inventory.remove(Item(COINS, BRIBE))) {
                                advance(buyer, phoenix, ASKED_RELDO, PAID_BARAEK)
                                buyer.newDialogue().npc(BARAEK, "Look for the VTAM Corporation down the ladder",
                                    "in the alley east of the Blue Moon Inn.").open()
                            } else buyer.newDialogue().npc(BARAEK, "Come back when you have $BRIBE coins.").open()
                        }
                    }, "I'll keep my money.", {}).open()
            }, "No, thanks.", {})
        }
        dialogue.open()
    }

    fun tramp(player: Player, npc: Npc) {
        player.newDialogue().npc(TRAMP, "Can you spare a moment?")
            .options(FIND_ALLEY, {
                it.newDialogue().npc(TRAMP, "The Black Arm Gang keeps its headquarters down there.",
                    "Speak to Katrine if you're looking for work.")
                    .then { listener ->
                        if (nearby(listener, npc) && phoenixStage(listener) < PHOENIX_JOINED) {
                            advance(listener, blackArm, 0, 1)
                        }
                    }.open()
            }, "I've got to go.", {}).open()
    }

    fun katrine(player: Player, npc: Npc) {
        val dialogue = player.newDialogue()
        when {
            phoenixStage(player) >= PHOENIX_JOINED -> dialogue.npc(KATRINE, "The Phoenix Gang isn't welcome here.")
            blackArmStage(player) == 1 -> dialogue.npc(KATRINE, "What do you want from the Black Arm Gang?")
                .options(JOIN_BLACK_ARM, {
                    it.newDialogue().npc(KATRINE, "Steal two Phoenix crossbows from their weapons store.",
                        "Bring them to me and I'll know you can be trusted.")
                        .then { recruit ->
                            if (nearby(recruit, npc) && phoenixStage(recruit) < PHOENIX_JOINED) {
                                advance(recruit, blackArm, 1, ASSIGNED_CROSSBOWS)
                            }
                        }.open()
                }, "Nothing.", {})
            blackArmStage(player) == ASSIGNED_CROSSBOWS -> {
                if (player.inventory.computeAmountForId(CROSSBOW) >= 2) {
                    dialogue.npc(KATRINE, "Two Phoenix crossbows. Well done. Welcome to the Black Arm Gang.")
                        .then {
                            if (nearby(it, npc) && blackArmStage(it) == ASSIGNED_CROSSBOWS &&
                                phoenixStage(it) < PHOENIX_JOINED && it.inventory.remove(Item(CROSSBOW, 2))) {
                                advance(it, blackArm, ASSIGNED_CROSSBOWS, BLACK_ARM_JOINED)
                            }
                        }
                } else dialogue.npc(KATRINE, "I still need two crossbows from the Phoenix weapons store.")
            }
            blackArmStage(player) >= BLACK_ARM_JOINED -> dialogue.npc(KATRINE, "Good to see you again.")
            else -> dialogue.npc(KATRINE, "Who sent you?")
        }
        dialogue.open()
    }

    fun straven(player: Player, npc: Npc) {
        val dialogue = player.newDialogue()
        when {
            blackArmStage(player) >= BLACK_ARM_JOINED -> dialogue.npc(STRAVEN, "The Black Arm Gang isn't welcome here.")
            phoenixStage(player) == PAID_BARAEK -> dialogue.npc(STRAVEN, "This is the VTAM Corporation. What do you want?")
                .options(JOIN_PHOENIX, {
                    it.newDialogue().npc(STRAVEN, "Prove your loyalty first. Jonny the Beard is at the Blue Moon Inn.",
                        "Defeat him and bring me his intelligence report.")
                        .then { recruit ->
                            if (nearby(recruit, npc) && blackArmStage(recruit) < BLACK_ARM_JOINED) {
                                advance(recruit, phoenix, PAID_BARAEK, ASSIGNED_REPORT)
                            }
                        }.open()
                }, "Wrong place. Sorry.", {})
            phoenixStage(player) == ASSIGNED_REPORT -> {
                if (player.inventory.contains(REPORT)) {
                    dialogue.npc(STRAVEN, "That's the report I wanted. Welcome to the Phoenix Gang.",
                        "Take this key to our weapons store.")
                        .then {
                            if (nearby(it, npc) && phoenixStage(it) == ASSIGNED_REPORT &&
                                blackArmStage(it) < BLACK_ARM_JOINED && it.inventory.remove(REPORT)) {
                                it.inventory.add(STORE_KEY)
                                advance(it, phoenix, ASSIGNED_REPORT, PHOENIX_JOINED)
                            }
                        }
                } else dialogue.npc(STRAVEN, "Jonny should be in the Blue Moon Inn. Bring back his report.")
            }
            phoenixStage(player) >= PHOENIX_JOINED -> {
                dialogue.npc(STRAVEN, "Welcome back to headquarters.")
                if (!owns(player, STORE_KEY)) {
                    dialogue.then {
                        if (nearby(it, npc) && !owns(it, STORE_KEY)) it.inventory.add(STORE_KEY)
                    }
                }
            }
            else -> dialogue.npc(STRAVEN, "Only authorised VTAM personnel are allowed here.")
        }
        dialogue.open()
    }

    fun open(objectAtTile: GameObject, openId: Int) {
        Entrances.replace(objectAtTile, openId, 20)
    }

    fun searchHalf(player: Player, phoenixSide: Boolean) {
        val id = if (phoenixSide) PHOENIX_HALF else BLACK_ARM_HALF
        val joined = if (phoenixSide) phoenixStage(player) == PHOENIX_JOINED
                     else blackArmStage(player) == BLACK_ARM_JOINED
        if (!joined || owns(player, id) || owns(player, CERTIFICATE)) {
            player.sendMessage("You find nothing else of interest.")
        } else if (player.inventory.add(id)) player.sendMessage("You find half of the Shield of Arrav.")
        else player.sendMessage("You need room for the broken shield.")
    }

    fun curator(player: Player, npc: Npc) {
        val dialogue = player.newDialogue().npc(CURATOR, "Welcome to the Varrock Museum.")
        if (player.inventory.containsAll(PHOENIX_HALF, BLACK_ARM_HALF) && joined(player)) {
            dialogue.npc(CURATOR, "These pieces fit together. You found Arrav's shield!",
                "I'll write a certificate for you and one for the person who helped you.")
                .then {
                    if (nearby(it, npc) && joined(it) && it.inventory.containsAll(PHOENIX_HALF, BLACK_ARM_HALF) &&
                        !owns(it, CERTIFICATE)) {
                        it.inventory.remove(PHOENIX_HALF)
                        it.inventory.remove(BLACK_ARM_HALF)
                        it.inventory.add(Item(CERTIFICATE, 2))
                        it.sendMessage("The curator gives you two certificates. The king will honour them.")
                    }
                }
        } else if (player.inventory.contains(PHOENIX_HALF) || player.inventory.contains(BLACK_ARM_HALF)) {
            dialogue.npc(CURATOR, "That looks like part of an old shield. Can you find the other half?")
        }
        dialogue.open()
    }

    private fun joined(player: Player): Boolean =
        !completed(player) && (phoenixStage(player) == PHOENIX_JOINED || blackArmStage(player) == BLACK_ARM_JOINED)

    fun roald(player: Player, npc: Npc) {
        val dialogue = player.newDialogue()
        when {
            completed(player) -> dialogue.npc(ROALD, "You've already claimed your share of the reward.")
            joined(player) && player.inventory.contains(CERTIFICATE) ->
                dialogue.npc(ROALD, "My father offered a reward for that shield.",
                    "The certificate entitles you to 600 coins.")
                    .then { finish(it, npc) }
            else -> dialogue.npc(ROALD, "The museum curator can verify the Shield of Arrav if you find it.")
        }
        dialogue.open()
    }

    private fun finish(player: Player, npc: Npc) {
        if (!nearby(player, npc) || !joined(player) || !player.inventory.contains(CERTIFICATE)) return
        val reward = Item(COINS, REWARD)
        val certificate = player.inventory[player.inventory.computeIndexForId(CERTIFICATE)] ?: return
        if (player.inventory.computeAmountForId(COINS) > Int.MAX_VALUE - REWARD ||
            (certificate.amount > 1 && !player.inventory.hasSpaceFor(reward))) {
            player.sendMessage("You need room to carry the reward.")
            return
        }
        val changed = if (phoenixStage(player) == PHOENIX_JOINED) phoenix.complete(player, PHOENIX_JOINED)
                      else blackArm.complete(player, BLACK_ARM_JOINED)
        if (!changed) return
        player.inventory.remove(CERTIFICATE)
        player.inventory.add(reward)
        refresh(player)
        player.playJingle(Jingles.QUEST_COMPLETE_1)
        player.overlays.open(object : StandardInterface(12140) {
            override fun onOpen(plr: Player) {
                plr.sendText("You have completed the\\nShield of Arrav Quest!", 12144)
                plr.queue(WidgetItemModelMessageWriter(12145, 250, COINS))
                plr.sendText("1 Quest Point", 12150)
                plr.sendText("600 Coins", 12151)
                for (id in 12152..12155) plr.sendText("", id)
            }
        })
    }

    fun journal(player: Player) {
        val journal = QuestJournalInterface("Shield of Arrav")
        val instruction = when {
            completed(player) -> "@red@QUEST COMPLETE!"
            owns(player, CERTIFICATE) -> "I can claim my reward from King Roald with the certificate."
            joined(player) -> "I need both shield halves for the Varrock Museum curator."
            blackArmStage(player) == ASSIGNED_CROSSBOWS -> "Katrine wants two crossbows from the Phoenix weapons store."
            blackArmStage(player) == 1 -> "The tramp told me to speak to Katrine down the alley."
            phoenixStage(player) == ASSIGNED_REPORT -> "I need Jonny the Beard's report from the Blue Moon Inn."
            phoenixStage(player) == PAID_BARAEK -> "The Phoenix Gang poses as the VTAM Corporation."
            phoenixStage(player) == ASKED_RELDO -> "Baraek in Varrock Square might know where to find the gang."
            phoenixStage(player) == READ_BOOK -> "I should tell Reldo what I learned about the shield."
            phoenixStage(player) == 1 -> "Reldo sent me to look for a book about the shield."
            else -> "I can start by speaking to Reldo in the Varrock Palace library."
        }
        journal.addLine(instruction)
        if (completed(player)) {
            journal.addLine("1 Quest Point")
            journal.addLine("600 Coins")
        }
        player.overlays.open(journal)
    }
}
