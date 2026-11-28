package game.content.sheepShearer

import api.predef.crafting
import api.predef.ext.playJingle
import api.quest.QuestProgress
import game.player.Jingles
import game.player.QuestJournalInterface
import game.skill.crafting.shearing.SheepShearing
import game.skill.crafting.textileCrafting.Textile
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.net.msg.out.WidgetColourMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * Records Fred's wool deliveries and presents the Sheep Shearer journal and reward.
 *
 * @author lare96
 */
object SheepShearer {
    const val FRED = 758
    const val JOURNAL_BUTTON = 7344
    const val NOT_STARTED = 0
    const val STARTED = 1
    const val LAST_BALL = 20
    const val COMPLETE = 22
    private const val TITLE = "Sheep Shearer"
    private const val CRAFTING_XP = 150.0
    private val coins = Item(Currency.COINS.id, 60)
    private val ball = Textile.BALL_OF_WOOL.processedItem
    private val progress = QuestProgress(PersistentVarp.SHEEP_SHEARER, COMPLETE, 1)

    fun stage(player: Player): Int = progress.stage(player)

    fun ballsRemaining(player: Player): Int = (LAST_BALL + 1 - stage(player)).coerceIn(0, LAST_BALL)

    internal fun start(player: Player) {
        if (progress.advance(player, STARTED)) refresh(player)
    }

    internal fun deliver(player: Player): Int {
        val stage = stage(player)
        if (stage !in STARTED until LAST_BALL || !player.isAlive) return 0
        // The last ball stays in the inventory until Fred's payment dialogue is confirmed.
        // The 289 and 274 scripts confirm the cut-off missing in the 377 batch hand-in.
        val amount = minOf(player.inventory.computeAmountForId(ball.id), LAST_BALL - stage)
        if (amount == 0 || !player.inventory.remove(ball.withAmount(amount))) return 0
        progress.advance(player, stage + amount)
        return amount
    }

    internal fun finish(player: Player) {
        if (!player.isAlive || stage(player) != LAST_BALL || ball.id !in player.inventory) return
        if (player.inventory.computeAmountForId(coins.id) > Int.MAX_VALUE - coins.amount) {
            player.sendMessage("You need room in your coin stack before Fred can pay you.")
            return
        }
        if (!progress.complete(player, LAST_BALL)) return
        player.inventory.updateAll(listOf(coins), listOf(ball))
        player.crafting.let { it.experience += CRAFTING_XP }
        refresh(player)
        player.playJingle(Jingles.QUEST_COMPLETE_1)
        player.sendMessage("Congratulations! Quest complete!")
        player.overlays.open(object : StandardInterface(12140) {
            override fun onOpen(plr: Player) {
                plr.sendText("You have completed the\n$TITLE Quest!", 12144)
                plr.queue(WidgetItemModelMessageWriter(12145, 250, SheepShearing.SHEARS))
                plr.sendText("1 Quest Point", 12150)
                plr.sendText("150 Crafting XP", 12151)
                plr.sendText("60 coins", 12152)
                for (id in 12153..12155) plr.sendText("", id)
            }
        })
    }

    internal fun refresh(player: Player) {
        val colour = when (stage(player)) {
            NOT_STARTED -> 0xff0000
            COMPLETE -> 0x00ff00
            else -> 0xffff00
        }
        player.queue(WidgetColourMessageWriter(JOURNAL_BUTTON, colour))
    }

    internal fun journal(player: Player) {
        val journal = QuestJournalInterface(TITLE)
        when (stage(player)) {
            NOT_STARTED -> {
                journal.addLine("@dbl@I can start this quest by speaking to @dre@Farmer Fred@dbl@ at his")
                journal.addLine("@dre@farm@dbl@ just a little way @dre@North West of Lumbridge@dbl@.")
            }
            COMPLETE -> {
                journal.addLine("@str@I brought Farmer Fred 20 balls of wool, and he paid me for it!")
                journal.newLine()
                journal.addLine("@dre@QUEST COMPLETE!")
            }
            else -> {
                journal.addLine("@str@I asked Farmer Fred, near Lumbridge, for a quest.")
                journal.addLine("@str@Fred said he'd pay me for shearing his sheep for him!")
                journal.newLine()
                val needed = ballsRemaining(player) - player.inventory.computeAmountForId(ball.id)
                if (needed <= 0) {
                    journal.addLine("@dbl@I have enough @dre@balls of wool @dbl@to give @dre@Fred @dbl@and get my reward!")
                } else {
                    val wool = if (needed == 1) "ball" else "balls"
                    journal.addLine("@dbl@I need to collect $needed more @dre@$wool of wool@dbl@.")
                }
            }
        }
        player.overlays.open(journal)
    }
}
