package game.bot.scripts.skills

import api.bot.action.trading.BotTradeIntentions
import api.bot.script.BotScript.ActionSafety
import api.predef.SKILL_AGILITY
import api.predef.SKILL_STRENGTH
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.player.item.consume.food.Food
import game.skill.Skills
import game.skill.agility.pyramid.AgilityPyramid
import io.luna.game.action.ActionType
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlin.math.ceil

/**
 * Optional summer-pie supplies for dextrous Agility trainees. Native eating owns each bite's effects and leftovers.
 * Useful boosts are kept until they wear off or no longer meet admission; safe courses only need pies for low energy.
 *
 * @author lare96
 */
internal class AgilitySummerPies(private val bot: Bot, private val course: AgilityBotCourse) {

    companion object {
        /**
         * Finish an opened pie before starting another one.
         */
        private val PORTIONS = listOf(Food.SUMMER_PIE.id2!!, Food.SUMMER_PIE.id)
        private const val CARRIED_PIES = 4
        private const val STOCK_TARGET = 24
    }

    private var requested = false

    /**
     * Whether keeping a small supply will help at the bot's ordinary level, or its current low run energy.
     */
    fun stockUseful(): Boolean = bot.personality.isDextrous &&
        (bot.runEnergy <= 40.0 || boostImprovesSuccess(bot.skill(SKILL_AGILITY).staticLevel))

    /**
     * Keeps a remaining boost unless admission needs refreshing. Energy can justify a bite at any level.
     */
    fun needsBite(): Boolean {
        if (!bot.personality.isDextrous) return false
        if (bot.runEnergy <= 40.0) return true
        val agility = bot.skill(SKILL_AGILITY)
        if (agility.level < course.minimumLevel &&
            minOf(agility.level + 5, agility.staticLevel + 5) >= course.minimumLevel) return true
        if (agility.level > agility.staticLevel) return false
        return boostImprovesSuccess(agility.level)
    }

    /**
     * Uses the same ordinary failure-rate metadata as the native obstacles, without rolling an outcome.
     */
    private fun boostImprovesSuccess(level: Int): Boolean {
        val boosted = minOf(level + 5, bot.skill(SKILL_AGILITY).staticLevel + 5)
        if (boosted <= level) return false
        // The death slide uses the uncapped current level together with Strength and weight.
        if (course == AgilityBotCourse.WEREWOLF) {
            val strengthAndWeight = bot.skill(SKILL_STRENGTH).level - maxOf(bot.weight, 0.0)
            return ceil(level + strengthAndWeight).coerceIn(0.0, 161.0) <
                ceil(boosted + strengthAndWeight).coerceIn(0.0, 161.0)
        }
        val ordinaryLevel = minOf(level, 99)
        val ordinaryBoost = minOf(boosted, 99)
        if (ordinaryBoost <= ordinaryLevel) return false
        if (course.steps.any { step ->
                step.obstacle?.failure?.let {
                    Skills.successRate(it.low, it.high, ordinaryLevel).coerceIn(0.0, 1.0) <
                        Skills.successRate(it.low, it.high, ordinaryBoost).coerceIn(0.0, 1.0)
                } == true
            }) return true
        return when (course) {
            AgilityBotCourse.AGILITY_PYRAMID -> AgilityPyramid.Hazard.entries.any { level < it.safeLevel }
            // Native Yanille ledge and monkey-bar rolls stop failing at these current levels.
            AgilityBotCourse.YANILLE_LEDGE -> level < 66
            AgilityBotCourse.YANILLE_BARS -> level < 75
            else -> false
        }
    }

    /**
     * Creates a bounded activity supply request once, retaining existing wanted-item definitions.
     */
    fun requestSupplies() {
        if (requested || !stockUseful()) return
        if (PORTIONS.sumOf { bot.itemTracker.count(it).toLong() } >= CARRIED_PIES) return
        BotTradeIntentions.recordSupplies(bot, listOf(Item(Food.SUMMER_PIE.id, CARRIED_PIES)), stockTarget = STOCK_TARGET)
        requested = true
    }

    /**
     * Whether an initial bank visit can add useful pies that are not already carried.
     */
    fun canStock(): Boolean = stockUseful() && PORTIONS.none { bot.inventory.contains(it) } &&
        PORTIONS.any { bot.bank.contains(it) }

    /**
     * Takes a small optional supply from an already open bank. A missing pie never rejects the course.
     */
    suspend fun withdraw(): Boolean {
        if (!stockUseful()) return false
        requestSupplies()
        var remaining = CARRIED_PIES - PORTIONS.sumOf { bot.inventory.computeAmountForId(it) }
        for (id in PORTIONS) {
            val amount = minOf(remaining, bot.bank.computeAmountForId(id))
            if (amount > 0 && bot.actionHandler.banking.withdraw(Item(id, amount))) {
                remaining = CARRIED_PIES - PORTIONS.sumOf { bot.inventory.computeAmountForId(it) }
            }
            if (remaining <= 0) break
        }
        return PORTIONS.any { bot.inventory.contains(it) }
    }

    /**
     * Eats one real portion between settled obstacles, including at full health when its support effects are useful.
     */
    suspend fun eatIfUseful(): Boolean {
        if (!needsBite() || bot.isLocked || bot.actions.size(ActionType.STRONG) > 0 || !bot.walking.isEmpty ||
            bot.overlays.hasWindow()) return false
        val safety = if (course.isYanille) ActionSafety.COMBAT else ActionSafety.NON_COMBAT
        if (!safety.allows(bot)) return false
        val id = PORTIONS.firstOrNull { bot.inventory.contains(it) }
        if (id == null) {
            requestSupplies()
            return false
        }
        return bot.actionHandler.inventory.eat(bot.inventory.computeIndexForId(id), id)
    }
}
