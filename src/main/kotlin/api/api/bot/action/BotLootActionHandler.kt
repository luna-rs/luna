package api.bot.action

import api.bot.Suspendable.waitFor
import api.bot.action.eating.CarriedFood
import api.bot.script.BotScript.ActionSafety
import api.combat.death.DeathHookHandler.deathItems
import api.predef.ext.rebalanceWantedItems
import game.player.item.consume.food.Food
import io.luna.game.model.EntityState
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration.Companion.seconds

/**
 * Shared ground-loot ordering and verified pickup for combat visits and the loot reflex.
 * Callers choose eligible items and whether to discard junk; this handler never creates space by dropping supplies.
 * Native interaction, visibility and container rules remain authoritative throughout the web approach.
 *
 * @property bot The bot collecting the loot.
 * @property handler The existing action handler used for native ground-item clicks.
 * @author lare96
 */
class BotLootActionHandler(private val bot: Bot, private val handler: BotActionHandler) {

    companion object {
        const val VALUABLE_LOOT_VALUE = 5000L
    }

    /**
     * Ground-loot priorities for ordinary pickups, player fights, and monster training.
     *
     * @author lare96
     */
    enum class Purpose {
        GENERAL, PLAYER_COMBAT, NPC_COMBAT
    }

    /**
     * An eligible drop and the scores calculated for this selection.
     *
     * @author lare96
     */
    private data class Candidate(val item: GroundItem, val priority: Int, val value: Long)

    /**
     * Uses the world's total guide value, including stack quantity and native noted-item pricing.
     */
    fun value(item: GroundItem): Long = bot.world.economy.getTotalPrice(item.toItem()).coerceAtLeast(0)

    /**
     * Keeps personal recovery first. After player fights, a bot without healing food seeks food before other supplies
     * and prioritizes coins and valuable gear ahead of spare consumables. Monster training favors stackable loot
     * alongside valuable individual drops. Items can remain eligible when a caller can make room for them.
     */
    @JvmOverloads
    fun ordered(items: Iterable<GroundItem>, purpose: Purpose = Purpose.GENERAL): List<GroundItem> {
        val needsFood = purpose == Purpose.PLAYER_COMBAT && CarriedFood.of(bot).none { it.food.heal > 0 }
        val recovery = bot.deathItems
        val position = bot.position
        return items.distinct().map { item ->
            val guideValue = value(item)
            val priority = when {
                item in recovery -> 0
                needsFood && (Food.ID_TO_FOOD[item.id]?.heal ?: 0) > 0 -> 1
                bot.preferences.needsWantedItem(item.id) -> 2
                purpose == Purpose.PLAYER_COMBAT ->
                    if (item.id == Currency.COINS.id || guideValue > VALUABLE_LOOT_VALUE) 3 else 4
                purpose == Purpose.NPC_COMBAT ->
                    if (guideValue > VALUABLE_LOOT_VALUE || item.toItem().itemDef.isStackable) 3 else 4
                else -> 3
            }
            Candidate(item, priority, guideValue)
        }.sortedWith(compareBy<Candidate> { it.priority }.thenByDescending { it.value }
            .thenBy { position.computeLongestDistance(it.item.position) }).map { it.item }
    }

    /**
     * Keeps these drops eligible even when they fall below a combat script's ordinary item or value filter.
     */
    fun isRecoveryOrWanted(item: GroundItem): Boolean =
        item in bot.deathItems || bot.preferences.needsWantedItem(item.id)

    /**
     * Rechecks native capacity, visibility and the owning activity's loot restrictions without changing any items.
     * Existing stacks can fit in a full inventory, while overflowing stacks and dynamic items obey native slot rules.
     */
    fun canPickup(item: GroundItem): Boolean {
        if (bot.state != EntityState.ACTIVE || !ActionSafety.COMBAT.allows(bot) ||
            item.state != EntityState.ACTIVE || !item.isVisibleTo(bot) ||
            !bot.inventory.hasSpaceFor(item.toItem())) return false
        if (item !in bot.deathItems && BotBankSpace.recentlyDisposed(bot, item.id)) return false
        val activity = bot.scriptStack.current()
        return activity == null || (item.id !in activity.ignoredLoot && activity.allowsLoot(item))
    }

    /**
     * Approaches through the existing web-walker interaction and sends an ordinary pickup click only while allowed.
     * Reaching the tile or seeing an item disappear is not collection: the carried quantity must actually increase.
     * A lost race or rejected click returns false after a bounded wait, leaving callers free to try another drop.
     */
    suspend fun pickup(item: GroundItem, allowed: () -> Boolean = { true }): Boolean {
        fun permitted() = allowed() && canPickup(item)
        if (!permitted()) return false
        val before = bot.inventory.computeAmountForId(item.id)
        if (handler.interactions.interact(1, item, ::permitted)) {
            waitFor(3.seconds) { bot.inventory.computeAmountForId(item.id) > before || !permitted() }
        }
        val collected = bot.inventory.computeAmountForId(item.id) > before
        if (collected) bot.preferences.rebalanceWantedItems()
        return collected
    }
}
