package api.bot.action

import api.bot.Suspendable.waitFor
import api.bot.script.BotScript.ActionSafety
import api.combat.death.DeathHookHandler.deathItems
import api.predef.ext.rebalanceWantedItems
import io.luna.game.model.EntityState
import io.luna.game.model.item.GroundItem
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

    /**
     * Uses the world's total guide value, including stack quantity and native noted-item pricing.
     */
    fun value(item: GroundItem): Long = bot.world.economy.getTotalPrice(item.toItem()).coerceAtLeast(0)

    /**
     * Keeps personal death recovery first and wanted supplies second, then orders each group by descending total value.
     * Equal-value drops prefer the nearer tile. Values are quoted once for this synchronous selection only.
     * Items that cannot currently fit remain in the list because a caller may make space before attempting them.
     */
    fun ordered(items: Iterable<GroundItem>): List<GroundItem> {
        val values = items.associateWith(::value)
        return values.keys.sortedWith(compareBy<GroundItem> {
            when {
                it in bot.deathItems -> 0
                bot.preferences.needsWantedItem(it.id) -> 1
                else -> 2
            }
        }.thenByDescending { values.getValue(it) }.thenBy { bot.position.computeLongestDistance(it.position) })
    }

    /**
     * Rechecks native capacity, visibility and the owning activity's loot restrictions without changing any items.
     * Existing stacks can fit in a full inventory, while overflowing stacks and dynamic items obey native slot rules.
     */
    fun canPickup(item: GroundItem): Boolean {
        if (bot.state != EntityState.ACTIVE || !ActionSafety.COMBAT.allows(bot) ||
            item.state != EntityState.ACTIVE || !item.isVisibleTo(bot) ||
            !bot.inventory.hasSpaceFor(item.toItem())) return false
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
