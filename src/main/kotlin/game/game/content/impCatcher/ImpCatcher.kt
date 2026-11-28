/* Adapted from LostCityRS/Content quest_imp at 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50. */
package game.content.impCatcher

import api.predef.ext.playJingle
import api.quest.QuestProgress
import game.player.Jingles
import game.player.QuestJournalInterface
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.net.msg.out.WidgetColourMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * Saves Mizgog's request and bead hand-in and presents the quest journal and reward.
 *
 * @author lare96
 */
object ImpCatcher {
    const val MIZGOG = 706
    const val AMULET = 1478
    const val JOURNAL_BUTTON = 7340
    const val STARTED = 1
    const val COMPLETE = 2
    const val MAGIC_XP = 875.0

    private val beadNames = linkedMapOf(1474 to "Black", 1470 to "Red", 1476 to "White", 1472 to "Yellow")
    val beads: Set<Int> get() = beadNames.keys
    private val progress = QuestProgress(PersistentVarp.IMP_CATCHER, COMPLETE, 1)

    fun stage(player: Player): Int = progress.stage(player)

    fun missingBeads(player: Player): List<Int> = beads.filterNot { player.inventory.contains(it) }

    internal fun start(player: Player) {
        if (progress.advance(player, STARTED)) refresh(player)
    }

    internal fun finish(player: Player) {
        if (!player.isAlive || missingBeads(player).isNotEmpty() || !progress.complete(player, STARTED)) return

        // Each bead is an ordinary unstackable item, so removing the four frees room for the amulet.
        for (bead in beads) player.inventory.remove(bead)
        player.inventory.add(AMULET)
        player.skill(Skill.MAGIC).let { it.experience += MAGIC_XP }
        refresh(player)
        player.playJingle(Jingles.QUEST_COMPLETE_1)
        player.sendMessage("You give four coloured beads to Wizard Mizgog.")
        player.sendMessage("The Wizard hands you an amulet.")
        player.overlays.open(object : StandardInterface(12140) {
            override fun onOpen(plr: Player) {
                plr.sendText("You have completed the\\nImp Catcher Quest!", 12144)
                plr.queue(WidgetItemModelMessageWriter(12145, 200, AMULET))
                plr.sendText("${progress.questPoints} Quest Point", 12150)
                plr.sendText("875 Magic XP", 12151)
                plr.sendText("Amulet of accuracy", 12152)
                for (id in 12153..12155) plr.sendText("", id)
            }
        })
    }

    fun refresh(player: Player) {
        val colour = when (stage(player)) {
            STARTED -> 0xffff00
            COMPLETE -> 0x00ff00
            else -> 0xff0000
        }
        player.queue(WidgetColourMessageWriter(JOURNAL_BUTTON, colour))
    }

    fun journal(player: Player) {
        val journal = QuestJournalInterface("Imp Catcher")
        when (stage(player)) {
            0 -> {
                journal.addLine("@dbl@I can start this quest by talking to @dre@Wizard Mizgog@dbl@ in the")
                journal.addLine("@dre@Wizards' Tower@dbl@.")
            }
            STARTED -> {
                journal.addLine("@str@I have spoken to Wizard Mizgog.")
                journal.newLine()
                val missing = missingBeads(player)
                if (missing.isEmpty()) {
                    journal.addLine("@dbl@I have collected all the missing beads and need to return")
                    journal.addLine("@dbl@them to @dre@Wizard Mizgog@dbl@.")
                } else {
                    journal.addLine("@dbl@I can collect the missing beads by killing @dre@Imps@dbl@.")
                    journal.addLine("@dbl@I can find Imps all over the kingdom.")
                    for ((id, colour) in beadNames) {
                        journal.addLine("${if (id in missing) "@dre@" else "@str@"}1 $colour bead.")
                    }
                }
            }
            COMPLETE -> {
                journal.addLine("@str@I spoke to Wizard Mizgog and collected his four beads.")
                journal.newLine()
                journal.addLine("@str@Wizard Mizgog thanked me and gave me an Amulet of accuracy.")
                journal.newLine()
                journal.addLine("@red@QUEST COMPLETE!")
            }
        }
        player.overlays.open(journal)
    }
}
