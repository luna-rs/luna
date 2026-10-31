package api.bot.action.trading

import api.attr.Attr
import api.bot.action.CombatLoadout
import api.predef.ext.allSuitableFood
import api.predef.ext.canUseWantedItem
import api.predef.ext.totalItemAmount
import api.predef.SKILL_MAGIC
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
        for ((id, plan) in livePlans(bot, now)) {
            val owned = bot.totalItemAmount(id)
            if (owned >= plan.target || !bot.canUseWantedItem(id)) continue
            val priority = if (owned < plan.minimum) WantedItemPriority.URGENT else WantedItemPriority.STANDARD
            result[id] = definition(id, plan.minimum, plan.target, priority)
        }
        val progression = BotEquipmentPreferences.progressionCandidates(bot)
        val gear = progression + if (discretionaryShopper(bot.username.orEmpty()) || bot.preferences.isMerchant)
            BotEquipmentPreferences.fashionCandidates(bot) else emptyList()
        for (id in gear) {
            if (bot.totalItemAmount(id) > 0 || !bot.canUseWantedItem(id)) continue
            val cosmetic = BotEquipmentPreferences.isCosmetic(id)
            result.putIfAbsent(id, definition(id, -1, 1,
                if (id !in progression && cosmetic) WantedItemPriority.LOW else WantedItemPriority.HIGH))
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
        val retained = livePlans(bot, System.currentTimeMillis()).mapValuesTo(HashMap()) { it.value.target }
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
    fun supplyTarget(bot: Bot, id: Int): Int = livePlans(bot, System.currentTimeMillis())[id]?.target ?: 0

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
