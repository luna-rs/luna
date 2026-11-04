package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.script.DynamicBotScript
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration.Companion.milliseconds

/**
 * Defers new work after bank recovery fails, without retrying travel, acquiring supplies or collecting more goods.
 * The absolute deadline continues through interruptions, and each wait yields cooperatively to the game loop.
 *
 * @property deadline Epoch-millisecond expiry of this bounded wait.
 * @author lare96
 */
class BankRecoveryWaitBotScript(bot: Bot, private val deadline: Long) : DynamicBotScript(bot) {
    override fun allowsLoot(item: GroundItem): Boolean = false

    override suspend fun run(): Boolean {
        val remaining = deadline - System.currentTimeMillis()
        if (remaining <= 0L) return true
        delay(minOf(remaining, 1_000L).milliseconds)
        return System.currentTimeMillis() >= deadline
    }
}
