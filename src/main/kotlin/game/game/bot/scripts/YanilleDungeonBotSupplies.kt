package game.bot.scripts

import api.bot.Suspendable.waitFor
import api.predef.*
import game.player.item.consume.food.Food
import game.player.item.consume.potion.Potion
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration.Companion.seconds

/**
 * Owned food and native antipoison preparation for trips through the Yanille Dungeon.
 *
 * @author lare96
 */
internal object YanilleDungeonBotSupplies {

    /**
     * Stronger protection first, retaining every native dose as usable stock.
     */
    val antipoisons = listOf(Potion.ANTIDOTE_PLUS_PLUS, Potion.ANTIDOTE_PLUS,
        Potion.SUPER_ANTIPOISON, Potion.ANTIPOISON_POTION).flatMap { it.doses.reversed() }

    /**
     * Whether the bank or inventory can supply a cure and useful food without purchasing anything.
     */
    fun owned(bot: Bot): Boolean = antipoisons.any { it in bot.inventory || it in bot.bank } &&
        (bot.inventory + bot.bank).filterNotNull().any { (Food.ID_TO_FOOD[it.id]?.heal ?: 0) >= 10 }

    /**
     * Reserves a real antipoison before food fills the inventory.
     */
    suspend fun withdrawAntipoison(bot: Bot): Boolean {
        if (antipoisons.any { it in bot.inventory }) return true
        val id = antipoisons.firstOrNull { it in bot.bank } ?: return false
        return bot.actionHandler.banking.withdraw(Item(id)) && antipoisons.any { it in bot.inventory }
    }

    /**
     * Drinks through the normal item packet and verifies immunity, including native potion cooldowns.
     */
    suspend fun protect(bot: Bot): Boolean {
        if (bot.status.isAntiPoison()) return true
        if (!bot.isAlive || bot.isLocked || bot.overlays.hasWindow()) return false
        val id = antipoisons.firstOrNull { it in bot.inventory } ?: return false
        if (!bot.actionHandler.inventory.clickItem(1, id)) return false
        return waitFor(3.seconds) { bot.status.isAntiPoison() }
    }

    /**
     * Takes a verified bite when hurt, leaving combat and obstacle damage to their native handlers.
     */
    suspend fun eatIfHurt(bot: Bot): Boolean {
        if (bot.health > maxOf(12, bot.hitpoints.staticLevel / 2)) return true
        val food = bot.inventory.withIndex().firstOrNull {
            it.value?.id?.let { id -> (Food.ID_TO_FOOD[id]?.heal ?: 0) >= 10 } == true
        } ?: return false
        return bot.actionHandler.inventory.eat(food.index, food.value!!.id) && bot.health > 12
    }
}
