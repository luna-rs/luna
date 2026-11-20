package engine.bot.coordinator

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotCombatActionHandler
import api.bot.action.trading.NativeOutputDemand
import api.bot.action.trading.BotMarketPreference
import api.bot.action.trading.BotMerchantCapital
import api.bot.action.trading.NativeShopOpportunities
import api.bot.isRealBot
import api.bot.script.BotScript
import api.bot.script.BotScript.ActionSafety
import engine.bot.coordinator.skill.FishingScriptFactory
import engine.bot.coordinator.skill.GatheringOpportunity
import engine.bot.coordinator.skill.MiningScriptFactory
import engine.bot.coordinator.skill.NativeProductionOpportunities
import engine.bot.coordinator.skill.SkillingCoordinator.Companion.profitMode
import engine.bot.coordinator.skill.SkillingScriptFactory
import engine.bot.coordinator.skill.WoodcuttingScriptFactory
import engine.controllers.Controllers.inWilderness
import game.bot.scripts.trading.NativeShopProfitScript
import io.luna.game.model.EntityState
import io.luna.game.model.World
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.util.RandomUtils
import kotlin.math.ln1p
import kotlin.math.pow
import kotlin.time.Duration.Companion.minutes

/**
 * Chooses a funded production, gathering or shop route when the ordinary brain selects merchanting.
 * Route previews do not commit stock, coins or demand. Existing market and trade visits are the fallback.
 *
 * @author lare96
 */
internal object MerchantWork {
    private const val DECISIONS_PER_TICK = 2
    private var Bot.nextProfitCheck by Attr.long()
    private var lastWorld: World? = null
    private var lastTick = Long.MIN_VALUE
    private var decisions = 0

    fun script(bot: Bot, capital: BotMerchantCapital.Balance? = null,
               now: Long = System.currentTimeMillis()): BotScript? {
        if (capital?.busy == true || capital?.stockHeavy == true || !bot.isRealBot() ||
            bot.isTemporary || bot.isConnectedPlayer || !BotMarketPreference.profitSeeking(bot) ||
            now < bot.nextProfitCheck || bot.state != EntityState.ACTIVE ||
            bot.inWilderness() || !ActionSafety.NON_COMBAT.allows(bot)) return null
        val world = bot.world
        val tick = world.currentTick
        if (lastWorld !== world || lastTick != tick) {
            lastWorld = world
            lastTick = tick
            decisions = 0
        }
        val jitter = Math.floorMod(bot.usernameHash xor (tick * -7046029254386353131L), 30_001L)
        if (decisions >= DECISIONS_PER_TICK) {
            bot.nextProfitCheck = now + 600L + jitter % 4_201L
            return null
        }
        decisions++
        bot.nextProfitCheck = now + 20_000L + jitter

        val ids = NativeOutputDemand.candidateIds(world)
        if (ids.isEmpty()) return null
        val canSkill = bot.preferences.canChooseActivity(BotActivity.PROFIT_SKILLS) &&
            bot.preferences.activities[BotActivity.PROFIT_SKILLS]?.let { it.isFinite() && it > 0.0 } == true
        val production = if (canSkill && capital?.cashRecovery != true) NativeProductionOpportunities.merchantHint(bot) else 0.0
        val shops = if (capital?.cashRecovery == true) null else NativeShopOpportunities.preview(bot, ids, now, capital)
        val gathering = if (canSkill) gather(bot, ids.toSet()) else null
        val exponent = 0.5 + bot.personality.intelligence.coerceIn(0.0, 1.0)
        val work = linkedMapOf<() -> BotScript?, Double>()
        fun add(score: Double, start: () -> BotScript?) {
            if (score.isFinite() && score > 0.0) work[start] = ln1p(score).pow(exponent)
        }
        add(production) {
            NativeProductionOpportunities.merchantScript(bot, minOf(20.minutes, SkillingScriptFactory.getDuration(bot)))
                ?.also { bot.profitMode = true }
        }
        if (shops != null) add(shops.score) {
            NativeShopOpportunities.admit(bot, shops)?.let { NativeShopProfitScript(bot, it) }
        }
        if (gathering != null) add(gathering.quote.score) {
            gathering.start(ArrayList(), 20.minutes).also { bot.profitMode = true }
        }
        if (work.isEmpty()) return null
        work[{ null }] = if (bot.preferences.isMerchant) 0.8 else 1.2
        return RandomUtils.weightedRoll(work)?.invoke()
    }

    /**
     * Previews one supported gathering skill per decision. Higher and preferred skills get more chances.
     */
    private fun gather(bot: Bot, items: Set<Int>): GatheringOpportunity? {
        val skills = linkedMapOf<SkillingScriptFactory, Double>()
        for (factory in listOf(FishingScriptFactory, MiningScriptFactory, WoodcuttingScriptFactory)) {
            val skill = factory.skillId
            if (!BotCombatActionHandler.mayGainExperience(bot, skill)) continue
            skills[factory] = 1.0 + bot.skill(skill).staticLevel / 30.0 +
                if (skill in bot.preferences.skills) 1.0 else 0.0
        }
        val factory = RandomUtils.weightedRoll(skills) ?: return null
        val level = bot.skill(factory.skillId).staticLevel
        return when (factory) {
            FishingScriptFactory -> FishingScriptFactory.profitCandidate(bot, level, marketOnly = true, items = items)
            MiningScriptFactory -> MiningScriptFactory.profitCandidate(bot, level, marketOnly = true, items = items)
            WoodcuttingScriptFactory -> WoodcuttingScriptFactory.profitCandidate(bot, level, marketOnly = true, items = items)
            else -> null
        }
    }
}
