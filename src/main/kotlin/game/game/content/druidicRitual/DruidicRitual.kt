/* Mechanics and journal adapted from LostCityRS/Content quest_druid at 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50. */
package game.content.druidicRitual

import api.predef.ext.playJingle
import api.predef.ext.scheduleOnce
import api.quest.QuestProgress
import game.player.Jingles
import game.player.QuestJournalInterface
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.net.msg.out.WidgetColourMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * Tracks the druids' preparations and presents the revision-377 quest journal and reward scroll.
 *
 * @author lare96
 */
object DruidicRitual {
    const val KAQEMEEX = 455
    const val SANFEW = 454
    const val CAULDRON = 2142
    const val JOURNAL_BUTTON = 7355
    const val NOT_STARTED = 0
    const val STARTED = 1
    const val SPOKE_TO_SANFEW = 2
    const val DELIVERED_MEATS = 3
    const val COMPLETE = 4
    const val HERBLORE_REWARD = 250.0

    val kaqemeexPosition = Position(2925, 3486)
    val sanfewPosition = Position(2897, 3426, 1)
    val cauldronPosition = Position(2893, 9831)
    val meats = mapOf(2132 to 522, 2134 to 523, 2136 to 524, 2138 to 525)

    private val offerings = meats.values.map { Item(it) }
    private val progress = QuestProgress(PersistentVarp.DRUIDIC_RITUAL, COMPLETE, 4)

    fun stage(player: Player): Int = progress.stage(player)

    internal fun advance(player: Player, expected: Int, next: Int) {
        if (stage(player) == expected && progress.advance(player, next)) refresh(player)
    }

    internal fun hasOfferings(player: Player): Boolean = meats.values.all(player.inventory::contains)

    internal fun deliver(player: Player): Boolean {
        if (stage(player) != SPOKE_TO_SANFEW || !hasOfferings(player)) return false
        player.inventory.removeAll(offerings)
        advance(player, SPOKE_TO_SANFEW, DELIVERED_MEATS)
        return true
    }

    internal fun dip(player: Player, slot: Int, raw: Int, enchanted: Int) {
        val inventory = player.inventory
        if (!inventory.contains(slot, raw)) return
        val meat = inventory[slot]
        inventory.set(slot, Item(enchanted))
        player.sendMessage("You dip the ${meat.itemDef.name.lowercase()} in the cauldron.")
    }

    fun refresh(player: Player) {
        val colour = when (stage(player)) {
            NOT_STARTED -> 0xff0000
            COMPLETE -> 0x00ff00
            else -> 0xffff00
        }
        player.queue(WidgetColourMessageWriter(JOURNAL_BUTTON, colour))
    }

    internal fun finish(player: Player, lesson: (Player) -> Unit) {
        if (!progress.complete(player, DELIVERED_MEATS)) return
        player.skill(Skill.HERBLORE).let { it.experience += HERBLORE_REWARD }
        refresh(player)
        player.playJingle(Jingles.QUEST_COMPLETE_1)
        player.sendMessage("Congratulations! Quest complete!")
        player.overlays.open(object : StandardInterface(12140) {
            override fun onOpen(plr: Player) {
                plr.sendText("You have completed the\\nDruidic Ritual Quest!", 12144)
                plr.queue(WidgetItemModelMessageWriter(12145, 220, 251))
                plr.sendText("${progress.questPoints} Quest Points", 12150)
                plr.sendText("250 Herblore XP", 12151)
                for (id in 12152..12155) plr.sendText("", id)
            }

            override fun onClose(plr: Player) {
                plr.world.scheduleOnce(1) {
                    if (!plr.overlays.hasWindow()) lesson(plr)
                }
            }
        })
    }

    fun journal(player: Player) {
        val journal = QuestJournalInterface("Druidic Ritual")
        val stage = stage(player)
        if (stage == NOT_STARTED) {
            journal.addLine("@dbl@I can start by speaking to @dre@Kaqemeex@dbl@ at the")
            journal.addLine("@dre@Druids' Circle@dbl@ just north of @dre@Taverley@dbl@.")
        } else {
            journal.addLine("@str@I told Kaqemeex I would help prepare the ceremony.")
            journal.newLine()
            when (stage) {
                STARTED -> {
                    journal.addLine("@dbl@I should speak to @dre@Sanfew@dbl@ upstairs in the village")
                    journal.addLine("@dbl@to the south.")
                }
                SPOKE_TO_SANFEW -> {
                    journal.addLine("@dre@Sanfew@dbl@ needs four raw meats for the ritual.")
                    journal.addLine("@dbl@I should dip @dre@beef@dbl@, @dre@rat meat@dbl@, @dre@bear meat@dbl@ and @dre@chicken@dbl@")
                    journal.addLine("@dbl@in the @dre@Cauldron of Thunder@dbl@ in the dungeon")
                    journal.addLine("@dbl@south of @dre@Taverley@dbl@ and take them back to him.")
                }
                else -> {
                    journal.addLine("@str@I dipped the four meats in the Cauldron of Thunder")
                    journal.addLine("@str@and gave them to Sanfew.")
                    journal.newLine()
                    if (stage == COMPLETE) {
                        journal.addLine("@str@Kaqemeex shared his knowledge of Herblore with me.")
                        journal.newLine()
                        journal.addLine("@red@QUEST COMPLETE!")
                        journal.addLine("${progress.questPoints} Quest Points")
                        journal.addLine("250 Herblore XP")
                    } else {
                        journal.addLine("@dbl@I should speak to @dre@Kaqemeex@dbl@ again for my reward.")
                    }
                }
            }
        }
        player.overlays.open(journal)
    }
}
