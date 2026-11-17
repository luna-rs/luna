package api.bot.action

import api.bot.script.BotScript.ActionSafety
import game.skill.cooking.prepareFood.PieDishes
import io.luna.game.model.EntityState
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Empties carried burnt pies using their ordinary inventory action before a cooking trip is banked.
 * Only verified native replacements count as progress; no withdrawal, ingredient or dish is synthesized.
 *
 * @author lare96
 */
object BotPieMaintenance {

    /**
     * Recovers at most one inventory's worth of dishes, stopping once 28 empty dishes are already owned.
     * Permission loss, stale slots and unhandled clicks leave the actual remaining possessions untouched.
     */
    suspend fun recycleCarried(bot: Bot, allowed: () -> Boolean = { true }): Int {
        fun permitted() = allowed() && bot.state == EntityState.ACTIVE && ActionSafety.NON_COMBAT.allows(bot)
        fun dishes() = bot.inventory.computeAmountForId(PieDishes.DISH).toLong() +
            bot.bank.computeAmountForId(PieDishes.DISH)
        var progress = 0
        withTimeoutOrNull(30.seconds) {
            repeat(bot.inventory.capacity()) {
                if (!permitted() || dishes() >= 28) return@withTimeoutOrNull
                val slot = bot.inventory.withIndex().firstOrNull { (_, item) ->
                    item?.id == PieDishes.BURNT_PIE && item.amount == 1 && !item.isDynamic && !item.itemDef.isNoted
                } ?: return@withTimeoutOrNull
                val pie = slot.value
                if (!bot.actionHandler.inventory.clickItem(1, PieDishes.BURNT_PIE, slot.index)) return@withTimeoutOrNull
                val recovered = withTimeoutOrNull(3.seconds) wait@ {
                    while (true) {
                        val current = bot.inventory[slot.index]
                        if (current?.id == PieDishes.DISH && current.amount == 1 &&
                            !current.isDynamic && !current.itemDef.isNoted) return@wait true
                        if (!permitted() || current != pie) return@wait false
                        delay(100)
                    }
                } == true
                if (!recovered) return@withTimeoutOrNull
                progress++
            }
        }
        return progress
    }
}
