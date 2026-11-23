package game.content.bankPin

/**
 * What a PIN typed on the keypad is for.
 *
 * @param heading The line shown above the keypad.
 * @param forgettable If the keypad offers "I don't know it.", which asks to delete the PIN.
 * @author TheLining
 */
enum class PinPurpose(val heading: String, val forgettable: Boolean) {

    /**
     * Opening the bank.
     */
    BANK("Please enter your FOUR DIGIT PIN using the buttons below.", true),

    /**
     * Opening the PIN settings.
     */
    SETTINGS("Please enter your FOUR DIGIT PIN using the buttons below.", true),

    /**
     * Choosing a new PIN.
     */
    NEW_PIN("Please enter your NEW PIN using the buttons below.", false),

    /**
     * Typing the new PIN again.
     */
    CONFIRM_PIN("Please confirm your NEW PIN using the buttons below.", false)
}
