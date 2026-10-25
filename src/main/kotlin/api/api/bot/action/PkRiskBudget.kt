package api.bot.action

import api.combat.death.PlayerDeathItems
import game.skill.runecrafting.essencePouch.EssencePouch
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import java.math.BigInteger
import kotlin.math.roundToInt

/**
 * A read-only GP assessment of the bot's current PK loadout against its owned item wealth.
 * Only items actually lost under the default death rules consume the allowance. Bank stock and retained
 * tradeable items contribute to wealth, but never to death loss. Non-tradeable items have no liquid GP value.
 * Values use one cached economy guide price per unnoted item, including coins at one GP each.
 * Arbitrary precision totals avoid overflow when several maximum-size stacks are owned.
 *
 * @property basisPoints Maximum wealth share in hundredths of a percent, or zero for an ineligible account.
 * @property wealth Inventory, equipment and bank wealth, including essence stored in owned pouches.
 * @property deathLoss Guide value of dropped items, destroyed pouches and their contents.
 * @author lare96
 */
data class PkRiskBudget(val basisPoints: Int, val wealth: BigInteger, val deathLoss: BigInteger) {
    /**
     * Whole-GP allowance, rounded down so the approved percentage is never exceeded.
     */
    val maximumLoss: BigInteger = wealth.multiply(BigInteger.valueOf(basisPoints.toLong())) / BigInteger.valueOf(10_000)

    /**
     * Whether an eligible bot can afford the current loadout's actual loss, including the exact budget boundary.
     */
    val withinBudget: Boolean = basisPoints > 0 && deathLoss <= maximumLoss

    companion object {
        /**
         * Interpolates between 5% and 25%, weighting confidence, low intelligence and PK affinity equally.
         * Neutral traits produce 15%. Saved out-of-range traits are clamped; non-finite values use neutral traits.
         */
        fun basisPoints(confidence: Double, intelligence: Double, affinity: Double): Int {
            fun normalized(value: Double) = if (value.isFinite()) value.coerceIn(0.0, 1.0) else 0.5
            val appetite = (normalized(confidence) + 1.0 - normalized(intelligence) + normalized(affinity)) / 3.0
            return (500 + (2000 * appetite).roundToInt()).coerceIn(500, 2500)
        }

        /**
         * Takes a synchronous snapshot of physical stock; no items are moved, purchased or created.
         * Callers supply whether default item loss applies to the bot's current server/rank configuration.
         * Active Protect Item and the current skull determine retention, and must be reassessed after changes.
         * Optional proposed containers are valued against actual owned wealth, without adding proposed items to wealth.
         * Planning may protect the cheapest guide-valued units within equal native base-value groups, bounding loss
         * across equipment-switch ordering. Live assessments retain the actual native container order.
         * This does not authorize ownership, withdrawals or controller-specific deaths.
         */
        fun assess(bot: Bot, losesItems: Boolean, inventory: List<Item> = bot.inventory.filterNotNull(),
                   equipment: List<Item> = bot.equipment.filterNotNull(), conservativeTies: Boolean = false): PkRiskBudget =
            assessor(bot, losesItems)(inventory, equipment, conservativeTies)

        /**
         * Quotes owned wealth, prices and protection once for a synchronous sequence of pack candidates.
         * The planner must discard this function before suspending; departure uses a fresh live assessment.
         * An optional retention override prices forced skull/prayer-drain travel before those native effects occur.
         */
        internal fun assessor(bot: Bot, losesItems: Boolean,
                              shareOverride: Int? = null, keepCountOverride: Int? = null): (List<Item>, List<Item>, Boolean) -> PkRiskBudget {
            val ownedInventory = bot.inventory.filterNotNull()
            val ownedEquipment = bot.equipment.filterNotNull()
            val bank = bot.bank.filterNotNull()
            val prices = HashMap<Int, Long>()
            fun value(items: Iterable<Item>): BigInteger = items.fold(BigInteger.ZERO) { total, item ->
                if (!item.itemDef.isTradeable || item.amount <= 0) total else {
                    val id = item.unnotedItemDef.id()
                    val price = prices.getOrPut(id) { if (id == 995) 1L else bot.world.economy.getPrice(id).coerceAtLeast(0) }
                    total + BigInteger.valueOf(price) * BigInteger.valueOf(item.amount.toLong())
                }
            }
            fun pouchContents(items: List<Item>): List<Item> = items.mapNotNull { item ->
                val pouch = EssencePouch.ID_TO_POUCH[item.id] ?: return@mapNotNull null
                val id = bot.attributes()[pouch.essenceId]
                val amount = bot.attributes()[pouch.essence]
                if (amount > 0 && id in setOf(EssencePouch.RUNE_ESSENCE, EssencePouch.PURE_ESSENCE)) Item(id, amount) else null
            }

            // Pouch attributes belong to the account, not each item. Count each pouch type only once.
            val ownedPouches = (ownedInventory + bank).filter { it.id in EssencePouch.ID_TO_POUCH }
                .distinctBy { EssencePouch.ID_TO_POUCH[it.id] }
            val wealth = value(ownedInventory + ownedEquipment + bank) + value(pouchContents(ownedPouches))
            val share = shareOverride?.coerceIn(0, 10_000) ?: if (bot.preferences.isSkiller || bot.preferences.pk == null) 0 else basisPoints(
                bot.personality.confidence, bot.personality.intelligence,
                bot.preferences.activities[BotActivity.PKING] ?: 0.0)
            val keepCount = keepCountOverride?.coerceIn(0, 4) ?: PlayerDeathItems.keepCount(bot)
            return { inventory, equipment, conservativeTies ->
                val carriedPouches = inventory.filter { it.id in EssencePouch.ID_TO_POUCH }
                    .distinctBy { EssencePouch.ID_TO_POUCH[it.id] }
                val lost = if (losesItems) {
                    val carried = inventory.filter { it.id !in EssencePouch.ID_TO_POUCH } + equipment
                    val tradeable = if (conservativeTies) carried.sortedWith(
                        Item.VALUE_COMPARATOR.thenBy { value(listOf(it.withAmount(1))) }) else carried
                    value(PlayerDeathItems.select(tradeable, keepCount).lost) +
                        value(inventory.filter { it.id in EssencePouch.ID_TO_POUCH }) + value(pouchContents(carriedPouches))
                } else BigInteger.ZERO
                PkRiskBudget(share, wealth, lost)
            }
        }
    }
}
