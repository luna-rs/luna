package game.bot.scripts

import api.bot.action.BotTaverleyKeys
import api.bot.script.DynamicBotScript
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes

/**
 * Owns one dusty-key visit across normal combat reflex pauses.
 *
 * @author lare96
 */
class ObtainDustyKeyBotScript(bot: Bot, basket: Set<Int>) : DynamicBotScript(bot) {
    private val protectedItems = basket.toSet()
    private val deadline = System.currentTimeMillis() + 5.minutes.inWholeMilliseconds
    private var attackedJailer = false

    override suspend fun awaitActionSafety(safety: ActionSafety): Boolean {
        val remaining = deadline - System.currentTimeMillis()
        return remaining > 0 && withTimeoutOrNull(remaining) { super.awaitActionSafety(safety) } == true
    }

    override suspend fun run(): Boolean {
        val remaining = deadline - System.currentTimeMillis()
        if (remaining <= 0) return true
        val onAttack: (() -> Unit)? = if (attackedJailer) null else ({ attackedJailer = true })
        return withTimeoutOrNull(remaining) {
            BotTaverleyKeys.visit(bot, protectedItems, { bot.scriptStack.current() === this }, onAttack) !=
                BotTaverleyKeys.VisitResult.WAIT_FOR_FIGHT
        } ?: true
    }
}
