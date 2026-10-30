package api.bot.action

import api.bot.script.BotScript.ActionSafety
import api.bot.zone.SubZone
import api.predef.ext.get
import engine.controllers.Controllers.inWilderness
import game.item.degradable.jewellery.TeleportJewellery
import game.player.item.consume.food.Food
import game.player.item.consume.potion.Potion
import game.skill.magic.Rune
import io.luna.game.model.EntityState
import io.luna.game.model.def.AmmoDefinition
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.def.WeaponDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPkPreference
import io.luna.game.model.mob.combat.Weapon
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes

/**
 * Funds a concrete PK kit from existing gold and non-combat belongings, then uses ordinary wanted-item purchases.
 * Actual shop quotes and inventory changes govern every sale. Missing shop stock never triggers liquidation.
 *
 * @author lare96
 */
internal class PkSupplyAcquisition(private val bot: Bot, private val handler: BotActionHandler) {
    private val selection = PkLoadoutSelection(bot)

    /**
     * Combat equipment and consumables remain reserved even when they are absent from today's selected switches.
     * Notes are classified by their physical item, and dynamic items are never sold.
     */
    internal fun protectedItem(item: Item, reserved: Set<Int> = emptySet()): Boolean {
        val id = item.unnotedItemDef.id()
        if (item.isDynamic || !item.unnotedItemDef.isTradeable || id == 995 || id in reserved ||
            id in Rune.ID_TO_RUNE || id in Food.ID_TO_FOOD || BotPkPreference.isTeamCape(id) ||
            Potion.values().any { id in it.doses } ||
            TeleportJewellery.values().any { id in it.items || id == it.emptyId } || id in 8007..8015 || id == 4251 ||
            AmmoDefinition.ALL.values.any { id in it.ammo }) return true
        val weapon = WeaponDefinition.ALL.get(id).orElse(null)
        if (weapon != null && weapon.type !in setOf(Weapon.PICKAXE, Weapon.SICKLE)) return true
        val equipment = EquipmentDefinition.ALL.get(id).orElse(null) ?: return false
        return (0..10).any { equipment.getBonus(it) > 0 } && weapon == null
    }

    /**
     * Obtains a complete requested stock target. The whole funding/purchase visit has one five-minute deadline.
     * Failed or partial purchases remain owned, and departure still requires a fresh loadout/risk assessment.
     */
    suspend fun obtain(required: Map<Int, Int>): Boolean = withTimeoutOrNull(5.minutes) {
        if (!safe()) return@withTimeoutOrNull false
        for ((id, amount) in required) bot.preferences.raiseWantedItemTarget(id, amount)
        try {
            // Quote all missing items together so funding does not repeatedly sell and return to the bank.
            val cost = quoteMissing(required)
            if (cost != null && coins() < cost && !fund(cost, required.keys)) return@withTimeoutOrNull false
            for ((id, amount) in required) {
                if (!safe() || !handler.shop.obtainWantedItem(id, amount) || !safe()) return@withTimeoutOrNull false
                if (bot.inventory.computeAmountForId(id).toLong() + bot.equipment.computeAmountForId(id) < amount) {
                    return@withTimeoutOrNull false
                }
                // Requote remaining shortages after dynamic shop prices or somebody else's purchases change stock.
                val remainingCost = quoteMissing(required)
                if (remainingCost != null && coins() < remainingCost &&
                    !fund(remainingCost, required.keys)) return@withTimeoutOrNull false
            }
            true
        } finally {
            bot.overlays.closeWindows()
        }
    } == true

    /**
     * Quotes only obtainable missing quantities, respecting stock shared between the candidate shops.
     * Null means that this visit cannot source a full kit, so selling belongings would be premature.
     */
    internal fun quoteMissing(required: Map<Int, Int>): Long? {
        var total = 0L
        for ((id, amount) in required) {
            var missing = (amount - selection.owned(id)).coerceAtLeast(0)
            val shops = bot.world.shops.values().filter {
                it.currency == Currency.COINS && id in it.itemIds && it.botAccess.apply(bot) &&
                    it.items.computeAmountForId(id) > 0
            }.sortedBy { it.computeBuyValue(id, 1).orElse(Int.MAX_VALUE) }
            for (shop in shops) {
                if (missing == 0) break
                val count = minOf(missing, shop.items.computeAmountForId(id))
                val quote = shop.computeBuyValue(id, count).orElse(-1)
                if (quote < 0) continue
                total += quote.toLong() + count // Bound per-click integer rounding in the one-item acquisition API.
                missing -= count
            }
            if (missing > 0) return null
        }
        return total
    }

    /**
     * Sells only enough non-PK stock to cover this concrete funding target, with bounded actions and shop visits.
     * Wanted skilling stock may be sold as explicitly requested; PK supplies never become sale candidates.
     */
    internal suspend fun fund(target: Long, reserved: Set<Int>): Boolean {
        if (target < 0 || target > Int.MAX_VALUE || !safe()) return false
        if (coins() >= target) return true
        if (!handler.banking.travelToBankOpen() || !safe() || !handler.banking.depositInventory() || !safe()) return false
        val candidates = bot.bank.filterNotNull().filter { !protectedItem(it, reserved) }
            .map { it.id }.distinct().sortedByDescending { id ->
                val price = shopsForSale(id).maxOfOrNull { it.computeSellValue(id, 1).coerceAtLeast(0) } ?: 0
                price.toLong() * bot.bank.computeAmountForId(id)
            }.take(16)
        var actions = 0
        for (id in candidates) {
            val failed = HashSet<Shop>()
            while (safe() && coins() < target && actions < 120) {
                val shop = shopsForSale(id).filter { it !in failed }.maxByOrNull { it.computeSellValue(id, 1) } ?: break
                if (!handler.banking.travelToBankOpen() || !safe() || !handler.banking.depositInventory() || !safe()) return false
                handler.banking.clickBankingMode(false)
                val quote = shop.computeSellValue(id, 1).coerceAtLeast(1)
                val needed = ((target - coins() + quote - 1) / quote).coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
                val capacity = if (Item(id).itemDef.isStackable) needed else 27
                val amount = minOf(needed, capacity, bot.bank.computeAmountForId(id))
                if (amount < 1 || !handler.banking.withdraw(Item(id, amount)) || !safe()) break
                if (SubZone.HOME !in bot.subZones && !handler.webWalkTo(SubZone.HOME)) return false
                if (!safe() || !shop.botAccess.apply(bot)) return false
                bot.overlays.closeWindows()
                bot.overlays.open(ShopInterface(shop))
                var progressed = false
                while (safe() && coins() < target && actions < 120 &&
                    bot.overlays[ShopInterface::class]?.shop === shop && shop.botAccess.apply(bot) && shop.computeCanSell(id)) {
                    val held = bot.inventory.computeAmountForId(id)
                    if (held < 1 || protectedItem(Item(id), reserved)) break
                    val unit = shop.computeSellValue(id, 1)
                    if (unit <= 0) break
                    val deficit = target - coins()
                    val batch = when {
                        held >= 10 && shop.computeSellValue(id, 10).toLong() in 1L..deficit -> 10
                        held >= 5 && shop.computeSellValue(id, 5).toLong() in 1L..deficit -> 5
                        else -> 1
                    }
                    val before = coins()
                    actions++
                    val success = when (batch) {
                        10 -> handler.shop.sell10(id)
                        5 -> handler.shop.sell5(id)
                        else -> handler.shop.sell1(id)
                    }.await()
                    if (!success || coins() <= before || bot.inventory.computeAmountForId(id) >= held) break
                    progressed = true
                }
                if (coins() >= target) return true
                if (bot.inventory.computeAmountForId(id) > 0 || !progressed) failed += shop
                if (!progressed) break
            }
            if (coins() >= target) return true
        }
        return coins() >= target
    }

    private fun shopsForSale(id: Int): List<Shop> = bot.world.shops.values().filter {
        it.currency == Currency.COINS && it.botAccess.apply(bot) && it.computeCanSell(id) &&
            it.computeSellValue(id, 1) > 0
    }

    private fun coins(): Long = bot.inventory.computeAmountForId(995).toLong() + bot.bank.computeAmountForId(995)

    private fun safe(): Boolean = bot.state == EntityState.ACTIVE && !bot.inWilderness() && ActionSafety.NON_COMBAT.allows(bot)
}
