package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.script.ProductionPlan
import api.bot.script.VoidBotScript
import api.bot.script.recentEscape
import api.bot.zone.SubZone
import api.predef.*
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.bot.scripts.skills.CraftRuneBotScript
import game.bot.scripts.skills.CraftRuneBotScript.Companion.PURE_ESSENCE
import game.bot.scripts.skills.CraftRuneBotScript.Companion.RUNE_ESSENCE
import game.obj.entrances.Zanaris
import game.player.item.consume.food.Food
import game.skill.runecrafting.craftRune.CraftableRune
import game.skill.runecrafting.enterAltar.Altar
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Chooses a Runecrafting trip using owned materials or executable supply and a viable altar entrance.
 *
 * @author lare96
 */
object RunecraftingScriptFactory : SkillingScriptFactory(SKILL_RUNECRAFTING) {

    private const val MAX_PLANS = 4

    /**
     * One compatible essence and altar combination.
     *
     * @author lare96
     */
    private data class Candidate(val rune: CraftableRune, val essence: Int, val owned: Boolean,
                                 val access: Boolean, val score: Double) {
        val key get() = recipeKey(rune, essence)
    }

    internal fun recipeKey(rune: CraftableRune, essence: Int): String =
        if (rune.level <= 20 && essence == PURE_ESSENCE) "runecraft:pure:${rune.name}" else "runecraft:${rune.name}"

    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        createScript(bot, level, zones, training = true)

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        createScript(bot, level, zones, training = false)

    private fun createScript(bot: Bot, level: Int, zones: MutableList<SubZone>, training: Boolean): BotScript =
        choose(bot, level, training, getDuration(bot, skillId))?.also {
            zones.clear()
            zones.add(it.altar.zone)
        } ?: VoidBotScript(bot)

    internal fun alternative(bot: Bot, plan: ProductionPlan, duration: Duration,
                             altarUnavailable: Boolean): CraftRuneBotScript? =
        choose(bot, bot.runecrafting.staticLevel, plan.training, duration, plan, altarUnavailable)

    private fun choose(bot: Bot, level: Int, training: Boolean, duration: Duration,
                       previous: ProductionPlan? = null, altarUnavailable: Boolean = false): CraftRuneBotScript? {
        val currentLevel = minOf(level, bot.runecrafting.level)
        val supply = HashMap<Int, BotProductionPlanner.RuneSupply?>()
        fun source(id: Int): BotProductionPlanner.RuneSupply? {
            if (!supply.containsKey(id)) supply[id] = BotProductionPlanner.runeSupply(bot, id)
            return supply[id]
        }
        fun owned(id: Int): Boolean =
            bot.itemTracker.contains(id) || itemDef(id).notedId.orElse(-1).let { it >= 0 && bot.itemTracker.contains(it) }
        val canUseAbyss = previous == null && bot.recentEscape?.avoids(SubZone.ABYSS_ENTRY_MAGE) != true &&
            bot.health >= 30 && Food.values().any { it.heal >= 8 && bot.itemTracker.count(it.id) >= 4 }
        val candidates = buildList {
            for (rune in CraftableRune.entries) {
                if (rune.level > currentLevel || bot.recentEscape?.avoids(rune.altar.zone) == true) continue
                val talisman = rune.altar.talisman
                val hasAccess = owned(talisman)
                if (!hasAccess && source(talisman) == null) continue
                val needsAbyss = rune.altar == Altar.DEATH ||
                    rune.altar == Altar.COSMIC && !owned(Zanaris.STAFF)
                if (needsAbyss && !canUseAbyss) continue
                for (essence in listOf(RUNE_ESSENCE, PURE_ESSENCE)) {
                    if (essence == RUNE_ESSENCE && rune.level > 20) continue
                    if (rune.id in previous?.recipe?.products.orEmpty() &&
                        (altarUnavailable || essence in previous?.recipe?.materials.orEmpty())) continue
                    val onHand = owned(essence)
                    val offered = if (onHand) null else source(essence) ?: continue
                    val score = if (training) rune.exp else
                        bot.world.economy.getPrice(rune.id).toDouble() * rune.outputMultiplier(currentLevel) -
                            (offered?.unitPrice ?: bot.world.economy.getPrice(essence))
                    if (score > 0.0) add(Candidate(rune, essence, onHand, hasAccess, score))
                }
            }
        }.sortedWith(compareByDescending<Candidate> { it.owned }.thenByDescending { it.access }
            .thenByDescending { it.score }.thenByDescending { it.rune.level })
        val preferred = selectRankedActivity(bot, candidates.takeWhile {
            it.owned == candidates.first().owned && it.access == candidates.first().access
        }) ?: return null
        for (candidate in (listOf(preferred) + candidates.filter { it != preferred }).take(MAX_PLANS)) {
            val rune = candidate.rune
            val plan = BotProductionPlanner.select(bot, skillId, currentLevel, training, duration, candidate.key,
                maximumCoinsLimit = previous?.remainingCoins ?: Long.MAX_VALUE,
                deadlineLimit = previous?.deadline ?: Long.MAX_VALUE)?.productionPlan
                ?: continue
            val viaAbyss = canUseAbyss && (rune.altar == Altar.DEATH ||
                rune.altar == Altar.COSMIC && !owned(Zanaris.STAFF) ||
                rand(0.25 + bot.personality.confidence.coerceIn(0.0, 1.0) * 0.5))
            return CraftRuneBotScript(bot, rune.altar, duration, viaAbyss, profit = !training).also {
                it.trainingGoal = if (training) skillId else null
                it.planProduction(plan)
            }
        }
        return null
    }
}
