package engine.bot.gear

import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot

/**
 * Finds useful equipment unlocked by permanent skill progression.
 *
 * Equipment scores and requirement thresholds are indexed once. Each check visits only the crossed thresholds and
 * the bot's owned equipment, rather than scanning every equipment definition for every bot level.
 *
 * @author lare96
 */
class BotGearUnlocks internal constructor(gear: Collection<BotGearItem>) {

    companion object {

        private val shared by lazy { BotGearUnlocks(BotGearSelector.ALL_GEAR_IDS.value.values) }

        /**
         * Returns newly unlocked upgrades, keeping at most one item per equipment slot and combat style.
         * [oldLevel] is the last checked level, so a delayed check includes every intervening unlock.
         */
        fun find(bot: Bot, skillId: Int, oldLevel: Int, newLevel: Int): Set<Int> =
            shared.upgrades(bot, skillId, oldLevel, newLevel)

        /**
         * Removes obsolete tiers from a queued basket using the bot's current permanent levels and ownership.
         */
        fun bestUpgrades(bot: Bot, ids: Collection<Int>): Set<Int> = shared.best(bot, ids)
    }

    /**
     * Immutable metadata shared by the requirement index and the ownership lookup.
     *
     * @author lare96
     */
    private class Candidate(val gear: BotGearItem) {
        val requirements = gear.def.value.requirements
        val score = gear.calculateScore()
        val purposes = gear.purposes.filterTo(HashSet()) {
            // The gear registry assigns MELEE to every equipment definition as a fallback. An explicit ranged or
            // magic requirement identifies that style even when the fallback metadata is also present.
            it != BotGearPurpose.MELEE || requirements.none { requirement ->
                requirement.id == Skill.RANGED || requirement.id == Skill.MAGIC
            } || requirements.any { requirement -> requirement.id == Skill.ATTACK || requirement.id == Skill.STRENGTH }
        }

        fun equippable(bot: Bot): Boolean = requirements.all { bot.skill(it.id).staticLevel >= it.level }
    }

    private val byId = gear.associate { it.id to Candidate(it) }
    private val byOwnedId = byId.toMutableMap().apply {
        byId.forEach { (id, candidate) ->
            val noted = ItemDefinition.ALL.get(id).orElse(null)?.notedId?.orElse(-1) ?: -1
            if (noted >= 0) put(noted, candidate)
        }
    }
    private val byRequirement = byId.values.flatMap { candidate ->
        candidate.requirements.map { (it.id to it.level) to candidate }
    }.groupBy({ it.first }, { it.second })

    /**
     * Selects upgrades without changing equipment or wanted-item preferences. Temporary boosts and drains do not
     * change permanent unlocks. An equal or better equippable item anywhere in the bot's ownership suppresses demand.
     */
    internal fun upgrades(bot: Bot, skillId: Int, oldLevel: Int, newLevel: Int): Set<Int> {
        if (newLevel <= oldLevel) return emptySet()
        val unlocked = (maxOf(1, oldLevel + 1)..minOf(99, newLevel)).flatMap {
            byRequirement[skillId to it].orEmpty()
        }.distinctBy { it.gear.id }
        if (unlocked.isEmpty()) return emptySet()

        val purposes = when (skillId) {
            Skill.ATTACK, Skill.STRENGTH -> setOf(BotGearPurpose.MELEE)
            Skill.RANGED -> setOf(BotGearPurpose.RANGED)
            Skill.MAGIC -> setOf(BotGearPurpose.MAGIC)
            Skill.DEFENCE, Skill.HITPOINTS, Skill.PRAYER ->
                setOf(BotGearPurpose.MELEE, BotGearPurpose.RANGED, BotGearPurpose.MAGIC)
            else -> setOf(BotGearPurpose.SKILLING)
        }
        return select(bot, unlocked, purposes)
    }

    /**
     * Rechecks a pending basket after further training or item acquisition.
     */
    internal fun best(bot: Bot, ids: Collection<Int>): Set<Int> = select(bot,
        ids.distinct().mapNotNull { byId[it] },
        setOf(BotGearPurpose.MELEE, BotGearPurpose.RANGED, BotGearPurpose.MAGIC, BotGearPurpose.SKILLING))

    private fun select(bot: Bot, items: Collection<Candidate>, purposes: Set<BotGearPurpose>): Set<Int> {
        val eligible = items.filter { it.equippable(bot) }
        if (eligible.isEmpty()) return emptySet()
        val owned = bot.itemTracker.elementSet().mapNotNull { byOwnedId[it] }.distinct()
            .filter { it.equippable(bot) }
        val ownedIds = owned.mapTo(HashSet()) { it.gear.id }
        val wanted = LinkedHashSet<Int>()
        for (purpose in purposes) {
            val candidates = eligible.filter { purpose in it.purposes }.groupBy { it.gear.index }
            for ((slot, alternatives) in candidates) {
                val best = alternatives.sortedWith(compareByDescending<Candidate> { it.score }.thenBy { it.gear.id }).first()
                if (best.gear.id in ownedIds) continue
                val ownedScore = owned.asSequence().filter { it.gear.index == slot && purpose in it.purposes }
                    .maxOfOrNull { it.score }
                if (ownedScore == null || best.score > ownedScore) wanted += best.gear.id
            }
        }
        return wanted
    }
}
