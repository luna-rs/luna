package game.player

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.net.msg.out.WidgetScrollPositionMessageWriter

/**
 * A [StandardInterface] implementation representing the quest journal interface.
 *
 * @author lare96 
 */
class QuestJournalInterface(private val title: String) : StandardInterface(8134) {

    companion object {

        /**
         * The title line id.
         */
        const val TITLE_LINE = 8144

        /**
         * The first line id.
         */
        const val FIRST_LINE = 8145

        /**
         * Subsequent line ids in the revision 377 journal's scroll container.
         */
        val CONTENT_LINES = (8147..8195).toList() + (12174..12223).toList()

        /**
         * All one hundred body widgets, in display order.
         */
        private val LINE_IDS = listOf(FIRST_LINE) + CONTENT_LINES
    }

    /**
     * The lines to display, retained when the journal is closed and reopened.
     */
    private val lines = ArrayList<Any>(100)

    /**
     * The amount of lines displayed.
     */
    val size: Int
        get() = lines.size

    override fun onOpen(plr: Player) {
        plr.queue(WidgetScrollPositionMessageWriter(8143, 0))
        // The client discards widget state when an interface is closed. Refresh the
        // server's text cache too, and overwrite unused rows from longer journals.
        plr.clearText(TITLE_LINE)
        plr.sendText(title, TITLE_LINE)
        for ((index, id) in LINE_IDS.withIndex()) {
            plr.clearText(id)
            plr.sendText(lines.getOrNull(index) ?: "", id)
        }
    }

    override fun onClose(plr: Player) {
        plr.sendText("", TITLE_LINE)
        for (id in LINE_IDS) {
            plr.sendText("", id)
        }
    }

    /**
     * Adds a line to be displayed.
     */
    fun addLine(obj: Any) {
        if (size == 100) {
            throw IllegalStateException("Maximum lines reached! (100)")
        }
        lines.add(obj)
    }

    /**
     * Adds a blank newline to be displayed.
     */
    fun newLine() {
        addLine("")
    }
}
