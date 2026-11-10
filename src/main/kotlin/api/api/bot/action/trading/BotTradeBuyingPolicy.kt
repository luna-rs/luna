package api.bot.action.trading

import api.attr.Attr
import api.predef.ext.canUseWantedItem
import api.predef.ext.totalItemAmount
import engine.bot.gear.BotEquipmentPreferences
import engine.bot.gear.BotGearItem
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotGearSelector
import engine.bot.gear.BotItemTracker.Companion.itemOwnershipRevision
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.def.WantedItemDefinition
import io.luna.game.model.def.WantedItemDefinition.WantedItemTag
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.bot.Bot
import java.util.Locale

/**
 * Quotes opportunistic player purchases separately from proactive shopping and personal sale reserves.
 *
 * Shared default supplies receive category buying ceilings without changing their saved ownership targets.
 * Custom supply requirements retain their own targets, including legacy saved overrides. Consequently there is
 * no ambiguous save migration, and a large reactive buying allowance never protects surplus from selling.
 * Supply checks use indexed wanted-item and ownership lookups. Equipment goals are rebuilt at most once every
 * five seconds after an equipment offer; owned-slot indexes are invalidated by native container revisions.
 *
 * @author lare96
 */
object BotTradeBuyingPolicy {

    /**
     * One personal slot need and the interchangeable equipment that may satisfy it.
     *
     * @author lare96
     */
    private data class Goal(val id: Int, val slot: Int, val family: String?, val alternatives: Set<Int>,
                            val cosmetic: Boolean)

    /**
     * Lazy equipment-only state. Supply advertisements do not allocate or refresh this cache.
     *
     * @author lare96
     */
    private class EquipmentCache {
        var expires = Long.MIN_VALUE
        var style: BotGearPurpose? = null
        var goals = emptyList<Goal>()
        var revision = Long.MIN_VALUE
        var ownedStyle: BotGearPurpose? = null
        var ownedExpires = Long.MIN_VALUE
        var owned = emptyMap<Int, List<BotGearItem>>()
    }

    private val equipment = Attr.obj { EquipmentCache() }
    private val supplyCeilings = HashMap<Int, Int>()
    private val expandedDefaults = HashMap<Int, WantedItemDefinition>()
    private val metalNames = listOf("bronze", "iron", "steel", "black", "white", "mithril", "adamant", "rune", "dragon")
    private val equipmentFamilies = HashMap<Int, String?>()
    private val familyMembers by lazy(LazyThreadSafetyMode.NONE) {
        BotGearSelector.ALL_GEAR_IDS.value.values.asSequence().filter { it.index != Equipment.AMMUNITION }
            .mapNotNull { gear -> family(gear.id)?.let { (gear.index to it) to gear.id } }
            .groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
    }

    /**
     * Resolves a currently eligible offered commodity and its total buying ceiling, not an additional quantity.
     * The trade owner separately caps the deficit by its actual carried cash and receiving capacity.
     */
    fun demand(bot: Bot, id: Int): WantedItemDefinition? {
        val definition = ItemDefinition.ALL.get(id).orElse(null) ?: return null
        val base = definition.unnotedId.orElse(id)
        if (base == 995 || !definition.isTradeable) return null
        val active = BotTradeIntentions.supplyDemand(bot, base)
        val wanted = bot.preferences.getWantedItem(base)?.takeIf { it.target > 0 && eligible(bot, it) }
            ?: active ?: return null
        if (!bot.canUseWantedItem(base)) return null
        val gear = BotGearSelector.ALL_GEAR_IDS.value[base]
        if (gear != null && gear.index != Equipment.AMMUNITION && WantedItemTag.TOOL !in wanted.tags) {
            if (!gear.def.value.meetsAllRequirements(bot)) return null
            val goal = goals(bot).firstOrNull { base in it.alternatives } ?: return null
            if (fulfilled(bot, goal, gear)) return null
            return copy(wanted, 1)
        }
        val target = maxOf(ceiling(wanted), active?.target ?: 0)
        if (bot.totalItemAmount(base) >= target) return null
        if (target == wanted.target) return wanted
        val shared = WantedItemDefinition.DEFAULT.get(base).orElse(null)
        return if (shared != null && sameSettings(wanted, shared) && target == ceiling(shared)) {
            expandedDefaults.getOrPut(base) { copy(shared, target) }
        } else copy(wanted, target)
    }

    /**
     * Returns only currently acceptable replacements for the same personal equipment need.
     * A purchased equivalent immediately satisfies the entire group, even within the same game tick.
     */
    fun alternatives(bot: Bot, id: Int): Set<Int> {
        val base = ItemDefinition.ALL.get(id).map { it.unnotedId.orElse(id) }.orElse(id)
        if (!groupedEquipment(bot, base)) return emptySet()
        val goal = goals(bot).firstOrNull { base in it.alternatives } ?: return emptySet()
        return goal.alternatives.filterTo(linkedSetOf()) { alternative ->
            val wanted = bot.preferences.getWantedItem(alternative)
            val gear = BotGearSelector.ALL_GEAR_IDS.value[alternative]
            wanted != null && wanted.target > 0 && gear != null && eligible(bot, wanted) &&
                gear.def.value.meetsAllRequirements(bot) && !fulfilled(bot, goal, gear)
        }
    }

    /**
     * Describes a real set of accepted equipment alternatives; other purchases retain their exact item wording.
     */
    fun buyingAdvertisement(bot: Bot, id: Int): String? {
        val base = ItemDefinition.ALL.get(id).map { it.unnotedId.orElse(id) }.orElse(id)
        if (!groupedEquipment(bot, base)) return null
        val goal = goals(bot).firstOrNull { base in it.alternatives } ?: return null
        val family = goal.family ?: return null
        if (alternatives(bot, base).size < 2) return null
        val category = if (goal.slot == Equipment.WEAPON) "weapons" else "armour upgrades"
        return "Buying $family $category!"
    }

    /**
     * Shares one equipment quantity across alternative ids in an offered basket. Supplies and tools have no group.
     */
    fun groupKey(bot: Bot, id: Int): Int? {
        val base = ItemDefinition.ALL.get(id).map { it.unnotedId.orElse(id) }.orElse(id)
        if (!groupedEquipment(bot, base)) return null
        return goals(bot).firstOrNull { base in it.alternatives }?.slot
    }

    /**
     * Keeps ordinary proactive ads satisfied by the same owned tier that satisfies reactive alternatives.
     * This uses only the ownership index, so resolving equipment goals cannot recursively rebuild itself.
     */
    fun satisfiedEquipment(bot: Bot, id: Int): Boolean {
        val base = ItemDefinition.ALL.get(id).map { it.unnotedId.orElse(id) }.orElse(id)
        val candidate = BotGearSelector.ALL_GEAR_IDS.value[base] ?: return false
        if (candidate.index == Equipment.AMMUNITION || family(base) == null ||
            BotEquipmentPreferences.isCosmetic(base) || !candidate.def.value.meetsAllRequirements(bot)) return false
        val style = BotEquipmentPreferences.planningPurpose(bot)
        if (!BotEquipmentPreferences.supports(candidate, setOf(style))) return false
        return ownedSlots(bot)[candidate.index].orEmpty().any { owned ->
            owned.def.value.highestRequirement >= candidate.def.value.highestRequirement ||
                owned.calculateScore() >= candidate.calculateScore()
        }
    }

    /**
     * Clears equipment decisions when a caller deliberately changes the bot's shopping policy.
     * Container changes independently invalidate the owned-slot index through [itemOwnershipRevision].
     */
    fun invalidate(bot: Bot) {
        bot.attributes()[equipment.attr] = EquipmentCache()
    }

    /**
     * Raises shared bulk-supply buying allowances while preserving every genuinely custom target.
     */
    internal fun ceiling(wanted: WantedItemDefinition): Int {
        val shared = WantedItemDefinition.DEFAULT.get(wanted.id).orElse(null) ?: return wanted.target
        if (!sameSettings(wanted, shared)) return wanted.target
        val consumable = WantedItemTag.SKILLING_CONSUMABLE in wanted.tags ||
                WantedItemTag.COMBAT_CONSUMABLE in wanted.tags
        if (!consumable) return wanted.target
        val category = supplyCeilings.getOrPut(wanted.id) { supplyCeiling(wanted.id) }
        return maxOf(wanted.target, category)
    }

    private fun eligible(bot: Bot, wanted: WantedItemDefinition): Boolean {
        val level = if (wanted.skill == -1) bot.combatLevel else bot.skill(wanted.skill).staticLevel
        if (wanted.maxLevel != -1 && level > wanted.maxLevel) return false
        val skilling = WantedItemTag.SKILLING_CONSUMABLE in wanted.tags || WantedItemTag.TOOL in wanted.tags
        val combat = WantedItemTag.COMBAT_CONSUMABLE in wanted.tags || WantedItemTag.WEAPON in wanted.tags ||
                WantedItemTag.COMBAT_EQUIPMENT in wanted.tags
        if (bot.preferences.isSkiller && combat && !skilling) return false
        return true
    }

    private fun goals(bot: Bot): List<Goal> {
        val cache = bot.attributes()[equipment.attr]
        val style = BotEquipmentPreferences.planningPurpose(bot)
        val now = System.nanoTime()
        if (cache.expires > now && cache.style == style) return cache.goals
        cache.style = style
        cache.expires = now + 5_000_000_000L
        cache.goals = BotTradeIntentions.purchases(bot).mapNotNull { wanted ->
            val gear = BotGearSelector.ALL_GEAR_IDS.value[wanted.id] ?: return@mapNotNull null
            if (gear.index == Equipment.AMMUNITION || !gear.def.value.meetsAllRequirements(bot)) return@mapNotNull null
            val cosmetic = BotEquipmentPreferences.isCosmetic(gear.id)
            val family = if (cosmetic) null else family(gear.id)
            val choices = if (family == null) setOf(gear.id) else
                familyMembers[gear.index to family].orEmpty().filterTo(linkedSetOf()) { alternative ->
                    val candidate = BotGearSelector.ALL_GEAR_IDS.value[alternative] ?: return@filterTo false
                    candidate.def.value.highestRequirement == gear.def.value.highestRequirement &&
                        BotEquipmentPreferences.supports(candidate, setOf(style)) &&
                        ItemDefinition.ALL.get(alternative).map { it.isTradeable }.orElse(false) &&
                        candidate.def.value.meetsAllRequirements(bot)
                }
            Goal(gear.id, gear.index, family, choices + gear.id, cosmetic)
        }
        return cache.goals
    }

    private fun fulfilled(bot: Bot, goal: Goal, candidate: BotGearItem): Boolean {
        if (bot.totalItemAmount(candidate.id) > 0) return true
        if (goal.cosmetic) return false
        return ownedSlots(bot)[goal.slot].orEmpty().any { owned ->
            owned.def.value.highestRequirement >= candidate.def.value.highestRequirement ||
                owned.calculateScore() >= candidate.calculateScore()
        }
    }

    private fun ownedSlots(bot: Bot): Map<Int, List<BotGearItem>> {
        val cache = bot.attributes()[equipment.attr]
        val style = BotEquipmentPreferences.planningPurpose(bot)
        val revision = bot.itemOwnershipRevision
        val now = System.nanoTime()
        if (cache.revision == revision && cache.ownedStyle == style && cache.ownedExpires > now) return cache.owned
        cache.revision = revision
        cache.ownedStyle = style
        cache.ownedExpires = now + 5_000_000_000L
        cache.owned = bot.itemTracker.elementSet().asSequence().mapNotNull { id ->
            val base = ItemDefinition.ALL.get(id).map { it.unnotedId.orElse(id) }.orElse(id)
            BotGearSelector.ALL_GEAR_IDS.value[base]
        }.distinctBy { it.id }.filter { gear ->
            gear.index != Equipment.AMMUNITION && BotEquipmentPreferences.supports(gear, setOf(style)) &&
                gear.def.value.meetsAllRequirements(bot)
        }.groupBy { it.index }
        return cache.owned
    }

    private fun family(id: Int): String? {
        if (equipmentFamilies.containsKey(id)) return equipmentFamilies[id]
        val name = ItemDefinition.ALL.get(id).map { it.name.lowercase(Locale.ROOT) }.orElse("")
        return metalNames.firstOrNull { name.startsWith("$it ") }.also { equipmentFamilies[id] = it }
    }

    private fun groupedEquipment(bot: Bot, id: Int): Boolean =
        BotGearSelector.ALL_GEAR_IDS.value[id]?.index?.let { it != Equipment.AMMUNITION } == true &&
            bot.preferences.getWantedItem(id)?.tags?.contains(WantedItemTag.TOOL) != true &&
            WantedItemDefinition.DEFAULT.get(id).map { WantedItemTag.TOOL !in it.tags }.orElse(true)

    private fun supplyCeiling(id: Int): Int {
        val name = ItemDefinition.ALL.get(id).map { it.name.lowercase(Locale.ROOT) }.orElse("")
        return when {
            name.contains("essence") -> 50_000
            name == "feather" || name.contains("arrow shaft") || name.contains("arrowhead") -> 50_000
            name.endsWith(" rune") -> if (name.startsWith("air ") || name.startsWith("water ") ||
                name.startsWith("earth ") || name.startsWith("fire ")) 20_000 else 10_000
            name == "flax" -> 20_000
            name == "bow string" -> 10_000
            name.endsWith(" seed") -> 5_000
            name.contains("ore") || name == "coal" -> when {
                name.startsWith("runite") -> 1_000
                name.startsWith("adamantite") -> 2_000
                name.startsWith("mithril") -> 5_000
                else -> 10_000
            }
            name.endsWith(" bar") -> when {
                name.startsWith("runite") -> 1_000
                name.startsWith("adamantite") -> 2_000
                else -> 5_000
            }
            name.contains("logs") -> when {
                name.startsWith("magic") -> 1_000
                name.startsWith("yew") -> 2_000
                else -> 10_000
            }
            name.startsWith("raw ") || name.contains("bones") -> 5_000
            else -> 5_000
        }
    }

    private fun sameSettings(first: WantedItemDefinition, second: WantedItemDefinition): Boolean =
        first === second || first.id == second.id && first.min == second.min && first.target == second.target &&
                first.skill == second.skill && first.maxLevel == second.maxLevel &&
                first.priority == second.priority && first.tags == second.tags

    private fun copy(wanted: WantedItemDefinition, target: Int): WantedItemDefinition =
        WantedItemDefinition(wanted.id, wanted.min, target, wanted.skill, wanted.maxLevel, wanted.priority, wanted.tags)
}
