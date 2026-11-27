/*
 * Adapted from LostCityRS/Content scripts/quests/quest_romeojuliet and areas/area_varrock,
 * revision 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
 */
package game.content.romeoJuliet

import api.predef.ext.playJingle
import api.quest.QuestProgress
import game.player.Jingles
import game.player.QuestJournalInterface
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.mob.varp.Varbit
import io.luna.net.msg.out.WidgetColourMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter
import kotlin.random.Random

/**
 * Stores Romeo & Juliet's story stages and presents its journal and completion reward.
 *
 * @author lare96
 */
object RomeoJuliet {
    const val ROMEO = 639
    const val JULIET_SPAWN = 3323
    const val JULIET = 637
    const val FATHER_LAWRENCE = 640
    const val APOTHECARY = 638

    const val BERRIES = 753
    const val MESSAGE = 755
    const val POTION = 756
    const val JOURNAL_BUTTON = 7343

    const val NOT_STARTED = 0
    const val SPOKEN_ROMEO = 10
    const val SPOKEN_JULIET = 20
    const val PASSED_MESSAGE = 30
    const val SPOKEN_FATHER = 40
    const val SPOKEN_APOTHECARY = 50
    const val JULIET_CRYPT = 60
    const val COMPLETE = 100

    private const val JULIET_VISIBILITY = 1838
    private const val TITLE = "Romeo & Juliet"
    private val progress = QuestProgress(PersistentVarp.ROMEO_JULIET, COMPLETE, 5)

    fun stage(player: Player): Int = progress.stage(player)

    internal fun advance(player: Player, next: Int) {
        if (progress.advance(player, next)) refresh(player)
    }

    fun refresh(player: Player) {
        val stage = stage(player)
        player.sendVarbit(Varbit(JULIET_VISIBILITY, if (stage == JULIET_CRYPT) 1 else 0))
        val colour = when (stage) {
            NOT_STARTED -> 0xff0000
            COMPLETE -> 0x00ff00
            else -> 0xffff00
        }
        player.queue(WidgetColourMessageWriter(JOURNAL_BUTTON, colour))
    }

    internal fun finish(player: Player) {
        if (!progress.complete(player, JULIET_CRYPT)) return
        refresh(player)
        player.playJingle(when (Random.nextInt(4)) {
            0 -> Jingles.QUEST_COMPLETE_3
            1 -> Jingles.QUEST_COMPLETE_2
            else -> Jingles.QUEST_COMPLETE_1
        })
        player.sendMessage("Congratulations! Quest complete!")
        player.overlays.open(object : StandardInterface(12140) {
            override fun onOpen(plr: Player) {
                plr.sendText("You have completed the\\n$TITLE Quest!", 12144)
                plr.queue(WidgetItemModelMessageWriter(12145, 250, POTION))
                plr.sendText("${progress.questPoints} Quest Points", 12150)
                for (id in 12151..12155) plr.sendText("", id)
            }
        })
    }

    fun journal(player: Player) {
        val stage = stage(player)
        val journal = QuestJournalInterface(TITLE)
        if (stage >= SPOKEN_ROMEO) {
            journal.addLine("@str@I agreed to help Romeo and Juliet get in touch.")
        }
        if (stage >= SPOKEN_JULIET) journal.addLine("@str@Juliet gave me a message for Romeo.")
        if (stage >= PASSED_MESSAGE) {
            journal.addLine("@str@I delivered Juliet's message. Her father opposes the marriage.")
            journal.addLine("@str@Romeo asked me to consult Father Lawrence.")
        }
        if (stage >= SPOKEN_FATHER) {
            journal.addLine("@str@Father Lawrence suggested a potion to make Juliet seem dead.")
            journal.addLine("@str@He sent me to the Apothecary to have it made.")
        }
        if (stage >= SPOKEN_APOTHECARY) {
            journal.addLine("@str@The Apothecary asked me to find some Cadava berries.")
        }
        if (stage >= JULIET_CRYPT) {
            journal.addLine("@str@I gave Juliet the potion. She asked me to explain the plan to Romeo.")
        }
        val next = when (stage) {
            NOT_STARTED -> listOf("@dbl@I can start by talking to @dre@Romeo@dbl@ in @dre@Varrock Square@dbl@.")
            SPOKEN_ROMEO -> listOf("@dbl@I should find @dre@Juliet@dbl@ upstairs in the house west of Varrock.")
            SPOKEN_JULIET -> listOf(when {
                player.inventory.contains(MESSAGE) -> "@dbl@I should take the message to @dre@Romeo@dbl@ in Varrock Square."
                player.bank.contains(MESSAGE) -> "@dbl@I should withdraw Juliet's message and take it to @dre@Romeo@dbl@."
                else -> "@dbl@I should ask @dre@Juliet@dbl@ for another copy of her message."
            })
            PASSED_MESSAGE -> listOf("@dbl@I should see @dre@Father Lawrence@dbl@ in the church in northeast Varrock.")
            SPOKEN_FATHER -> listOf("@dbl@I need to speak to the @dre@Apothecary@dbl@ in southwest Varrock.")
            SPOKEN_APOTHECARY -> listOf(when {
                player.inventory.contains(POTION) -> "@dbl@I should take the @dre@Cadava potion@dbl@ to Juliet."
                player.bank.contains(POTION) -> "@dbl@I should withdraw the @dre@Cadava potion@dbl@ and take it to Juliet."
                player.inventory.contains(BERRIES) -> "@dbl@I should take the @dre@Cadava berries@dbl@ to the Apothecary."
                player.bank.contains(BERRIES) -> "@dbl@I should withdraw my @dre@Cadava berries@dbl@ for the Apothecary."
                else -> "@dbl@I can find @dre@Cadava berries@dbl@ southeast of Varrock."
            })
            JULIET_CRYPT -> listOf("@dbl@I should tell @dre@Romeo@dbl@ the plan in Varrock Square.")
            COMPLETE -> listOf("@str@Romeo came to the crypt, then left with Juliet's cousin Phillipa.", "", "@red@QUEST COMPLETE!",
                "${progress.questPoints} Quest Points")
            else -> emptyList()
        }
        if (stage != NOT_STARTED) journal.newLine()
        next.forEach(journal::addLine)
        player.overlays.open(journal)
    }
}
