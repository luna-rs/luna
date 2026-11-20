package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.script.BotScript
import engine.bot.coordinator.RuneRunningCoordinator
import kotlin.time.Duration.Companion.seconds
import io.luna.game.model.mob.bot.Bot

/**
 * Publishes a runner's decision while a prospective host volunteers or finishes preparing.
 * An expired or interrupted request returns the bot to its ordinary activity selection.
 *
 * @author lare96
 */
class RuneRunningWaitBotScript(bot: Bot, internal val introductory: Boolean) : BotScript(bot) {

    override val actionSafety = ActionSafety.COMBAT

    override suspend fun init(resumed: Boolean): Boolean =
        resumed || !RuneRunningCoordinator.submit(this)

    override suspend fun run(): Boolean {
        val runner = RuneRunningCoordinator.match(this)
        if (runner != null) {
            bot.scriptStack.softPushHead(runner)
            return true
        }
        if (!RuneRunningCoordinator.waiting(this)) return true
        delay(2.seconds)
        return false
    }

    override fun paused() {
        RuneRunningCoordinator.withdraw(this)
    }

    override suspend fun finish() {
        RuneRunningCoordinator.withdraw(this)
    }

    override fun snapshot() = null
}
