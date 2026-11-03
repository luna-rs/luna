package game.bot.scripts

import api.bot.action.BotLightSources
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.player.item.consume.food.Food
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot

/**
 * Owned supplies for a Giant Mole outing. Sealed lit lights survive the mole's mud; the native light-source table
 * supplies their brightness and item IDs. Owned unlit sources are lit with an owned tinderbox through native item use.
 * A worn mining helmet counts without being removed, and bank visits restore carried supplies before travel.
 *
 * @author lare96
 */
internal object GiantMoleTrip {

    /**
     * Native digging tool and the stronger food stock carried on one boss trip.
     */
    const val SPADE = 952
    const val FOOD_COUNT = 20
    const val MINIMUM_HEAL = 16

    /**
     * Tools and a real lit source are required independently of food remaining after an interrupted fight.
     */
    fun suppliesOwned(bot: Bot): Boolean = bot.itemTracker.contains(SPADE) && lightOwned(bot)

    /**
     * Shares owned sealed-light admission with ordinary dark-cave assignments.
     */
    fun lightOwned(bot: Bot): Boolean = BotLightSources.owned(bot, sealed = true) != null

    /**
     * Verifies real illumination without requiring a digging tool outside the Mole's lair.
     */
    fun lightCarried(bot: Bot): Boolean = BotLightSources.carried(bot, sealed = true)

    /**
     * Admission requires enough owned food of a usable healing strength for the shared boss banking policy.
     */
    fun ready(bot: Bot): Boolean = suppliesOwned(bot) && Food.ID_TO_FOOD.filterValues {
        it.heal >= MINIMUM_HEAL
    }.keys.sumOf { bot.itemTracker.count(it).toLong() } >= FOOD_COUNT

    /**
     * Rechecks physical supplies before attacks and after saved-session restoration or item loss.
     */
    fun carried(bot: Bot): Boolean = bot.inventory.contains(SPADE) && lightCarried(bot)

    /**
     * Collects existing tools through native banking and verifies delivery, retaining an already worn lit helmet.
     */
    suspend fun collect(bot: Bot): Boolean {
        if (!bot.inventory.contains(SPADE) && (!bot.actionHandler.banking.withdraw(Item(SPADE)) ||
                    !bot.inventory.contains(SPADE))) return false
        return collectLight(bot)
    }

    /**
     * Withdraws an owned enclosed light and, when needed, its tinderbox through ordinary banking.
     */
    suspend fun collectLight(bot: Bot): Boolean = BotLightSources.collect(bot, sealed = true)

    /**
     * Lights a collected source after the bank is closed, verifying the actual item transformation before travel.
     * Failed native clicks or interrupted lighting cannot authorize a dark-cave attack.
     */
    suspend fun ensureLit(bot: Bot): Boolean {
        return bot.inventory.contains(SPADE) && ensureLight(bot)
    }

    /**
     * Uses the existing light-source item transformation after closing the bank.
     */
    suspend fun ensureLight(bot: Bot): Boolean = BotLightSources.ensureLit(bot, sealed = true)
}
