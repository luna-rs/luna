package game.content.cooksAssistant

import api.predef.ext.playJingle
import api.quest.QuestProgress
import game.content.flourMill.FlourMill
import game.player.Jingles
import game.player.QuestJournalInterface
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.net.msg.out.WidgetColourMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * Saved progress, journal and rewards for the Lumbridge Cook's quest.
 *
 * @author lare96
 */
object CooksAssistant {
    const val COOK = 278
    const val JOURNAL_BUTTON = 7333
    const val COOK_ANAGRAM = 2843
    const val MILK = 1927
    const val EGG = 1944
    const val FLOUR = FlourMill.POT_OF_FLOUR

    const val NOT_STARTED = 0
    const val STARTED = 1
    const val COMPLETE = 2
    private const val COOKING_XP = 300
    private const val CAKE = 1891
    private const val TITLE = "Cook's Assistant"

    private val progress = QuestProgress(PersistentVarp.COOKS_ASSISTANT, COMPLETE, 1)
    private val handIn = listOf(Item(MILK), Item(EGG), Item(FLOUR))

    fun stage(player: Player) = progress.stage(player)

    fun refresh(player: Player) {
        val colour = when (stage(player)) {
            NOT_STARTED -> 0xff0000
            STARTED -> 0xffff00
            else -> 0x00ff00
        }
        player.queue(WidgetColourMessageWriter(JOURNAL_BUTTON, colour))
    }

    internal fun start(player: Player) {
        if (progress.advance(player, STARTED)) refresh(player)
    }

    internal fun hasIngredients(player: Player) = player.inventory.containsAll(handIn)

    internal fun finish(player: Player) {
        if (!hasIngredients(player)) {
            player.sendMessage("You need to have all three ingredients with you.")
            return
        }
        if (!progress.complete(player, STARTED)) return
        player.inventory.removeAll(handIn)
        player.skill(Skill.COOKING).let { it.experience += COOKING_XP }
        refresh(player)
        player.playJingle(Jingles.QUEST_COMPLETE_1)
        player.overlays.open(object : StandardInterface(12140) {
            override fun onOpen(plr: Player) {
                plr.sendText("You have completed the Cook's Assistant Quest!", 12144)
                plr.queue(WidgetItemModelMessageWriter(12145, 250, CAKE))
                plr.sendText("${progress.questPoints} Quest Point", 12150)
                plr.sendText("$COOKING_XP Cooking XP", 12151)
                for (id in 12152..12155) plr.sendText("", id)
            }
        })
    }

    fun journal(player: Player) {
        val journal = QuestJournalInterface(TITLE)
        when (stage(player)) {
            NOT_STARTED -> {
                journal.addLine("@dbl@I can start by speaking to the @dre@Cook@dbl@ in the kitchen")
                journal.addLine("@dbl@on the ground floor of @dre@Lumbridge Castle@dbl@.")
            }
            STARTED -> {
                journal.addLine("@dbl@The @dre@Duke of Lumbridge@dbl@ is celebrating his birthday.")
                journal.addLine("@dbl@I agreed to bring the Cook ingredients for his cake.")
                journal.newLine()
                for ((id, ingredient) in listOf(MILK to "a bucket of milk", EGG to "an egg", FLOUR to "a pot of flour")) {
                    val colour = if (player.inventory.contains(id)) "@str@" else "@dre@"
                    journal.addLine("$colour$ingredient")
                }
            }
            COMPLETE -> {
                journal.addLine("@str@I brought milk, an egg and flour to the Cook, who")
                journal.addLine("@str@made a birthday cake for the Duke of Lumbridge.")
                journal.newLine()
                journal.addLine("@dbl@I earned ${progress.questPoints} Quest Point and $COOKING_XP Cooking XP.")
                journal.addLine("@red@QUEST COMPLETE!")
            }
        }
        player.overlays.open(journal)
    }
}
