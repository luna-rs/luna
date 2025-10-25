package game.player

/**
 * A collection of common chatbox message constants.
 *
 * @author lare96
 */
enum class Messages(val text: String) {
    INVENTORY_FULL("You do not have enough space in your inventory."),
    BUSY("You are busy."),
    INTERACT_BUSY("That player is busy.")
}