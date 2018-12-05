import api.predef.*
import io.luna.game.event.impl.ButtonClickEvent

/**
 * Sets the withdraw mode if the banking interface is open.
 */
fun setWithdrawMode(msg: ButtonClickEvent, value: Boolean) {
    val plr = msg.plr
    if (plr.bank.isOpen) {
        plr.isWithdrawAsNote = value
    }
}

/**
 * Withdraw items as unnoted.
 */
button(5387) { setWithdrawMode(it, false) }

/**
 * Withdraw items as noted.
 */
button(5386) { setWithdrawMode(it, true) }
