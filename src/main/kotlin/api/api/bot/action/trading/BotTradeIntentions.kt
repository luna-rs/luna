package api.bot.action.trading

import api.attr.Attr
import api.bot.action.CombatLoadout
import api.bot.script.ProductionPlan
import api.predef.ext.allSuitableFood
import api.predef.ext.canUseWantedItem
import api.predef.ext.totalItemAmount
import api.predef.SKILL_MAGIC
import api.predef.equipDef
import com.google.common.collect.ImmutableSet
import engine.bot.gear.BotEquipmentPreferences
import engine.bot.gear.BotGearSelector
import engine.bot.gear.BotGearPurpose
import io.luna.game.model.def.AmmoDefinition
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.def.WantedItemDefinition.WantedItemPriority
import io.luna.game.model.def.WantedItemDefinition.WantedItemTag
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotEmotion.EmotionType
import io.luna.game.model.item.Equipment

/**
 * Separates ownership preferences from purchases justified by an activity or a usable equipment upgrade.
 * Supply plans expire after a personality-dependent horizon and are rebuilt by actual activity preparation.
 * A large default wanted target alone never creates an ordinary marketplace shopping intention.
 *
 * @author lare96
 */
object BotTradeIntentions {

    /**
     * A concrete activity's minimum startup quantity and desired stock, expressed as total ownership.
     *
     * @author lare96
     */
    private data class SupplyPlan(val minimum: Int, val target: Int, val expires: Long)

    private val plans = Attr.map<Int, SupplyPlan>()

    private var Bot.productionPlan by Attr.nullableObj(ProductionPlan::class)
    private var Bot.productionOwner by Attr.nullableObj(Any::class)

    /**
     * Hard purchase limits shared by a finite production goal and every shop or native trade filling it.
     * Target means total ordinary ownership, matching the existing wanted-item and trade-target APIs.
     *
     * @author lare96
     */
    data class ProductionPurchaseLimit(val target: Int, val maximumUnitPrice: Long,
                                       val maximumCoins: Long, val expires: Long, val identity: Any)

    /**
     * Publishes the selected goal without expanding it into the broad default wanted-item catalogue.
     */
    fun recordProductionPlan(bot: Bot, plan: ProductionPlan, owner: Any = plan) {
        bot.productionPlan = plan
        bot.productionOwner = owner
    }

    /**
     * Releases only the exact owner, so finishing an older paused script cannot clear a newer goal.
     */
    fun clearProductionPlan(bot: Bot, plan: ProductionPlan, owner: Any = plan) {
        if (bot.productionPlan === plan && bot.productionOwner === owner) {
            bot.productionPlan = null
            bot.productionOwner = null
        }
    }

    /**
     * Reads one bounded production limit without rebuilding gear, supply, shop or world indexes.
     */
    fun productionLimit(bot: Bot, id: Int, now: Long = System.currentTimeMillis()): ProductionPurchaseLimit? {
        val plan = activeProductionPlan(bot, now) ?: return null
        val target = productionTarget(plan, id)
        if (target < 1) return null
        return ProductionPurchaseLimit(target, plan.maximumUnitPrices.getValue(id), plan.remainingCoins,
            plan.deadline, bot.productionOwner ?: plan)
    }

    /**
     * Debits an actual confirmed GP payment once, keeping all inputs on the same finite allowance.
     */
    fun recordProductionPurchase(bot: Bot, id: Int, coins: Long, identity: Any? = null) {
        require(coins >= 0L)
        val plan = bot.productionPlan ?: return
        if (identity != null && (bot.productionOwner ?: plan) !== identity) return
        if (id in plan.maximumUnitPrices) plan.recordSpending(coins)
    }

    private fun activeProductionPlan(bot: Bot, now: Long): ProductionPlan? {
        val plan = bot.productionPlan ?: return null
        if (plan.deadline <= now || plan.remainingOperations < 1 && !plan.hasNextStage()) {
            bot.productionPlan = null
            bot.productionOwner = null
            return null
        }
        return plan
    }

    private fun productionTarget(plan: ProductionPlan, id: Int): Int = plan.target(id)

    /**
     * Enumerates only concrete bounded activity and tool requests for production's dependency graph.
     * No equipment catalogue, item definitions, bank, players or shops are scanned to find these ids.
     */
    fun supplyDemandIds(bot: Bot, now: Long = System.currentTimeMillis()): Set<Int> = buildSet {
        addAll(livePlans(bot, now).keys)
        val upgrades = bot.attributes()[toolUpgrades.attr]
        upgrades.entries.removeIf { it.value.expires <= now }
        addAll(upgrades.keys)
        activeProductionPlan(bot, now)?.let { addAll(it.targets().keys) }
    }

    /**
     * A one-copy upgrade and the already validated tools that make its purchase unnecessary.
     *
     * @author lare96
     */
    private data class ToolUpgradePlan(val expires: Long, val satisfying: List<Int>, val fallback: Int?)

    private val toolUpgrades = Attr.map<Int, ToolUpgradePlan>()

    /**
     * Replaces this tool family's upgrade request without removing independent activity supplies.
     * Useful owned tools retain one working copy instead of creating another purchase.
     */
    internal fun recordToolUpgrade(bot: Bot, family: Set<Int>, id: Int?,
                                   satisfying: List<Int> = emptyList(), retained: Int? = null,
                                   activeForMillis: Long = 2 * 60 * 60_000L,
                                   now: Long = System.currentTimeMillis()) {
        val current = bot.attributes()[toolUpgrades.attr]
        current.keys.removeAll(family)
        if (id != null && id in family && ItemDefinition.ALL.get(id).map { it.isTradeable }.orElse(false)) {
            current[id] = ToolUpgradePlan(now + activeForMillis.coerceIn(1L, 10 * 60 * 60_000L),
                (satisfying.filter { it in family } + id).distinct(), retained?.takeIf { it in family })
        }
    }

    /**
     * Records already validated activity inputs. Tools retain one usable copy; consumable reserves grow
     * for patient stockpilers without inheriting unrelated targets from the global wanted-item catalogue.
     */
    fun recordSupplies(bot: Bot, materials: List<Item>, tools: Set<Int> = emptySet(),
                       stockTarget: Int = 112, now: Long = System.currentTimeMillis()) {
        val stockpiler = bot.personality.isDextrous || bot.personality.isIntelligent
        val horizon = if (stockpiler) 2 * 60 * 60_000L else 30 * 60_000L
        val current = livePlans(bot, now)
        for (item in materials + tools.map { Item(it) }) {
            val definition = ItemDefinition.ALL.get(item.id).orElse(null) ?: continue
            val id = definition.unnotedId.orElse(item.id)
            if (id == 995 || item.amount <= 0 || !definition.isTradeable) continue
            val minimum = if (id in tools) 1 else item.amount
            val reserve = if (id in tools) 1 else if (stockpiler) stockTarget.coerceAtMost(2_000)
                else stockTarget.coerceAtMost(112)
            val target = maxOf(minimum, reserve)
            val previous = current[id]
            current[id] = SupplyPlan(maxOf(minimum, previous?.minimum ?: 0),
                maxOf(target, previous?.target ?: 0), now + horizon)
        }
        // Keep runtime plans bounded even if an account samples many unrelated activities.
        if (current.size > 64) current.entries.sortedBy { it.value.expires }
            .take(current.size - 64).forEach { current.remove(it.key) }
    }

    /**
     * Requests one sensible food type after an activity actually runs out, rather than every food in its tier.
     */
    fun requestFood(bot: Bot, minimum: Int = 14) {
        val food = bot.preferences.allSuitableFood().maxByOrNull { it.heal } ?: return
        recordSupplies(bot, listOf(Item(food.id, minimum.coerceAtLeast(1))), stockTarget = 250)
    }

    /**
     * Resolves active shopping needs while keeping rare cosmetic opportunities outside startup demand.
     * Personal fashion goals are one-copy purchases; equipment candidates already respect real requirements.
     */
    fun purchases(bot: Bot, now: Long = System.currentTimeMillis()): List<WantedItemDefinition> {
        val result = LinkedHashMap<Int, WantedItemDefinition>()
        for ((id, plan) in supplyPlans(bot, now)) {
            val owned = bot.totalItemAmount(id)
            if (owned >= plan.target || !bot.canUseWantedItem(id)) continue
            val priority = if (owned < plan.minimum) WantedItemPriority.URGENT else WantedItemPriority.STANDARD
            result[id] = definition(id, plan.minimum, plan.target, priority)
        }
        val completion = BotEquipmentPreferences.completionCandidates(bot)
        val completionSlots = completion.associateBy { equipDef(it).index }
        val progression = BotEquipmentPreferences.progressionCandidates(bot).filter { id ->
            val fallback = completionSlots[equipDef(id).index]
            fallback == null || fallback == id
        }
        val gear = progression + completion + if (discretionaryShopper(bot.username.orEmpty()) || bot.preferences.isMerchant)
            BotEquipmentPreferences.fashionCandidates(bot) else emptyList()
        for (id in gear) {
            if (bot.totalItemAmount(id) > 0 || !bot.canUseWantedItem(id)) continue
            if (BotTradeBuyingPolicy.satisfiedEquipment(bot, id)) continue
            val cosmetic = BotEquipmentPreferences.isCosmetic(id)
            val urgentUpgrade = id in progression &&
                bot.preferences.getWantedItem(id)?.priority == WantedItemPriority.URGENT
            result.putIfAbsent(id, definition(id, -1, 1,
                if (urgentUpgrade) WantedItemPriority.URGENT
                else if (id in completion && id !in progression) WantedItemPriority.STANDARD
                else if (id !in progression && cosmetic) WantedItemPriority.LOW else WantedItemPriority.HIGH))
        }
        addCombatSupplies(bot, progression, result)
        return result.values.sortedByDescending { it.priority().level }
    }

    /**
     * A stable 1.5 percent of ordinary accounts may actively shop outside useful progression for a favourite look.
     * Every account can still value cosmetics encountered in another trade.
     */
    internal fun discretionaryShopper(username: String): Boolean =
        Math.floorMod(username.lowercase(java.util.Locale.ROOT).replace('_', ' ').trim().hashCode(), 1_000) < 15

    /**
     * Makes rune and arrow shopping follow the actual combat style, compatible weapon and unlocked spell tier.
     * The small ready stock is a near-term use case; gathering-skill levels never imply advanced material demand.
     */
    private fun addCombatSupplies(bot: Bot, progression: List<Int>, result: MutableMap<Int, WantedItemDefinition>,
                                 includeSatisfied: Boolean = false) {
        if (bot.preferences.isSkiller) return
        val purpose = BotEquipmentPreferences.planningPurpose(bot)
        if (purpose == BotGearPurpose.MELEE) return
        val ownedWeapons = BotGearSelector.ALL_GEAR.value[Equipment.WEAPON].filter {
            ItemDefinition.ALL.get(it.id).isPresent && bot.totalItemAmount(it.id) > 0 && bot.canUseWantedItem(it.id) &&
                BotEquipmentPreferences.supports(it, setOf(purpose))
        }
        val weapon = BotEquipmentPreferences.select(bot, ownedWeapons, setOf(purpose))?.id ?: progression.firstOrNull {
            BotGearSelector.ALL_GEAR_IDS.value[it]?.index == Equipment.WEAPON
        } ?: return
        fun add(id: Int, perUse: Int) {
            val target = (perUse.toLong() * 100).coerceIn(100, 1_000).toInt()
            if (!includeSatisfied && bot.totalItemAmount(id) >= target) return
            result.putIfAbsent(id, definition(id, minOf(target, perUse * 10), target, WantedItemPriority.HIGH))
        }
        if (purpose == BotGearPurpose.RANGED) {
            val ammo = AmmoDefinition.ALL.values.filter { weapon in it.weapons }.sortedByDescending { it.strength }
                .flatMap { it.ammo }.firstOrNull { bot.canUseWantedItem(it) }
            if (ammo != null) add(ammo, 1)
        } else if (purpose == BotGearPurpose.MAGIC) {
            val spell = CombatSpellDefinition.getSpells().values.filter {
                it.level <= bot.skill(SKILL_MAGIC).staticLevel && it.spellbook == bot.spellbook &&
                    it.spell.name.substringBefore('_') in setOf("WIND", "WATER", "EARTH", "FIRE") &&
                    it.spell.name.substringAfterLast('_') in setOf("STRIKE", "BOLT", "BLAST", "WAVE")
            }.maxByOrNull { it.maxHit } ?: return
            CombatLoadout.runeCosts(spell, weapon).forEach { (id, cost) -> add(id, cost) }
        }
    }

    /**
     * Keeps a working reserve for sensible accounts; foolish or greedy accounts may spend nearly everything.
     * The fraction is stable for a quote preparation and applies to both coins and barter payment stock.
     */
    fun budgetFraction(bot: Bot, purpose: BotTradePurpose): Double {
        val intelligence = bot.personality.intelligence.coerceIn(0.0, 1.0)
        val greedy = bot.emotions.isFeeling(EmotionType.GREEDY)
        return spendingFraction(intelligence, greedy,
            purpose == BotTradePurpose.MERCHANT_BUY || bot.preferences.isMerchant)
    }

    internal fun spendingFraction(intelligence: Double, greedy: Boolean, merchant: Boolean): Double {
        val caution = intelligence.coerceIn(0.0, 1.0)
        val normal = if (merchant) 0.95 - caution * 0.20 else 0.80 - caution * 0.45
        return (normal + if (greedy) 0.20 * (1.0 - caution) else 0.0).coerceIn(0.20, 0.98)
    }

    /**
     * Protects concrete supplies, personal fashion copies and the strongest owned usable item in each slot.
     * Merchant resale can sell other acquired stock even when a broad default wanted entry exists.
     */
    fun retainedStock(bot: Bot): Map<Int, Int> {
        val retained = supplyPlans(bot, System.currentTimeMillis()).mapValuesTo(HashMap()) { it.value.target }
        val combatSupplies = HashMap<Int, WantedItemDefinition>()
        addCombatSupplies(bot, BotEquipmentPreferences.progressionCandidates(bot), combatSupplies, includeSatisfied = true)
        for ((id, supply) in combatSupplies) retained[id] = maxOf(retained[id] ?: 0, supply.target())
        val owned = BotGearSelector.ALL_GEAR.value.values().filter {
            ItemDefinition.ALL.get(it.id).isPresent && bot.totalItemAmount(it.id) > 0 && bot.canUseWantedItem(it.id)
        }
        for (item in owned) {
            if (BotEquipmentPreferences.prefers(bot, item.id)) retained.putIfAbsent(item.id, 1)
        }
        for (purpose in listOf(BotGearPurpose.MELEE, BotGearPurpose.RANGED, BotGearPurpose.MAGIC)) {
            for (items in owned.filter { BotEquipmentPreferences.supports(it, setOf(purpose)) }.groupBy { it.index }.values) {
                val best = items.maxByOrNull { it.calculateScore() } ?: continue
                retained.putIfAbsent(best.id, 1)
            }
        }
        return retained
    }

    fun retained(bot: Bot, id: Int): Int = retainedStock(bot)[id] ?: 0

    /**
     * Rechecks newly requested supplies during a suspended sale without rebuilding every equipment layout.
     */
    fun supplyTarget(bot: Bot, id: Int): Int = supplyPlans(bot, System.currentTimeMillis())[id]?.target ?: 0

    /**
     * Reads one live sale reserve without copying or pruning the entire activity-supply map.
     * Tool upgrades preserve the same owned replacement and fallback as ordinary supply planning; only the small
     * tool-family map is inspected, and unrelated families require no ownership lookups. Selection and trade-stage
     * checks use this read to notice new working requirements even when no item container has changed.
     */
    internal fun saleSupplyTarget(bot: Bot, id: Int, now: Long = System.currentTimeMillis()): Int {
        val attributes = bot.attributes()
        val plan = attributes[plans.attr][id]
        var target = maxOf(if (plan != null && plan.expires > now) plan.target else 0,
            activeProductionPlan(bot, now)?.let { productionTarget(it, id) } ?: 0)
        val upgrades = attributes[toolUpgrades.attr]
        if (upgrades.isEmpty()) return target
        for ((requested, upgrade) in upgrades) {
            if (upgrade.expires <= now ||
                id != requested && id !in upgrade.satisfying && id != upgrade.fallback) continue
            val owned = upgrade.satisfying.firstOrNull { bot.totalItemAmount(it) > 0 }
            if (id == (owned ?: requested) ||
                owned == null && id == upgrade.fallback && bot.totalItemAmount(id) > 0) target = maxOf(target, 1)
        }
        return target
    }

    /**
     * Resolves one explicitly recorded activity input without rebuilding the equipment shopping catalogue.
     * Player-sale admission uses this alongside broader default wishes, preserving temporary concrete needs.
     */
    fun supplyDemand(bot: Bot, id: Int, now: Long = System.currentTimeMillis()): WantedItemDefinition? {
        val plan = bot.attributes()[plans.attr][id]?.takeIf { it.expires > now }
        var minimum = plan?.minimum ?: 0
        var target = plan?.target ?: 0
        activeProductionPlan(bot, now)?.let { production ->
            val productionTarget = productionTarget(production, id)
            if (productionTarget > 0) {
                minimum = maxOf(minimum, production.recipe.materials[id] ?: 1)
                target = maxOf(target, productionTarget)
            }
        }
        val upgrade = bot.attributes()[toolUpgrades.attr][id]
        if (upgrade != null && upgrade.expires > now && upgrade.satisfying.none { bot.totalItemAmount(it) > 0 }) {
            minimum = maxOf(minimum, 1)
            target = maxOf(target, 1)
        }
        val owned = bot.totalItemAmount(id)
        if (target <= owned || !bot.canUseWantedItem(id)) return null
        return definition(id, minimum, target,
            if (owned < minimum) WantedItemPriority.URGENT else WantedItemPriority.STANDARD)
    }

    /**
     * Combines independent supply sources without reducing either source's minimum or stock target.
     */
    private fun supplyPlans(bot: Bot, now: Long): Map<Int, SupplyPlan> {
        val result = HashMap(livePlans(bot, now))
        activeProductionPlan(bot, now)?.let { plan ->
            for ((id, target) in plan.targets()) {
                val existing = result[id]
                result[id] = SupplyPlan(maxOf(plan.recipe.materials[id] ?: 1, existing?.minimum ?: 0),
                    maxOf(target, existing?.target ?: 0), maxOf(plan.deadline, existing?.expires ?: 0L))
            }
        }
        val upgrades = bot.attributes()[toolUpgrades.attr]
        upgrades.entries.removeIf { it.value.expires <= now }
        fun add(id: Int, upgrade: ToolUpgradePlan) {
            val existing = result[id]
            result[id] = if (existing == null) SupplyPlan(1, 1, upgrade.expires) else SupplyPlan(
                maxOf(existing.minimum, 1), maxOf(existing.target, 1),
                maxOf(existing.expires, upgrade.expires))
        }
        for ((id, upgrade) in upgrades.toMap()) {
            val owned = upgrade.satisfying.firstOrNull { bot.totalItemAmount(it) > 0 }
            val desired = owned ?: id
            if (desired != id) {
                upgrades.remove(id)
                upgrades[desired] = upgrade
            }
            add(desired, upgrade)
            if (owned == null) upgrade.fallback?.takeIf { bot.totalItemAmount(it) > 0 }?.let { add(it, upgrade) }
        }
        return result
    }

    private fun livePlans(bot: Bot, now: Long): HashMap<Int, SupplyPlan> {
        val current = bot.attributes()[plans.attr]
        current.entries.removeIf { it.value.expires <= now }
        return current
    }

    private fun definition(id: Int, minimum: Int, target: Int,
                           priority: WantedItemPriority): WantedItemDefinition {
        val tags = if (BotEquipmentPreferences.isCosmetic(id)) ImmutableSet.of(WantedItemTag.COSMETIC_EQUIPMENT)
            else WantedItemDefinition.DEFAULT.get(id).map { it.tags() }.orElse(WantedItemDefinition.DEFAULT_TAGS)
        return WantedItemDefinition(id, minimum, target, -1, -1, priority, tags)
    }
}
