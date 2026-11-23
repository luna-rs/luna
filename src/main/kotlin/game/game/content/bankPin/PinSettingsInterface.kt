package game.content.bankPin

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.net.msg.out.WidgetPositionMessageWriter
import io.luna.net.msg.out.WidgetVisibilityMessageWriter

/**
 * The Bank PIN settings window. Its buttons for each PIN state share the same spots, so the ones that don't apply
 * are moved out of the window.
 *
 * @author TheLining
 */
class PinSettingsInterface : StandardInterface(ID) {

    companion object {

        /**
         * The settings window.
         */
        const val ID = 14924

        const val SET_BUTTON = 15075
        const val NO_PIN_DELAY_BUTTON = 15076
        const val CHANGE_BUTTON = 15078
        const val DELETE_BUTTON = 15079
        const val PIN_DELAY_BUTTON = 15080
        const val CANCEL_PENDING_BUTTON = 15082
        const val YES_BUTTON = 15171
        const val NO_BUTTON = 15176

        /**
         * The PIN status line.
         */
        const val STATUS = 15105

        /**
         * The recovery delay line.
         */
        const val DELAY = 15107

        /**
         * The twelve lines of the message panel.
         */
        val MESSAGE_LINES = 15038..15049

        /**
         * The "Do you really wish to do that?" panel.
         */
        const val CONFIRM_PANEL = 15108

        /**
         * The buttons of each PIN state.
         */
        private val NO_PIN_BUTTONS = listOf(SET_BUTTON, NO_PIN_DELAY_BUTTON)
        private val ACTIVE_BUTTONS = listOf(CHANGE_BUTTON, DELETE_BUTTON, PIN_DELAY_BUTTON)
        private val PENDING_BUTTONS = listOf(CANCEL_PENDING_BUTTON)
        private val STATE_BUTTONS = NO_PIN_BUTTONS + ACTIVE_BUTTONS + PENDING_BUTTONS

        /**
         * How far a button is moved down to take it out of the window.
         */
        private const val AWAY = 1000

        /**
         * The widest message line, in characters of the small font.
         */
        private const val LINE_LENGTH = 24
    }

    /**
     * What "Yes, I really want to do that." does, or `null` while no question is asked.
     */
    var pendingQuestion: ((Player) -> Unit)? = null
        private set

    override fun onOpen(player: Player) {
        refresh(player)
    }

    /**
     * Redraws the status, the delay, the buttons of the current state and the messages.
     */
    fun refresh(player: Player, vararg messages: String) {
        val state = BankPin.state(player)
        player.sendText(state.status, STATUS)
        player.sendText("${player.bankPinRecoveryDays} days", DELAY)
        val shown = when (state) {
            PinState.NONE -> NO_PIN_BUTTONS
            PinState.ACTIVE -> ACTIVE_BUTTONS
            PinState.PENDING -> PENDING_BUTTONS
        }
        STATE_BUTTONS.forEach { move(player, it, if (it in shown) 0 else AWAY) }
        hideQuestion(player)

        val lines = (if (messages.isEmpty()) state.messages(player) else messages.toList()).flatMap { wrap(it) + "" }
        MESSAGE_LINES.forEachIndexed { index, line -> player.sendText(lines.getOrElse(index) { "" }, line) }
    }

    /**
     * Asks "Do you really wish to do that?" and runs [action] on yes. The state's buttons are moved away meanwhile.
     */
    fun ask(player: Player, action: (Player) -> Unit) {
        pendingQuestion = action
        STATE_BUTTONS.forEach { move(player, it, AWAY) }
        player.queue(WidgetVisibilityMessageWriter(CONFIRM_PANEL, false))
        move(player, YES_BUTTON, 0)
        move(player, NO_BUTTON, 0)
    }

    /**
     * Takes the yes or no question away. [refresh] brings the state's buttons back.
     */
    private fun hideQuestion(player: Player) {
        pendingQuestion = null
        player.queue(WidgetVisibilityMessageWriter(CONFIRM_PANEL, true))
        move(player, YES_BUTTON, AWAY)
        move(player, NO_BUTTON, AWAY)
    }

    private fun move(player: Player, widget: Int, y: Int) {
        player.queue(WidgetPositionMessageWriter(widget, 0, y))
    }

    /**
     * Splits [message] into lines that fit the message panel.
     */
    private fun wrap(message: String): List<String> {
        val lines = ArrayList<String>()
        var line = StringBuilder()
        for (word in message.split(' ')) {
            if (line.isNotEmpty() && line.length + 1 + word.length > LINE_LENGTH) {
                lines += line.toString()
                line = StringBuilder()
            }
            if (line.isNotEmpty()) {
                line.append(' ')
            }
            line.append(word)
        }
        lines += line.toString()
        return lines
    }
}
