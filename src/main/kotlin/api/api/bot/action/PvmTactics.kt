package api.bot.action

import api.bot.action.eating.PvmEating
import api.bot.action.prayer.PvmPrayer
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.delay
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Looks after a bot while it fights monsters, the way a player keeps an eye on their hitpoints and prayer between
 * clicks: eating through [PvmEating], then praying through [PvmPrayer].
 *
 * @param bot The bot to look after.
 * @author lare96
 */
class PvmTactics(private val bot: Bot) {

    /**
     * Eats for the bot.
     */
    private val eating = PvmEating(bot)

    /**
     * Prays for the bot.
     */
    private val prayer = PvmPrayer(bot)

    /**
     * Looks after the bot once. Eating comes first, since it keeps the bot alive.
     *
     * @return `false` if the bot needs to leave and restock food.
     */
    suspend fun check(): Boolean {
        val fight = PvmFight.of(bot)
        val fed = eating.check(fight)
        prayer.check(fight)
        return fed
    }

    /**
     * Waits a natural moment while looking after the bot every tick.
     *
     * @param min The shortest wait.
     * @param max The longest wait.
     * @return `false` if the bot needs to leave and restock food.
     */
    suspend fun watch(min: Duration = 1200.milliseconds, max: Duration = 2400.milliseconds): Boolean {
        val end = System.currentTimeMillis() + Random.nextLong(min.inWholeMilliseconds, max.inWholeMilliseconds + 1)
        while (true) {
            if (!check()) {
                return false
            }
            val left = end - System.currentTimeMillis()
            if (left <= 0) {
                return true
            }
            delay(minOf(left, 600))
        }
    }

    /**
     * Called as the bot leaves the fight, to bank or to stop fighting. It may switch its prayers off to save points.
     */
    fun leaveFight() {
        prayer.leaveFight()
    }

    /**
     * Stops attending to the bot's hitpoints, handing them back to [game.bot.scripts.EatFoodReflexScript] right away.
     */
    fun stopAttending() {
        eating.stopAttending()
    }

    /**
     * How much food to take from the bank. See [PvmEating.foodToCarry].
     */
    fun foodToCarry(heal: Int, fighting: Int): Int = eating.foodToCarry(heal, fighting)

    /**
     * Whether the bot is too hurt to start a fight without food. See [PvmEating.needsFood].
     */
    fun needsFood(): Boolean = eating.needsFood()
}
