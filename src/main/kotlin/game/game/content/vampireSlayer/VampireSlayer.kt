/* Mechanics adapted from LostCityRS/Content quest_vampire at 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50. */
package game.content.vampireSlayer

import api.predef.ext.playJingle
import api.quest.QuestProgress
import game.player.Jingles
import game.player.QuestJournalInterface
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.net.msg.out.WidgetColourMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * Tracks the saved Vampire Slayer story and presents its quest journal and rewards.
 *
 * @author lare96
 */
object VampireSlayer {
    const val MORGAN = 755
    const val HARLOW = 756
    const val COUNT = 757
    const val STAKE = 1549
    const val GARLIC = 1550
    const val BEER = 1917
    const val HAMMER = 2347
    const val JOURNAL_BUTTON = 7347
    const val STARTED = 1
    const val SPOKE_TO_HARLOW = 2
    const val COMPLETE = 3
    const val ATTACK_REWARD = 4_825.0

    private val progress = QuestProgress(PersistentVarp.VAMPIRE_SLAYER, COMPLETE, 3)

    fun stage(player: Player): Int = progress.stage(player)

    internal fun advance(player: Player, expected: Int, next: Int) {
        if (stage(player) == expected && progress.advance(player, next)) refresh(player)
    }

    fun refresh(player: Player) {
        val colour = when (stage(player)) {
            0 -> 0xff0000
            COMPLETE -> 0x00ff00
            else -> 0xffff00
        }
        player.queue(WidgetColourMessageWriter(JOURNAL_BUTTON, colour))
    }

    internal fun finish(player: Player) {
        val stage = stage(player)
        if (stage !in STARTED..SPOKE_TO_HARLOW || !player.isAlive ||
            !player.inventory.containsAll(STAKE, HAMMER) || !progress.complete(player, stage)) return

        player.inventory.remove(STAKE)
        player.animation(Animation(806, Animation.AnimationPriority.IMMUTABLE))
        player.sendMessage("You hammer the stake into the vampire's chest!")
        player.skill(Skill.ATTACK).let { it.experience += ATTACK_REWARD }
        refresh(player)
        player.playJingle(Jingles.QUEST_COMPLETE_1)
        player.sendMessage("Congratulations! Quest complete!")
        player.overlays.open(object : StandardInterface(12140) {
            override fun onOpen(plr: Player) {
                plr.sendText("You have completed the\\nVampire Slayer Quest!", 12144)
                plr.queue(WidgetItemModelMessageWriter(12145, 250, STAKE))
                plr.sendText("${progress.questPoints} Quest Points", 12150)
                plr.sendText("4,825 Attack XP", 12151)
                for (id in 12152..12155) plr.sendText("", id)
            }
        })
    }

    fun journal(player: Player) {
        val journal = QuestJournalInterface("Vampire Slayer")
        val stage = stage(player)
        if (stage == 0) {
            journal.addLine("@dbl@I can start by speaking to @dre@Morgan@dbl@ in @dre@Draynor Village@dbl@.")
            journal.newLine()
            journal.addLine("@dbl@I must be able to defeat a level 34 vampire.")
        } else {
            journal.addLine("@str@Morgan told me a vampire is terrorising Draynor Village.")
            journal.newLine()
            if (stage == STARTED) {
                journal.addLine("@dbl@Morgan sent me to see @dre@Dr Harlow@dbl@ at the")
                journal.addLine("@dre@Blue Moon Inn@dbl@ in Varrock.")
            } else {
                journal.addLine("@str@I spoke to Dr Harlow. He asked me to buy him a beer.")
                journal.newLine()
                if (stage == COMPLETE) {
                    journal.addLine("@str@I defeated Count Draynor. The village is safe again!")
                    journal.newLine()
                    journal.addLine("@red@QUEST COMPLETE!")
                    journal.addLine("${progress.questPoints} Quest Points")
                    journal.addLine("4,825 Attack XP")
                } else {
                    if (player.inventory.contains(STAKE)) {
                        journal.addLine("@str@I have a stake to drive into the vampire's chest.")
                    } else {
                        journal.addLine("@dbl@I should bring @dre@Dr Harlow@dbl@ a beer and ask for a stake.")
                    }
                    if (!player.inventory.contains(HAMMER)) journal.addLine("@dbl@I need a hammer from a general store.")
                    if (!player.inventory.contains(GARLIC)) journal.addLine("@dbl@Garlic from Morgan's cupboard can weaken the vampire.")
                    journal.newLine()
                    journal.addLine("@dbl@Count Draynor sleeps in the basement of @dre@Draynor Manor@dbl@.")
                }
            }
        }
        player.overlays.open(journal)
    }
}
