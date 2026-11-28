/* Mechanics adapted from LostCityRS/Content quest_hero at 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50. */
package game.content.heroesQuest

import api.predef.ext.playJingle
import game.content.questRewards.QuestRewards.owns
import api.quest.QuestProgress
import game.content.dragonSlayer.DragonSlayer
import game.content.lostCity.LostCity
import game.content.merlinsCrystal.MerlinsCrystal
import game.content.shieldOfArrav.ShieldOfArrav
import game.player.Jingles
import game.player.QuestJournalInterface
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.net.msg.out.WidgetColourMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * The saved Heroes' Quest stages, proof handover, and revision-377 quest interface.
 *
 * @author lare96
 */
object HeroesQuest {
    const val ACHIETTIES = 796
    const val FIRE_BIRD = 139
    const val ICE_QUEEN = 795
    const val ICE_GLOVES = 1580
    const val FIRE_FEATHER = 1583
    const val RAW_LAVA_EEL = 2148
    const val LAVA_EEL = 2149
    const val ARMBAND = 1579
    const val JOURNAL_BUTTON = 7363
    const val APPLY = "What do I need to become a hero?"
    const val OFFER_PROOFS = "I brought the three proofs."
    const val NOT_STARTED = 0
    const val STARTED = 1
    const val PHOENIX_BRIEFED = 2
    const val PHOENIX_SPOKE_TO_ALFONSE = 3
    const val PHOENIX_SPOKE_TO_CHARLIE = 4
    const val PHOENIX_KILLED_GRIP = 5
    const val PHOENIX_OBTAINED_ARMBAND = 6
    const val BLACK_ARM_BRIEFED = 7
    const val BLACK_ARM_GAVE_PASSWORD = 8
    const val BLACK_ARM_OBTAINED_PAPERS = 9
    const val BLACK_ARM_PASSED_DISGUISE = 10
    const val BLACK_ARM_GAVE_PAPERS = 11
    const val BLACK_ARM_LOOTED_CHEST = 12
    const val BLACK_ARM_OBTAINED_ARMBAND = 13
    const val COMPLETE = 15

    private const val TITLE = "Heroes' Quest"
    private const val REWARD_MODEL = 1377
    private const val REQUIRED_POINTS = 55
    private val progress = QuestProgress(PersistentVarp.HEROES_QUEST, COMPLETE, 1)
    private val proofs = intArrayOf(FIRE_FEATHER, LAVA_EEL, ARMBAND)
    internal val experience = mapOf(
        Skill.ATTACK to 3_075.0, Skill.DEFENCE to 3_075.0, Skill.STRENGTH to 3_075.0,
        Skill.HITPOINTS to 3_075.0, Skill.RANGED to 2_075.0, Skill.FISHING to 2_725.0,
        Skill.COOKING to 2_825.0, Skill.WOODCUTTING to 1_575.0, Skill.FIREMAKING to 1_575.0,
        Skill.SMITHING to 2_275.0, Skill.MINING to 2_575.0, Skill.HERBLORE to 1_325.0
    )

    fun stage(player: Player): Int = progress.stage(player)

    fun canStart(player: Player): Boolean =
        player.varpManager.getValue(PersistentVarp.QUEST_POINTS) >= REQUIRED_POINTS &&
            DragonSlayer.stage(player) == DragonSlayer.COMPLETE && LostCity.stage(player) == LostCity.COMPLETE &&
            MerlinsCrystal.stage(player) == MerlinsCrystal.COMPLETE && ShieldOfArrav.completed(player)

    fun isInProgress(player: Player): Boolean = stage(player) in STARTED until COMPLETE

    fun hasProofs(player: Player): Boolean = player.inventory.containsAll(*proofs)

    fun achietties(player: Player, npc: Npc) {
        val dialogue = player.newDialogue().npc(ACHIETTIES, "Welcome to the Heroes' Guild.")
        when {
            stage(player) == NOT_STARTED -> dialogue.options(APPLY, {
                if (!canStart(it)) {
                    it.newDialogue().npc(ACHIETTIES, "I can give this assignment to adventurers with 55 quest points.",
                        "Finish Shield of Arrav, Lost City, Merlin's Crystal and Dragon Slayer first.").open()
                } else {
                    it.newDialogue().npc(ACHIETTIES, "Bring me a firebird feather from Entrana, a cooked lava eel,",
                        "and an armband from a master thief.")
                        .then { recruit ->
                            if (nearby(recruit, npc) && canStart(recruit)) advance(recruit, NOT_STARTED, STARTED)
                        }.open()
                }
            }, "I'm just visiting.", {})
            stage(player) == COMPLETE -> dialogue.npc(ACHIETTIES, "Good to see you, hero.")
            hasProofs(player) -> dialogue.options(OFFER_PROOFS, {
                it.newDialogue().npc(ACHIETTIES, "The feather, the eel and the armband. You've earned the title of hero.")
                    .then { hero -> finish(hero, npc) }.open()
            }, "I need a moment.", {})
            else -> dialogue.npc(ACHIETTIES, "I'm still waiting for the three proofs.",
                "Check your journal if you need directions.")
        }
        dialogue.open()
    }

    internal fun advance(player: Player, expected: Int, next: Int): Boolean {
        if (stage(player) != expected || !progress.advance(player, next)) return false
        refresh(player)
        return true
    }

    internal fun nearby(player: Player, npc: Npc): Boolean =
        player.state == EntityState.ACTIVE && player.isAlive && !player.isLocked &&
            npc.state == EntityState.ACTIVE && npc.isAlive && player.z == npc.z &&
            player.position.isWithinDistance(npc.position, 3)

    fun refresh(player: Player) {
        val colour = when (stage(player)) {
            NOT_STARTED -> 0xff0000
            COMPLETE -> 0x00ff00
            else -> 0xffff00
        }
        player.queue(WidgetColourMessageWriter(JOURNAL_BUTTON, colour))
    }

    internal fun finish(player: Player, achietties: Npc) {
        val current = stage(player)
        if (achietties.id != ACHIETTIES || !nearby(player, achietties) ||
            current !in STARTED until COMPLETE || !hasProofs(player) || !progress.complete(player, current)) return

        proofs.forEach { player.inventory.remove(it) }
        experience.forEach { (skill, reward) -> player.skill(skill).experience += reward }
        refresh(player)
        player.playJingle(Jingles.QUEST_COMPLETE_1)
        player.overlays.open(object : StandardInterface(12140) {
            override fun onOpen(plr: Player) {
                plr.sendText("You have completed\\nHeroes' Quest!", 12144)
                plr.queue(WidgetItemModelMessageWriter(12145, 250, REWARD_MODEL))
                plr.sendText("1 Quest Point", 12150)
                plr.sendText("Experience in twelve skills", 12151)
                for (id in 12152..12155) plr.sendText("", id)
            }
        })
    }

    fun journal(player: Player) {
        val journal = QuestJournalInterface(TITLE)
        val current = stage(player)
        when (current) {
            NOT_STARTED -> {
                journal.addLine("@dbl@I can speak to @dre@Achietties@dbl@ outside the Heroes' Guild")
                journal.addLine("@dbl@in Burthorpe to ask about the quest.")
                journal.newLine()
                journal.addLine("@dbl@I need 55 quest points and must have finished Dragon Slayer,")
                journal.addLine("@dbl@Lost City, Merlin's Crystal and Shield of Arrav.")
            }
            COMPLETE -> {
                journal.addLine("@str@I gave Achietties the fire feather, lava eel and armband.")
                journal.addLine("@str@I earned recognition as a hero.")
                journal.newLine()
                journal.addLine("@red@QUEST COMPLETE!")
                journal.addLine("1 Quest Point")
                journal.addLine("Experience in twelve skills")
            }
            else -> {
                journal.addLine("@str@Achietties asked me to prove myself with three items.")
                journal.newLine()
                proof(journal, player, FIRE_FEATHER, "An Entranan firebird feather")
                if (!owns(player, FIRE_FEATHER)) {
                    journal.addLine("@dbl@Ice gloves from the Ice Queen will protect me from the heat.")
                    journal.addLine("@dbl@The Ice Queen lives under White Wolf Mountain.")
                }
                journal.newLine()
                proof(journal, player, LAVA_EEL, "A cooked lava eel")
                if (!owns(player, LAVA_EEL)) {
                    journal.addLine("@dbl@Gerrant in Port Sarim knows how to prepare an oily fishing rod.")
                    journal.addLine("@dbl@I can bait lava eels in Taverley Dungeon and cook one.")
                }
                journal.newLine()
                proof(journal, player, ARMBAND, "A master thief's armband")
                when (current) {
                    STARTED -> journal.addLine("@dbl@I should ask my gang contact in Varrock about the armband.")
                    PHOENIX_BRIEFED -> journal.addLine("@dbl@I need to give Alfonse in Brimhaven the gang password.")
                    PHOENIX_SPOKE_TO_ALFONSE -> journal.addLine("@dbl@Alfonse sent me to Charlie in the kitchen.")
                    PHOENIX_SPOKE_TO_CHARLIE -> journal.addLine("@dbl@A Black Arm partner can help me ambush Grip.")
                    PHOENIX_KILLED_GRIP -> journal.addLine("@dbl@My partner can get a candlestick for Straven.")
                    BLACK_ARM_BRIEFED -> journal.addLine("@dbl@I need to give Grubor the password in Brimhaven.")
                    BLACK_ARM_GAVE_PASSWORD -> journal.addLine("@dbl@I should ask Trobert how to get into Pete's mansion.")
                    BLACK_ARM_OBTAINED_PAPERS -> journal.addLine("@dbl@I need to present myself to Garv in black armour.")
                    BLACK_ARM_PASSED_DISGUISE -> journal.addLine("@dbl@I should report to Grip and hand over the papers.")
                    BLACK_ARM_GAVE_PAPERS -> {
                        journal.addLine("@dbl@I need a Phoenix partner to ambush Grip for his key.")
                        journal.addLine("@dbl@Pete keeps the candlesticks in his treasure room.")
                    }
                    BLACK_ARM_LOOTED_CHEST -> journal.addLine("@dbl@I should share the loot and take a candlestick to Katrine.")
                    PHOENIX_OBTAINED_ARMBAND, BLACK_ARM_OBTAINED_ARMBAND ->
                        if (!owns(player, ARMBAND)) {
                            journal.addLine("@dbl@My gang contact can replace the armband.")
                        }
                }
                if (proofs.all { owns(player, it) }) {
                    journal.newLine()
                    journal.addLine("@dbl@I should bring all three proofs to Achietties.")
                }
            }
        }
        player.overlays.open(journal)
    }

    private fun proof(journal: QuestJournalInterface, player: Player, id: Int, name: String) {
        val marker = if (owns(player, id)) "@str@" else "@dbl@"
        journal.addLine("$marker$name")
    }
}
