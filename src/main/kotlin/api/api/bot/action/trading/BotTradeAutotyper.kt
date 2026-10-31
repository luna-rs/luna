package api.bot.action.trading

/**
 * Repeats configured trade advertisements without regenerating their wording or formatting.
 *
 * An ordinary session configures one message. Merchants can configure a small rotation of messages describing
 * their real intentions. Reapplying the same configuration preserves the messages and the rotation cursor.
 *
 * @author lare96
 */
internal class BotTradeAutotyper {
    private var configuration: Any? = null
    private var messages = emptyList<BotTradeMessage>()
    private var cursor = 0

    /**
     * Rebuilds the messages only when the advertised items or prices change.
     */
    fun configure(key: Any, create: () -> List<BotTradeMessage>) {
        if (key == configuration) return
        configuration = key
        messages = create().toList()
        cursor = 0
    }

    /**
     * Returns the next preconfigured message, wrapping around the merchant rotation.
     */
    fun next(): BotTradeMessage? {
        if (messages.isEmpty()) return null
        val message = messages[cursor]
        cursor = (cursor + 1) % messages.size
        return message
    }
}
