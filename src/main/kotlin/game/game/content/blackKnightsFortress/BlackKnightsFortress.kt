/*
 * Adapted from LostCityRS/Content scripts/quests/quest_blackknight and area_falador/scripts/sir_amik_varze.rs2
 * at revision 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
 */
package game.content.blackKnightsFortress

import api.predef.ext.playJingle
import api.quest.QuestProgress
import game.player.Jingles
import game.player.QuestJournalInterface
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression.*
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.net.msg.out.WidgetColourMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter
import kotlin.random.Random

/**
 * Sir Amik's espionage assignment, from the briefing through the sabotage and payment.
 *
 * @author lare96
 */
object BlackKnightsFortress {
    const val SIR_AMIK = 608
    const val FORTRESS_GUARD = 609
    const val GRILL = 2342
    const val HOLE = 2336
    const val BRONZE_MED_HELM = 1139
    const val IRON_CHAINBODY = 1101
    const val CABBAGE = 1965
    const val DRAYNOR_CABBAGE = 1967
    const val JOURNAL_BUTTON = 7332

    const val ASSIGNED = 1
    const val OVERHEARD = 2
    const val SABOTAGED = 3
    const val COMPLETE = 4
    const val SEEK_QUEST = "I seek a quest!"
    const val LAUGH_AT_DANGER = "I laugh in the face of danger!"

    val sirAmikPosition = Position(2960, 3336, 2)
    val grillPosition = Position(3025, 3507)
    val holePosition = Position(3031, 3507, 1)

    private const val REQUIRED_POINTS = 12
    private const val CAPTAIN = 610
    private const val WITCH = 611
    private const val GRELDO = 612
    private const val COINS = 995
    private const val COIN_REWARD = 2_500
    private const val TITLE = "Black Knights' Fortress"
    private val progress = QuestProgress(PersistentVarp.BLACK_KNIGHTS_FORTRESS, COMPLETE, 3)

    fun stage(player: Player): Int = progress.stage(player)

    fun canStart(player: Player): Boolean = player.varpManager.getValue(PersistentVarp.QUEST_POINTS) >= REQUIRED_POINTS

    fun disguised(player: Player): Boolean =
        player.equipment[Equipment.HEAD]?.id == BRONZE_MED_HELM &&
            player.equipment[Equipment.CHEST]?.id == IRON_CHAINBODY

    fun refresh(player: Player) {
        val colour = when (stage(player)) {
            0 -> 0xff0000
            COMPLETE -> 0x00ff00
            else -> 0xffff00
        }
        player.queue(WidgetColourMessageWriter(JOURNAL_BUTTON, colour))
    }

    private fun advance(player: Player, expected: Int, next: Int) {
        if (stage(player) == expected && progress.advance(player, next)) refresh(player)
    }

    fun speakToSirAmik(player: Player, npc: Npc) {
        when (stage(player)) {
            0 -> player.newDialogue().npc(npc.id, QUIZZICAL, "I lead the White Knights of Falador.",
                "Why do you seek my audience?")
                .options(SEEK_QUEST, { offerQuest(it, npc) },
                    "I'm just looking around.", { it.newDialogue().npc(npc.id, "Don't break anything.").open() })
                .open()
            ASSIGNED -> player.newDialogue().npc(npc.id, "How's the mission going?")
                .player(SAD, "I haven't found out what their secret weapon is yet.").open()
            OVERHEARD -> player.newDialogue().npc(npc.id, "How's the mission going?")
                .player("The Black Knights are making a potion of invincibility.")
                .npc(npc.id, "If you can sabotage it, you will be paid well.").open()
            SABOTAGED -> player.newDialogue()
                .player(HAPPY, "I've ruined the Black Knights' invincibility potion.")
                .npc(npc.id, "The Black Knights have withdrawn their demands. That confirms your story.")
                .player(QUIZZICAL, "You said you were going to pay me.")
                .npc(npc.id, HAPPY, "Yes, that's right.")
                .then { if (nearSirAmik(it, npc)) complete(it) }.open()
            COMPLETE -> player.newDialogue().player(HAPPY, "Hello Sir Amik.")
                .npc(npc.id, HAPPY, "Hello, friend!").open()
        }
    }

    private fun offerQuest(player: Player, npc: Npc) {
        if (!nearSirAmik(player, npc) || stage(player) != 0) return
        if (!canStart(player)) {
            player.newDialogue().npc(npc.id, "I have a dangerous assignment. I need an experienced quester.")
                .text("You need at least $REQUIRED_POINTS quest points to start this quest.").open()
            return
        }
        player.newDialogue().npc(npc.id, "I need someone to spy on the Black Knights in their fortress.")
            .options(LAUGH_AT_DANGER, { brief(it, npc) },
                "I cower at the first sign of danger!", { cautious ->
                    cautious.newDialogue().npc(npc.id, "I suppose spies do need to hide now and then.")
                        .options("I'll give it a go.", { brief(it, npc) },
                            "No, I'm not convinced.", { it.newDialogue().player("No, I'm not convinced.").open() })
                        .open()
                }).open()
    }

    private fun brief(player: Player, npc: Npc) {
        if (!nearSirAmik(player, npc) || stage(player) != 0 || !canStart(player)) return
        player.newDialogue().npc(npc.id, "My knights are known to the Black Knights. We need someone they won't recognise.")
            .npc(npc.id, ANGRY, "They've demanded money and land. If we refuse, they threaten to invade Falador.")
            .npc(npc.id, "They claim to have a new secret weapon. Find out what it is and sabotage it.",
                "You will be well paid.")
            .player("All right, I'll give it a try.")
            .then { if (nearSirAmik(it, npc)) advance(it, 0, ASSIGNED) }.open()
    }

    private fun nearSirAmik(player: Player, npc: Npc): Boolean =
        player.isAlive && npc.state == EntityState.ACTIVE && npc.isAlive &&
            player.z == npc.z && player.position.isWithinDistance(npc.position, 8)

    fun speakToGuard(player: Player) {
        val dialogue = player.newDialogue()
        if (disguised(player)) {
            dialogue.npc(FORTRESS_GUARD, ANGRY, "Hey! Get back on duty!").player(CONFUSED, "Uh...")
        } else {
            dialogue.npc(FORTRESS_GUARD, ANGRY, "Get lost. This is private property.")
                .options("What's the guards' uniform?", {
                    it.newDialogue().npc(FORTRESS_GUARD,
                        "Iron chain mail and a medium bronze helmet. You can see me wearing it.").open()
                }, "I'll be going then.", { })
        }
        dialogue.open()
    }

    fun listen(player: Player) {
        if (stage(player) != ASSIGNED) {
            player.sendMessage("You can't hear much right now.")
            return
        }
        if (!disguised(player)) {
            player.sendMessage("You need to wear the guards' uniform before you can spy at the grill.")
            return
        }
        player.newDialogue().npc(CAPTAIN, QUIZZICAL, "How's the secret weapon coming along?")
            .npc(WITCH, HAPPY, "The invincibility potion is almost ready. It's taken me five years.")
            .npc(WITCH, "Greldo is fetching the last ingredient. It's a cabbage my cousin Helda grows at Draynor Manor.")
            .npc(WITCH, "The soil there is a little magical. It gives the cabbages magical properties.")
            .npc(WITCH, "Only a Draynor Manor cabbage will do, Greldo. An ordinary cabbage would ruin the potion!")
            .npc(GRELDO, "Yes, mithreth.")
            .then {
                if (it.isAlive && disguised(it) && it.z == grillPosition.z && it.position.isWithinDistance(grillPosition, 1)) {
                    advance(it, ASSIGNED, OVERHEARD)
                }
            }.open()
    }

    fun sabotage(player: Player, cabbage: Int) {
        if (stage(player) != OVERHEARD) {
            player.newDialogue().player(CONFUSED, "Why would I want to do that?").open()
            return
        }
        if (cabbage == DRAYNOR_CABBAGE) {
            player.sendMessage("That's the wrong sort of cabbage. A Draynor Manor cabbage would help the witch.")
            return
        }
        if (!player.inventory.remove(Item(CABBAGE))) return
        advance(player, OVERHEARD, SABOTAGED)
        player.sendMessage("You drop a cabbage down the hole. It lands in the cauldron below.")
        player.sendMessage("The potion froths and bubbles. You hear the witch groan in dismay.")
        player.newDialogue().player(QUIZZICAL, "That should put an end to their secret weapon.").open()
    }

    private fun complete(player: Player) {
        if (stage(player) != SABOTAGED) return
        val reward = Item(COINS, COIN_REWARD)
        if (!player.inventory.hasSpaceFor(reward) ||
            player.inventory.computeAmountForId(COINS) > Int.MAX_VALUE - COIN_REWARD) {
            player.sendMessage("Make room for Sir Amik's 2,500 coins, then speak to him again.")
            return
        }
        if (!progress.complete(player, SABOTAGED)) return

        check(player.inventory.add(reward))
        refresh(player)
        player.playJingle(when (Random.nextInt(4)) {
            0 -> Jingles.QUEST_COMPLETE_3
            1 -> Jingles.QUEST_COMPLETE_2
            else -> Jingles.QUEST_COMPLETE_1
        })
        player.sendMessage("Sir Amik hands you 2,500 coins.")
        player.sendMessage("Congratulations! Quest complete!")
        player.overlays.open(object : StandardInterface(12140) {
            override fun onOpen(plr: Player) {
                plr.sendText("You have completed the\\nBlack Knights' Fortress Quest!", 12144)
                plr.queue(WidgetItemModelMessageWriter(12145, 250, COINS))
                plr.sendText("${progress.questPoints} Quest Points", 12150)
                plr.sendText("2,500 Coins", 12151)
                for (id in 12152..12155) plr.sendText("", id)
            }
        })
    }

    /**
     * Shows the objective for the player's saved stage on the native quest journal.
     */
    fun journal(player: Player) {
        val journal = QuestJournalInterface(TITLE)
        val lines = when (stage(player)) {
            0 -> listOf("@dbl@I can start by speaking to @dre@Sir Amik Varze@dbl@ at the",
                "@dre@White Knights' Castle@dbl@ in @dre@Falador@dbl@.",
                if (canStart(player)) "@str@I have at least $REQUIRED_POINTS Quest Points."
                else "@dbl@I need at least @dre@$REQUIRED_POINTS@dbl@ Quest Points.")
            ASSIGNED -> listOf("@dre@Sir Amik Varze@dbl@ asked me to investigate the",
                "@dre@Black Knights' Fortress@dbl@ near @dre@Ice Mountain@dbl@.",
                "@dbl@I should wear the guards' uniform to spy at the grill.",
                "@dbl@It consists of a @dre@bronze med helm@dbl@ and an @dre@iron chainbody@dbl@.")
            OVERHEARD -> listOf("@str@I disguised myself as a guard and listened at the grill.",
                "@dbl@The Black Knights are making an invincibility potion.",
                "@dbl@I can ruin it with an ordinary @dre@cabbage@dbl@.",
                "@dbl@I should find the hole above the witch's cauldron.")
            SABOTAGED -> listOf("@str@I overheard the witch and ruined the potion with a cabbage.",
                "@dbl@I should return to @dre@Sir Amik Varze@dbl@ in @dre@White Knights' Castle@dbl@.")
            else -> listOf("@str@I spied on the Black Knights in a guard's uniform.",
                "@str@I ruined the witch's potion and claimed my reward.", "",
                "@red@QUEST COMPLETE!", "@dbl@${progress.questPoints} Quest Points", "@dbl@2,500 Coins")
        }
        lines.forEach(journal::addLine)
        player.overlays.open(journal)
    }
}
