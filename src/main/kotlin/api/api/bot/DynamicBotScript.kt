package api.bot

import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.script.BotScriptSnapshot

/**
 * A base class for non-persistent, runtime-only bot scripts. These do not need to be registered within
 * `registerScript.kts`.
 *
 * Dynamic scripts are intended for temporary behaviours that:
 * - Do **not** need to survive server restarts or player relogs.
 * - Should be skipped entirely by the bot script persistence layer.
 *
 * Persistence integration details:
 * - The script is always serialized as a `null` snapshot.
 * - During load, the script is treated as a [VoidBotScript] and is skipped.
 *
 * This makes {@code DynamicBotScript} ideal for:
 * - Ephemeral behaviours (e.g., temporary tasks, experimentation).
 * - Transient state machines that can be safely reconstructed on login.
 */
abstract class DynamicBotScript(bot: Bot) : BotScript<Void?>(bot) {

    /**
     * Produces a non-persistent snapshot of this script.
     *
     * This implementation always returns `null`, which signals to the persistence system that there is nothing to
     * store for this script. As a result, the dynamic script is effectively invisible to long-term storage.
     *
     * @return Always `null`, indicating no state should be persisted.
     */
    override fun snapshot(): Void? {
        return null
    }
}
