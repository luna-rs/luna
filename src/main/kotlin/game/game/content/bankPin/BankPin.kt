package game.content.bankPin

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import game.player.Sound
import io.luna.game.model.mob.Player
import java.util.concurrent.TimeUnit

/**
 * The player's Bank PIN, or an empty string if none is active.
 */
var Player.bankPin by Attr.string().persist("bank_pin")

/**
 * A new PIN waiting out the recovery delay, or an empty string if none is.
 */
var Player.bankPinPending by Attr.string().persist("bank_pin_pending")

/**
 * When the pending PIN takes effect, in epoch milliseconds.
 */
var Player.bankPinPendingAt by Attr.long().persist("bank_pin_pending_at")

/**
 * When a forgotten PIN is deleted, in epoch milliseconds, or 0 if no deletion is due.
 */
var Player.bankPinDeleteAt by Attr.long().persist("bank_pin_delete_at")

/**
 * How many days a new PIN or a forgotten one's deletion waits.
 */
var Player.bankPinRecoveryDays by Attr.int { BankPin.LONG_DELAY }.persist("bank_pin_recovery_days")

/**
 * If the player has entered their PIN since logging in.
 */
var Player.bankPinEntered by Attr.boolean()

/**
 * If the player has been reminded of their pending PIN at a bank since logging in.
 */
var Player.bankPinReminded by Attr.boolean()

/**
 * Wrong PINs entered in a row.
 */
var Player.bankPinWrongTries by Attr.int().persist("bank_pin_wrong_tries")

/**
 * When the player may try their PIN again after wrong ones, in epoch milliseconds.
 */
var Player.bankPinLockedUntil by Attr.long().persist("bank_pin_locked_until")

/**
 * The Bank PIN: four digits a player can set to lock their bank, entered once a session.
 *
 * @author TheLining
 */
object BankPin {

    /**
     * Digits in a PIN.
     */
    const val LENGTH = 4

    /**
     * The two recovery delays to choose from, in days.
     */
    const val SHORT_DELAY = 3
    const val LONG_DELAY = 7

    /**
     * How long the keypad stays locked after each count of wrong PINs in a row. The first wrong PIN is free.
     */
    private val LOCK_SECONDS = listOf(0L, 0L, 10L, 60L, 600L, 600L, 1200L)

    /**
     * Applies a pending PIN or a deletion whose day has come, then returns the PIN's state.
     */
    fun state(plr: Player): PinState {
        val now = System.currentTimeMillis()
        if (plr.bankPinPending.isNotEmpty() && now >= plr.bankPinPendingAt) {
            plr.bankPin = plr.bankPinPending
            plr.bankPinPending = ""
            plr.bankPinEntered = false
        }
        if (plr.bankPinDeleteAt > 0 && now >= plr.bankPinDeleteAt) {
            plr.bankPin = ""
            plr.bankPinDeleteAt = 0
        }
        return when {
            plr.bankPin.isNotEmpty() -> PinState.ACTIVE
            plr.bankPinPending.isNotEmpty() -> PinState.PENDING
            else -> PinState.NONE
        }
    }

    /**
     * Whole days left until [epochMillis], counting a part day as one.
     */
    fun daysUntil(epochMillis: Long): Long {
        val left = epochMillis - System.currentTimeMillis()
        return if (left <= 0) 0 else (left + TimeUnit.DAYS.toMillis(1) - 1) / TimeUnit.DAYS.toMillis(1)
    }

    /**
     * [days] as "1 day" or "N days".
     */
    fun dayText(days: Long) = "$days ${if (days == 1L) "day" else "days"}"

    /**
     * The keypad line warning of a forgotten PIN's deletion, or an empty string.
     */
    fun deletionWarning(plr: Player): String {
        if (plr.bankPinDeleteAt == 0L) {
            return ""
        }
        return "YOUR PIN WILL BE DELETED IN ${dayText(daysUntil(plr.bankPinDeleteAt)).uppercase()}"
    }

    /**
     * The two sentences saying a new PIN is waiting to take effect.
     */
    fun pendingNotice(plr: Player) = listOf("You have requested that a PIN be set on your bank account.",
                                            "This will take effect in ${dayText(daysUntil(plr.bankPinPendingAt))}.")

    /**
     * Reminds the player in the chat box of a PIN waiting to take effect, if there is one.
     */
    fun remindPending(plr: Player) {
        if (state(plr) == PinState.PENDING) {
            pendingNotice(plr).forEach { plr.sendMessage(it) }
        }
    }

    /**
     * The bank's open check: a player with an active PIN gets the keypad until they enter it. A player with a PIN
     * waiting to take effect is reminded of it the first time they bank in a session.
     */
    fun mayOpenBank(plr: Player): Boolean {
        if (plr.isBot) {
            return true
        }
        val state = state(plr)
        if (state == PinState.PENDING && !plr.bankPinReminded) {
            plr.bankPinReminded = true
            remindPending(plr)
        }
        if (state != PinState.ACTIVE || plr.bankPinEntered) {
            return true
        }
        openKeypad(plr, PinPurpose.BANK)
        return false
    }

    /**
     * Opens the PIN settings, asking for the PIN first if one is active and hasn't been entered.
     */
    fun openSettings(plr: Player) {
        if (state(plr) == PinState.ACTIVE && !plr.bankPinEntered) {
            openKeypad(plr, PinPurpose.SETTINGS)
        } else {
            showSettings(plr)
        }
    }

    /**
     * Opens the settings window and plays its sound.
     */
    private fun showSettings(plr: Player) {
        plr.overlays.open(PinSettingsInterface())
        plr.playSound(Sound.PIN_PENDING)
    }

    /**
     * Opens the keypad, unless it is locked after too many wrong PINs.
     */
    fun openKeypad(plr: Player, purpose: PinPurpose, firstEntry: String? = null) {
        val locked = plr.bankPinLockedUntil - System.currentTimeMillis()
        if (locked > 0) {
            plr.sendMessage("You must wait ${waitText(locked)} before you can try your PIN again.")
            return
        }
        plr.overlays.open(PinEntryInterface(purpose, firstEntry))
    }

    /**
     * A wait such as "10 seconds" or "2 minutes", rounded up.
     */
    private fun waitText(millis: Long): String {
        val seconds = (millis + 999) / 1000
        if (seconds < 60) {
            return "$seconds ${if (seconds == 1L) "second" else "seconds"}"
        }
        val minutes = (seconds + 59) / 60
        return "$minutes ${if (minutes == 1L) "minute" else "minutes"}"
    }

    /**
     * Acts on a keypad entry once all four digits are in.
     */
    fun entered(plr: Player, keypad: PinEntryInterface) {
        val pin = keypad.entered.toString()
        when (keypad.purpose) {
            PinPurpose.BANK, PinPurpose.SETTINGS -> checkPin(plr, pin, keypad.purpose)
            PinPurpose.NEW_PIN -> openKeypad(plr, PinPurpose.CONFIRM_PIN, pin)
            PinPurpose.CONFIRM_PIN -> confirmNewPin(plr, keypad.firstEntry!!, pin)
        }
    }

    private fun checkPin(plr: Player, pin: String, purpose: PinPurpose) {
        if (pin != plr.bankPin) {
            plr.overlays.closeWindows()
            plr.playSound(Sound.PIN_CANCEL)
            plr.bankPinWrongTries++
            val tier = plr.bankPinWrongTries.coerceAtMost(LOCK_SECONDS.lastIndex)
            val lock = TimeUnit.SECONDS.toMillis(LOCK_SECONDS[tier])
            plr.sendMessage("The PIN you entered was incorrect.")
            if (lock > 0) {
                plr.bankPinLockedUntil = System.currentTimeMillis() + lock
                plr.sendMessage("You must wait ${waitText(lock)} before you can try your PIN again.")
            }
            return
        }
        plr.bankPinEntered = true
        plr.bankPinWrongTries = 0
        if (plr.bankPinDeleteAt > 0) {
            plr.bankPinDeleteAt = 0
            plr.sendMessage("Your PIN is no longer going to be deleted.")
        }
        plr.overlays.closeWindows()
        when (purpose) {
            PinPurpose.BANK -> plr.bank.open()
            else -> showSettings(plr)
        }
    }

    private fun confirmNewPin(plr: Player, first: String, second: String) {
        val settings = PinSettingsInterface()
        plr.overlays.open(settings)
        if (first != second) {
            settings.refresh(plr, "The PINs you entered didn't match. Your PIN has not been changed.")
            return
        }
        if (state(plr) == PinState.ACTIVE) {
            plr.bankPin = second
            plr.bankPinEntered = true
            settings.refresh(plr, "Your PIN has been changed.")
        } else {
            plr.bankPinPending = second
            plr.bankPinPendingAt = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(plr.bankPinRecoveryDays.toLong())
            settings.refresh(plr)
        }
    }

    /**
     * Asks to delete a forgotten PIN after the recovery delay.
     */
    fun forgot(plr: Player) {
        plr.overlays.closeWindows()
        if (state(plr) != PinState.ACTIVE) {
            return
        }
        if (plr.bankPinDeleteAt == 0L) {
            plr.bankPinDeleteAt = System.currentTimeMillis() +
                    TimeUnit.DAYS.toMillis(plr.bankPinRecoveryDays.toLong())
        }
        plr.sendMessage("Your PIN will be deleted in ${dayText(daysUntil(plr.bankPinDeleteAt))}.")
    }

    /**
     * Swaps the recovery delay between three and seven days.
     */
    fun toggleDelay(plr: Player) {
        plr.bankPinRecoveryDays = if (plr.bankPinRecoveryDays == LONG_DELAY) SHORT_DELAY else LONG_DELAY
    }

    /**
     * Deletes the active PIN.
     */
    fun delete(plr: Player) {
        plr.bankPin = ""
        plr.bankPinDeleteAt = 0
    }

    /**
     * Cancels the PIN waiting to take effect.
     */
    fun cancelPending(plr: Player) {
        plr.bankPinPending = ""
        plr.bankPinPendingAt = 0
    }
}
