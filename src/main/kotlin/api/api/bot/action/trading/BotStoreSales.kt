package api.bot.action.trading

import api.bot.action.BotActionHandler
import api.bot.script.BotScript.ActionSafety
import api.bot.zone.SubZone
import api.predef.ext.get
import api.predef.ext.totalItemAmount
import api.predef.world
import io.luna.game.model.EntityState
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.BuyPolicy
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.bot.Bot

/**
 * Shares shop-sale retention, access checks and verified transactions between ordinary sales and bank maintenance.
 * Equipment and supply retention is refreshed per batch; new supply and explicit wanted targets are rechecked per click.
 *
 * @author lare96
 */
class BotStoreSales(private val bot: Bot, private val handler: BotActionHandler) {

    companion object {
        /**
         * Preserves custom wanted quantities without treating the shared default catalogue as a bank reservation.
         * Wanted-item equality compares ids only, so every persisted setting is compared explicitly.
         */
        fun retainedStock(bot: Bot): Map<Int, Int> {
            val retained = BotTradeIntentions.retainedStock(bot).toMutableMap()
            for (wanted in bot.preferences.wantedItems) {
                val default = WantedItemDefinition.DEFAULT.get(wanted.id()).orElse(null)
                if (default == null || wanted.min() != default.min() || wanted.target() != default.target() ||
                    wanted.skill() != default.skill() || wanted.maxLevel() != default.maxLevel() ||
                    wanted.priority() != default.priority() || wanted.tags() != default.tags()) {
                    val id = ItemDefinition.ALL.get(wanted.id()).map { it.unnotedId.orElse(wanted.id()) }
                        .orElse(wanted.id())
                    retained[id] = maxOf(retained[id] ?: 0, wanted.min().coerceAtLeast(0), wanted.target().coerceAtLeast(0))
                }
            }
            return retained
        }
    }

    private var retained = emptyMap<Int, Int>()

    /**
     * Rebuilds account-wide equipment and supply retention before starting a new withdrawal batch.
     */
    fun refreshRetention() {
        retained = retainedStock(bot)
    }

    /**
     * Prefers bank notes when an accessible shop accepts them, otherwise keeps the ordinary item form.
     */
    fun saleId(id: Int): Int {
        val definition = ItemDefinition.ALL.retrieve(id)
        if (definition.isStackable) return id
        val noted = definition.notedId.orElse(id)
        return if (noted != id && shopsFor(noted).isNotEmpty()) noted else id
    }

    /**
     * Counts actual transferable surplus using the current batch's equipment and the latest supply reservations.
     */
    fun sellableAmount(id: Int): Int {
        if (bot.preferences.isMerchant) return 0
        val wanted = bot.preferences.getWantedItem(id)
        val default = WantedItemDefinition.DEFAULT.get(id).orElse(null)
        val explicit = if (wanted != null && (default == null || wanted.min() != default.min() ||
                wanted.target() != default.target() || wanted.skill() != default.skill() ||
                wanted.maxLevel() != default.maxLevel() || wanted.priority() != default.priority() ||
                wanted.tags() != default.tags())) maxOf(wanted.min(), wanted.target(), 0) else 0
        val reserve = maxOf(retained[id] ?: 0, BotTradeIntentions.supplyTarget(bot, id), explicit)
        val transferable = (bot.inventory.filterNotNull() + bot.bank.filterNotNull()).asSequence()
            .filter { !it.isDynamic && it.itemDef.unnotedId.orElse(it.id) == id }
            .sumOf { it.amount.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        return minOf(transferable,
            (bot.totalItemAmount(id).toLong() - reserve).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt())
    }

    /**
     * Finds accepting GP shops with current stock capacity. General stores receive notes for noteable items.
     * Native selling remains authoritative after suspension.
     */
    fun shopsFor(id: Int): List<Shop> = world.shops.values().filter {
        it.currency == Currency.COINS && it.botAccess.apply(bot) && it.computeCanSell(id) &&
            (it.buyPolicy != BuyPolicy.ALL || ItemDefinition.ALL.retrieve(id).isStackable ||
                ItemDefinition.ALL.retrieve(id).notedId.isEmpty) &&
            it.items.hasSpaceFor(Item(id))
    }

    /**
     * Travels to the same home shop-access area used by ordinary bot sales.
     */
    suspend fun travelHome(): Boolean = safe() &&
        (SubZone.HOME in bot.subZones || handler.webWalkTo(SubZone.HOME)) && safe()

    /**
     * Opens an authorized shop only from the home shop-access area.
     */
    fun openShop(shop: Shop): Boolean {
        if (!safe() || SubZone.HOME !in bot.subZones || !shop.botAccess.apply(bot)) return false
        bot.overlays.closeWindows()
        bot.overlays.open(ShopInterface(shop))
        return bot.overlays[ShopInterface::class]?.shop === shop
    }

    /**
     * Sells a bounded carried quantity through native widget actions, verifying each decrease and currency capacity.
     * The caller may impose stricter retention or session bounds through [canContinue].
     */
    suspend fun sellItem(shop: Shop, id: Int, maxAmount: Int, maximumActions: Int = Int.MAX_VALUE,
                         canContinue: () -> Boolean = { true }, beforeAction: () -> Unit = {}): Int {
        var sold = 0
        var actions = 0
        while (safe() && canContinue() && actions < maximumActions &&
            bot.overlays[ShopInterface::class]?.shop === shop && SubZone.HOME in bot.subZones &&
            shop.botAccess.apply(bot) && shop.computeCanSell(id) && shop.items.hasSpaceFor(Item(id))) {
            if (bot.inventory.any { it != null && it.id == id && it.isDynamic }) break
            val baseId = ItemDefinition.ALL.retrieve(id).unnotedId.orElse(id)
            val held = bot.inventory.computeAmountForId(id)
            val available = minOf(maxAmount - sold, sellableAmount(baseId), held)
            if (available < 1 || !bot.inventory.hasSpaceFor(Item(995))) break
            val before = bot.inventory.computeAmountForId(id)
            actions++
            beforeAction()
            val success = when {
                available >= minOf(100, held) -> handler.shop.sell100(id)
                available >= 10 -> handler.shop.sell10(id)
                available >= 5 -> handler.shop.sell5(id)
                else -> handler.shop.sell1(id)
            }.await()
            if (!safe()) break
            val decrease = before - bot.inventory.computeAmountForId(id)
            if (!success || decrease < 1) break
            sold += decrease
        }
        return sold
    }

    private fun safe(): Boolean = !bot.preferences.isMerchant && bot.state == EntityState.ACTIVE &&
        ActionSafety.NON_COMBAT.allows(bot)
}
