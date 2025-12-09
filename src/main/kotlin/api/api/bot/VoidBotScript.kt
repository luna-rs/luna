package api.bot

import io.luna.game.model.mob.bot.Bot

/**
 * A no-op placeholder script used when a [DynamicBotScript] was previously saved and encountered during reload.
 *
 * Dynamic scripts are not persisted; when the script stack is reconstructed, any entry that corresponds to a dynamic script is replaced by this
 * [VoidBotScript] so that:
 * - The deserialization pipeline remains consistent.
 * - The non-persisted dynamic behaviour is effectively discarded.
 *
 * After loading, this script is expected to be removed from the bot's script stack during the normal stack
 * reconciliation process.
 */
class VoidBotScript(bot: Bot) : DynamicBotScript(bot) {

    /**
     * Entry point for the script runtime.
     *
     * This implementation intentionally does nothing. It exists purely to satisfy the [BotScript] contract while
     * representing an "empty" or discarded dynamic script slot.
     */
    override suspend fun run() { }
}
