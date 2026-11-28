/* Mechanics adapted from LostCityRS/Content at 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50. */
package game.content.princeAliRescue

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.ext.playJingle
import api.quest.QuestProgress
import game.player.Jingles
import game.player.QuestJournalInterface
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.net.msg.out.WidgetColourMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * Tracks the Prince Ali Rescue story, copied key and one-time reward.
 *
 * @author lare96
 */
object PrinceAliRescue {
    const val HASSAN = 923
    const val OSMAN = 924
    const val LEELA = 915
    const val NED = 918
    const val AGGIE = 922
    const val KELI = 919
    const val JOE = 916
    const val ALI = 920
    const val ALI_DISGUISED = 921

    const val COINS = 995
    const val SOFT_CLAY = 1761
    const val BRONZE_BAR = 2349
    const val KEY_PRINT = 2423
    const val BRONZE_KEY = 2418
    const val WOOL = 1759
    const val WIG = 2421
    const val BLONDE_WIG = 2419
    const val YELLOW_DYE = 1765
    const val SKIN_PASTE = 2424
    const val PINK_SKIRT = 1013
    const val ROPE = 954
    const val BEER = 1917
    const val ASHES = 592
    const val FLOUR = 1933
    const val REDBERRIES = 1951
    const val ONION = 1957
    const val BUCKET_OF_WATER = 1929
    const val JUG_OF_WATER = 1937

    const val STARTED = 10
    const val BRIEFED = 20
    const val PREPARED = 30
    const val JOE_DRUNK = 40
    const val KELI_TIED = 50
    const val ALI_RESCUED = 100
    const val COMPLETE = 110

    const val KEY_ORDERED = 1
    const val KEY_COLLECTED = 2
    const val KEY_REPLACEMENT_PRICE = 15

    const val JOURNAL_BUTTON = 7342
    const val CASH_REWARD = 700
    const val CELL_DOOR = 2881
    val cellDoorTile = Position(3123, 3243)
    val disguise = listOf(BLONDE_WIG, PINK_SKIRT, SKIN_PASTE)

    private val progress = QuestProgress(PersistentVarp.PRINCE_ALI_RESCUE, COMPLETE, 3)
    internal var Player.aliCellUnlocked by Attr.boolean().persist("prince_ali_cell_unlocked")

    fun stage(player: Player): Int = progress.stage(player)

    fun hasRescuedAli(player: Player): Boolean = stage(player) >= ALI_RESCUED

    fun keyStatus(player: Player): Int = player.varpManager.getValue(PersistentVarp.PRINCE_ALI_KEY_STATUS)

    internal fun setKeyStatus(player: Player, status: Int) {
        player.varpManager.setAndSendValue(PersistentVarp.PRINCE_ALI_KEY_STATUS, status)
    }

    internal fun advance(player: Player, expected: Int, next: Int) {
        if (stage(player) == expected && progress.advance(player, next)) refresh(player)
    }

    internal fun nearby(player: Player, npc: Npc): Boolean =
        npc.state == EntityState.ACTIVE && player.isAlive && player.position.isWithinDistance(npc.position, 3)

    internal fun helping(player: Player): Boolean = stage(player) in BRIEFED until ALI_RESCUED

    internal fun hasDisguiseAndKey(player: Player): Boolean =
        player.inventory.containsAll(BRONZE_KEY, BLONDE_WIG, PINK_SKIRT, SKIN_PASTE)

    internal fun hasKey(player: Player): Boolean =
        player.inventory.contains(BRONZE_KEY) || player.bank.contains(BRONZE_KEY)

    internal fun insideCell(position: Position): Boolean =
        position.z == cellDoorTile.z && position.x in 3121..3125 && position.y in 3240..3243

    fun refresh(player: Player) {
        val colour = when (stage(player)) {
            0 -> 0xff0000
            COMPLETE -> 0x00ff00
            else -> 0xffff00
        }
        player.queue(WidgetColourMessageWriter(JOURNAL_BUTTON, colour))
    }

    internal fun finish(player: Player) {
        if (stage(player) != ALI_RESCUED) return
        val coins = Item(COINS, CASH_REWARD)
        if (player.inventory.computeAmountForId(COINS) > Int.MAX_VALUE - CASH_REWARD ||
            !player.inventory.hasSpaceFor(coins)) {
            player.sendMessage("Make room in your inventory for Hassan's reward.")
            return
        }
        if (!progress.complete(player, ALI_RESCUED)) return
        player.inventory.add(coins)
        refresh(player)
        player.playJingle(Jingles.QUEST_COMPLETE_1)
        player.sendMessage("Congratulations! Quest complete!")
        player.overlays.open(object : StandardInterface(12140) {
            override fun onOpen(plr: Player) {
                plr.sendText("You have completed the\\nPrince Ali Rescue Quest!", 12144)
                plr.queue(WidgetItemModelMessageWriter(12145, 250, COINS))
                plr.sendText("${progress.questPoints} Quest Points", 12150)
                plr.sendText("$CASH_REWARD Coins", 12151)
                plr.sendText("Free passage through the Al Kharid toll gate", 12152)
                for (id in 12153..12155) plr.sendText("", id)
            }
        })
    }

    fun journal(player: Player) {
        val journal = QuestJournalInterface("The Rescue of Prince Ali")
        val stage = stage(player)
        if (stage == 0) {
            journal.addLine("@dbl@I can start this quest by speaking to @dre@Hassan@dbl@")
            journal.addLine("@dbl@in the @dre@Al Kharid palace@dbl@.")
        } else {
            journal.addLine("@str@Hassan hired me and sent me to speak to Osman.")
            journal.newLine()
            if (stage == STARTED) {
                journal.addLine("@dbl@I should speak to @dre@Osman@dbl@ outside the palace.")
            } else {
                journal.addLine("@str@Osman told me that Lady Keli kidnapped Prince Ali.")
                journal.addLine("@str@Leela tracked him to the jail near Draynor Village.")
                journal.newLine()
                addRescueNotes(journal, player, stage)
            }
        }
        player.overlays.open(journal)
    }

    private fun addRescueNotes(journal: QuestJournalInterface, player: Player, stage: Int) {
        if (stage == BRIEFED) {
            journal.addLine("@dbl@I should speak to @dre@Leela@dbl@ near @dre@Draynor Village@dbl@.")
            val supplies = mapOf(BRONZE_KEY to "A copy of the jail key", ROPE to "Rope to tie up Lady Keli",
                SKIN_PASTE to "Skin paste from Aggie", PINK_SKIRT to "A pink skirt", BLONDE_WIG to "A blonde wig")
            supplies.forEach { (id, description) ->
                journal.addLine((if (player.inventory.contains(id)) "@str@" else "@dre@") + description)
            }
            return
        }
        journal.addLine("@str@I collected the duplicate key and disguise.")
        if (stage >= JOE_DRUNK) journal.addLine("@str@I distracted Joe with three beers.")
        if (stage >= KELI_TIED) journal.addLine("@str@I tied up Lady Keli and hid her in a cupboard.")
        if (stage >= ALI_RESCUED) journal.addLine("@str@I gave Ali his disguise and helped him escape.")
        journal.newLine()
        when (stage) {
            PREPARED -> journal.addLine("@dbl@I should speak to @dre@Joe@dbl@ and find a way to distract him.")
            JOE_DRUNK -> journal.addLine("@dbl@I should use @dre@rope@dbl@ to tie up @dre@Lady Keli@dbl@.")
            KELI_TIED -> {
                journal.addLine("@dbl@I should unlock @dre@Ali's cell@dbl@ with the bronze key.")
                journal.addLine("@dbl@I need to give him the wig, skirt, paste and key.")
            }
            ALI_RESCUED -> journal.addLine("@dbl@I should return to @dre@Hassan@dbl@ for my reward.")
            COMPLETE -> {
                journal.addLine("@red@QUEST COMPLETE!")
                journal.addLine("${progress.questPoints} Quest Points")
                journal.addLine("$CASH_REWARD Coins")
                journal.addLine("Free passage through the Al Kharid toll gate")
            }
        }
    }
}
