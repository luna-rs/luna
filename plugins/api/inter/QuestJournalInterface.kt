package api.inter

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.StandardInterface
import java.util.*

/**
 * A [StandardInterface] implementation representing the quest journal interface.
 *
 * @author lare96 <http://github.com/lare96>
 */
class QuestJournalInterface(val title: String) : StandardInterface(8134) {

    companion object {

        /**
         * The title line id.
         */
        val TITLE_LINE = 8144

        /**
         * The first line id.
         */
        val FIRST_LINE = 8145

        /**
         * Subsequent line ids.
         */
        val CONTENT_LINES = 8147..8247
    }

    /**
     * The queue of lines to display.
     */
    private var lines = ArrayDeque<Any>()

    /**
     * The amount of lines displayed.
     */
    var size = 0
        private set

    override fun onOpen(plr: Player) {
        plr.sendText(title, TITLE_LINE)
        if (size > 0) {
            plr.sendText(lines.poll(), FIRST_LINE)
            for (line in CONTENT_LINES) {
                plr.sendText(lines.poll() ?: break, line)
            }
        }
    }

    override fun onClose(plr: Player) {
        plr.sendText("", TITLE_LINE)
        plr.sendText("", FIRST_LINE)

        if (size > 1) {
            for (line in CONTENT_LINES.first..(CONTENT_LINES.first + size)) {
                plr.sendText("", line)
            }
        }
    }

    /**
     * Adds a line to be displayed.
     */
    fun addLine(obj: Any) {
        lines.add(obj)
        size++
    }

    /**
     * Adds a blank newline to be displayed.
     */
    fun newLine() {
        addLine("")
    }
}