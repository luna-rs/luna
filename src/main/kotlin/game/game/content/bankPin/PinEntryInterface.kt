package game.content.bankPin

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.net.msg.out.WidgetPositionMessageWriter

/**
 * The keypad where the player clicks the four digits of a Bank PIN. After every click the digits change buttons and
 * the buttons stray from their places, so the spots a player clicks say nothing about the PIN.
 *
 * @param purpose What the finished PIN is for.
 * @param firstEntry The PIN typed on the previous screen when this one confirms a new PIN, or `null`.
 * @author TheLining
 */
class PinEntryInterface(val purpose: PinPurpose, val firstEntry: String? = null) : StandardInterface(ID) {

    companion object {

        /**
         * The keypad window.
         */
        const val ID = 7424

        /**
         * The ten digit buttons.
         */
        val BUTTONS = 14873..14882

        /**
         * The digit drawn on each button, in the order of [BUTTONS].
         */
        val LABELS = 14883..14892

        /**
         * The four boxes showing how many digits have been entered.
         */
        val BOXES = 14913..14916

        /**
         * The heading above the keypad.
         */
        const val HEADING = 14920

        /**
         * The line saying which digit to click next.
         */
        const val PROMPT = 15313

        /**
         * The line warning that a deletion is due.
         */
        const val DELETION_WARNING = 14923

        /**
         * The "I don't know it." button.
         */
        const val FORGOT_BUTTON = 14921

        /**
         * The "Exit" button.
         */
        const val EXIT_BUTTON = 14922

        /**
         * How far a button may stray from its place each way, across and down, keeping clear of its neighbours.
         */
        private const val STRAY_X = 9
        private const val STRAY_Y = 3

        /**
         * How far a button is moved down to take it out of the window.
         */
        private const val AWAY = 1000

        /**
         * The words for each digit's place, used in [PROMPT].
         */
        private val PLACES = listOf("FIRST", "SECOND", "THIRD", "FOURTH")
    }

    /**
     * The digits entered so far.
     */
    val entered = StringBuilder()

    /**
     * The digit each button stands for right now, in the order of [BUTTONS].
     */
    private var keypad = (0..9).toList()

    override fun onOpen(player: Player) {
        player.queue(WidgetPositionMessageWriter(FORGOT_BUTTON, 0, if (purpose.forgettable) 0 else AWAY))
        player.sendText(purpose.heading, HEADING)
        player.sendText(BankPin.deletionWarning(player), DELETION_WARNING)
        refresh(player)
    }

    /**
     * Adds the digit on [button] to the entry.
     *
     * @return `true` once all four digits are in.
     */
    fun press(player: Player, button: Int): Boolean {
        if (entered.length >= BankPin.LENGTH) {
            return true
        }
        entered.append(keypad[button - BUTTONS.first])
        if (entered.length < BankPin.LENGTH) {
            refresh(player)
            return false
        }
        return true
    }

    /**
     * Shuffles the digits on the buttons and redraws the boxes and prompt.
     */
    private fun refresh(player: Player) {
        keypad = (0..9).shuffled()
        BUTTONS.forEachIndexed { index, button ->
            val x = (-STRAY_X..STRAY_X).random()
            val y = (-STRAY_Y..STRAY_Y).random()
            player.queue(WidgetPositionMessageWriter(button, x, y))
            player.queue(WidgetPositionMessageWriter(LABELS.first + index, x, y))
            player.sendText(keypad[index], LABELS.first + index)
        }
        BOXES.forEachIndexed { index, box -> player.sendText(if (index < entered.length) "*" else "?", box) }
        player.sendText("Click the ${PLACES[entered.length]} digit...", PROMPT)
    }
}
