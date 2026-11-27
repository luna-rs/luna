package game.content.runeMysteries

import api.predef.ext.playJingle
import api.quest.QuestProgress
import game.player.Jingles
import game.player.QuestJournalInterface
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.net.msg.out.WidgetColourMessageWriter
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * Rune Mysteries story progress, journal and completion.
 *
 * @author lare96
 */
object RuneMysteries {
    const val JOURNAL_BUTTON = 7335
    const val AIR_TALISMAN = 1438
    const val RESEARCH_PACKAGE = 290
    const val RESEARCH_NOTES = 291

    const val NOT_STARTED = 0
    const val STARTED = 1
    const val TALISMAN_DELIVERED = 2
    const val PACKAGE_RECEIVED = 3
    const val PACKAGE_DELIVERED = 4
    const val NOTES_RECEIVED = 5
    const val COMPLETE = 6

    private val progress = QuestProgress(PersistentVarp.RUNE_MYSTERIES, COMPLETE, 1)

    fun stage(player: Player): Int = progress.stage(player)

    fun refresh(player: Player) {
        val colour = when (stage(player)) {
            NOT_STARTED -> 0xff0000
            COMPLETE -> 0x00ff00
            else -> 0xffff00
        }
        player.queue(WidgetColourMessageWriter(JOURNAL_BUTTON, colour))
    }

    internal fun advance(player: Player, next: Int) {
        if (progress.advance(player, next)) refresh(player)
    }

    internal fun finish(player: Player) {
        if (!player.inventory.contains(RESEARCH_NOTES) || !progress.complete(player, NOTES_RECEIVED)) return
        // The notes occupy the slot needed for the returned talisman.
        player.inventory.remove(RESEARCH_NOTES)
        player.inventory.add(AIR_TALISMAN)
        refresh(player)
        player.playJingle(Jingles.QUEST_COMPLETE_1)
        player.overlays.open(object : StandardInterface(12140) {
            override fun onOpen(plr: Player) {
                plr.sendText("You have completed the Rune Mysteries Quest!", 12144)
                plr.queue(WidgetItemModelMessageWriter(12145, 250, AIR_TALISMAN))
                plr.sendText("${progress.questPoints} Quest Point", 12150)
                plr.sendText("Air talisman", 12151)
                plr.sendText("Ask a wizard to visit the Rune Essence mine.", 12152)
                plr.sendText("Take the essence to an altar to craft runes.", 12153)
                for (id in 12154..12155) plr.sendText("", id)
            }
        })
    }

    fun journal(player: Player) {
        val current = stage(player)
        val journal = QuestJournalInterface("Rune Mysteries")
        val history = listOf(
            "Duke Horacio found a strange talisman and asked me to take it|" +
                "to the head wizard at the Wizards' Tower.",
            "I delivered the talisman to Sedridor, the head wizard.",
            "I agreed to take Sedridor's research package to Aubury.",
            "I delivered the research package to Aubury in Varrock.",
            "Aubury gave me his own research notes to take back to Sedridor.",
            "I returned the notes. Sedridor explained how he and Aubury|" +
                "rediscovered Runecrafting and gave me back the air talisman."
        )
        history.take(current).flatMap { it.split('|') }.forEach { journal.addLine("@str@$it") }
        if (current != NOT_STARTED) journal.newLine()
        val next = when (current) {
            NOT_STARTED -> listOf("@dbl@I can start this quest by speaking to @dre@Duke Horacio@dbl@,",
                "@dbl@upstairs in @dre@Lumbridge Castle@dbl@.")
            STARTED -> listOf("@dbl@Take the @dre@air talisman@dbl@ to the head wizard in the",
                "@dre@Wizards' Tower@dbl@, south-west of Lumbridge.")
            TALISMAN_DELIVERED -> listOf("@dbl@Talk to @dre@Sedridor@dbl@ again about his research.")
            PACKAGE_RECEIVED -> listOf("@dbl@Take the @dre@research package@dbl@ to @dre@Aubury@dbl@ in @dre@Varrock@dbl@.")
            PACKAGE_DELIVERED -> listOf("@dbl@Speak to @dre@Aubury@dbl@ again after he has examined the package.")
            NOTES_RECEIVED -> listOf("@dbl@Return @dre@Aubury's notes@dbl@ to @dre@Sedridor@dbl@ at the Wizards' Tower.")
            else -> listOf("@red@QUEST COMPLETE!")
        }
        next.forEach(journal::addLine)
        player.overlays.open(journal)
    }
}
