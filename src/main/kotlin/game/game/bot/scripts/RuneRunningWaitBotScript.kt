package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.script.BotScript
import engine.bot.coordinator.RuneRunningCoordinator
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration.Companion.seconds

/**
 * Keeps an introductory runner available while a supplied crafter prepares a bounded native trip.
 * Missing or abandoned hosts immediately release the account to ordinary activity selection.
 *
 * @author lare96
 */
class RuneRunningWaitBotScript(bot: Bot, private val deadline: Long) : BotScript(bot) {
    override val allowsCombatResponse = false
    override val actionSafety = ActionSafety.COMBAT

    override fun snapshot() = null

    override suspend fun run(): Boolean {
        if (System.currentTimeMillis() >= deadline || !bot.preferences.runeRunning.isIntroductory ||
            !isActionSafe(ActionSafety.NON_COMBAT)) return true
        RuneRunningCoordinator.waitingRunner(bot)?.let {
            bot.scriptStack.pushTail(it)
            return true
        }
        if (!RuneRunningCoordinator.hasPreparingHost(bot)) return true
        delay(5.seconds)
        return false
    }
}
