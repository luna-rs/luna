package game.content.bankPin

import io.luna.game.model.mob.Player

/**
 * Where a player's Bank PIN stands.
 *
 * @param status The status line on the settings window.
 * @author TheLining
 */
enum class PinState(val status: String) {

    /**
     * No PIN is set or waiting.
     */
    NONE("No PIN set") {
        override fun messages(plr: Player) = listOf(WARNING)
    },

    /**
     * A new PIN is waiting out the recovery delay.
     */
    PENDING("PIN coming soon") {
        override fun messages(plr: Player): List<String> {
            val days = BankPin.daysUntil(plr.bankPinPendingAt)
            return listOf("You have requested that a PIN be set on your bank account. This will take effect in " +
                                  "$days ${if (days == 1L) "day" else "days"}.", WARNING)
        }
    },

    /**
     * A PIN locks the bank.
     */
    ACTIVE("You have a PIN") {
        override fun messages(plr: Player) = listOf(WARNING)
    };

    /**
     * The lines for the settings window's message panel.
     */
    abstract fun messages(plr: Player): List<String>

    companion object {

        /**
         * The reminder shown on the settings window.
         */
        const val WARNING = "Customers are reminded that they should NEVER tell anyone their Bank PINs or " +
                "passwords, nor should they ever enter their PINs on any website form."
    }
}
