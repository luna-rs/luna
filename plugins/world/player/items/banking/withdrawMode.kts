import api.*
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
on(ButtonClickEvent::class)
    .args(5387)
    .run { setWithdrawMode(it, false) }

/**
 * Withdraw items as noted.
 */
on(ButtonClickEvent::class)
    .args(5386)
    .run { setWithdrawMode(it, true) }
