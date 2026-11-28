package engine.bot.coordinator

import api.bot.action.trading.BotMarketplace
import api.bot.action.trading.BotMerchantCapital
import api.bot.action.trading.MerchantStoreBargains
import api.bot.script.BotScript
import api.bot.script.DynamicBotScript
import api.bot.script.ProductionMode
import api.bot.script.VoidBotScript
import api.bot.script.ProfitGatheringGoal
import api.bot.action.trading.ProfitGatheringDemand
import api.bot.script.BotScript.ActionSafety
import api.bot.zone.ProductionZones
import api.bot.zone.SubZone
import api.predef.rand
import api.predef.SKILL_HERBLORE
import api.predef.world
import com.google.common.collect.ImmutableMultiset
import engine.bot.coordinator.skill.ProductionCoordinator
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import engine.bot.coordinator.skill.BotProductionPlanner
import engine.bot.coordinator.skill.NativeProductionOpportunities
import engine.controllers.Controllers.inWilderness
import game.bot.scripts.HarvestBotScript
import game.bot.scripts.BucketSandProfitBotScript
import game.bot.scripts.CollectGroundItemBotScript
import game.bot.scripts.FillWaterBotScript
import game.bot.scripts.FillWaterBotScript.Source
import game.bot.scripts.JoinDragonShieldBotScript
import game.bot.scripts.MakeCrystalKeyBotScript
import game.bot.scripts.OpenContainerBotScript
import game.bot.scripts.OpenCrystalChestBotScript
import game.bot.scripts.SearchNestBotScript
import game.bot.scripts.TradeMolePartsBotScript
import game.bot.scripts.VialWaterProfitBotScript
import game.bot.scripts.WildernessScavengeBotScript
import game.bot.scripts.skills.DecantPotionBotScript
import game.bot.scripts.trading.BuyStoreBargainBotScript
import game.bot.scripts.trading.SellToStoreBotScript
import game.player.item.consume.potion.Potion
import game.player.item.consume.potion.PotionFamily
import game.skill.smithing.Smithing.DRAGON_SQUARE_SHIELD
import game.skill.woodcutting.searchNest.Nest
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivityWeights
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration

/**
 * Selects general money-making tasks outside combat.
 *
 * Existing paid rune deliveries and population-limited scavenging retain their own admission rules. Store bargains,
 * owned reward containers, key processing, dragon shield assembly and mole exchanges use their native scripts.
 * Early-game accounts can also collect raw flax or Lumbridge eggs, fill containers, or buy and fill empty buckets.
 * Sale visits advertise actual transferable stock through the existing player marketplace. Candidate factories are
 * shuffled before invocation so unchosen rune reservations and store-bargain polls have no side effects.
 *
 * @author lare96
 */
object GeneralProfitCoordinator : BotCoordinator {
    private val starterMeleePotionFamilies by lazy {
        listOf(Potion.ATTACK_POTION, Potion.STRENGTH_POTION, Potion.DEFENCE_POTION)
            .map { PotionFamily.forPotion(it) }.toSet()
    }
    private const val FLAX = 1779
    private const val EGG = 1944
    private const val MAX_RAW_FLAX_STOCK = 112
    private const val MAX_EGG_STOCK = 32

    private val chestPositions by lazy {
        world.objects.filter { it.id == OpenCrystalChestBotScript.CHEST }.map { it.position }
    }

    override fun accept(bot: Bot) {
        getScript(bot)?.let { bot.scriptStack.push(it) }
    }

    /**
     * Previews the safe starter fallback without reserving a runner, scavenger or marketplace session.
     */
    fun starterVials(bot: Bot): BotScript? =
        if (VialWaterProfitBotScript.eligible(bot)) VialWaterProfitBotScript(bot) else null

    /**
     * Queues existing jobs in their normal order, with production available after every failed start.
     */
    internal fun getScript(bot: Bot): BotScript? = getOuting(bot)

    /**
     * Gives the early-account vial visit its usual priority while retaining the rest of the outing.
     */
    fun starterOuting(bot: Bot): BotScript? = starterVials(bot)?.let { getOuting(bot, it) }

    private fun getOuting(bot: Bot, starter: BotScript? = null): BotScript? {
        if (!canWork(bot)) return null
        val duration = rand(30, 90).minutes
        return outing(bot, regularJobs(bot, duration, starter)) {
            ProductionCoordinator.select(bot, ProductionMode.PROFIT, duration)
        }
    }

    private fun canWork(bot: Bot): Boolean = ActionSafety.NON_COMBAT.allows(bot) && !bot.inWilderness()

    private fun regularJobs(bot: Bot, duration: Duration, starter: BotScript?): List<() -> BotScript?> {
        val first = mutableListOf<() -> BotScript?>()
        starter?.let { first += { it } }
        val dragonShieldReady = JoinDragonShieldBotScript.eligible(bot)
        val prioritizeShield = dragonShieldReady && owned(bot, DRAGON_SQUARE_SHIELD) == 0L
        if (prioritizeShield) first += { JoinDragonShieldBotScript(bot, 20.minutes).takeIf { JoinDragonShieldBotScript.eligible(bot) } }
        val needsCoins = BotActivityWeights.needsStarterCoins(bot)
        val sell = { MerchantingCoordinator.getSellingScript(bot) }
        if (needsCoins) first += sell

        val candidates = mutableListOf<() -> BotScript?>(
            { MerchantStoreBargains.poll(bot)?.let { BuyStoreBargainBotScript(bot, it) } },
            {
                if (!bot.preferences.isMerchant && (bot.personality.isDumb ||
                        MerchantingCoordinator.sellingPurpose(bot) == null &&
                        BotMarketplace.entries.none { it.hasAvailableTradingSpot(bot) }))
                    SellToStoreBotScript(bot).takeIf { it.saleCandidates().isNotEmpty() }
                else null
            }
        )
        if (!needsCoins) candidates += sell
        if (bot.isEarlyGame()) {
            candidates += listOf(
                { RuneRunningCoordinator.profitScript(bot) },
                { decantStarterPotions(bot, duration) },
                { if (starter == null) starterVials(bot) else null },
                { if (BucketSandProfitBotScript.eligible(bot)) BucketSandProfitBotScript(bot) else null },
                { fillOwnedContainers(bot) },
                { rawFlax(bot) },
                { collectEggs(bot) },
                {
                    if (ScavengingCoordinator.canDispatch(bot) && ScavengingCoordinator.permits(bot))
                        WildernessScavengeBotScript(bot, introductory = true, duration = 30.minutes)
                    else null
                }
            )
        }
        if (owned(bot, OpenCrystalChestBotScript.KEY) > 0) {
            candidates += {
                chestPositions.minByOrNull { it.computeLongestDistance(bot.position) }?.let {
                    OpenCrystalChestBotScript(bot, it, duration)
                }
            }
        }
        candidates += { MakeCrystalKeyBotScript(bot, duration).takeIf { it.isEligible() } }
        if (dragonShieldReady && !prioritizeShield) candidates += { JoinDragonShieldBotScript(bot, 20.minutes) }
        candidates += { TradeMolePartsBotScript(bot, duration).takeIf { it.isEligible() } }
        candidates += { HerbloreSecondaryActivities.profit(bot, duration) }
        for (container in OpenContainerBotScript.Container.entries) {
            candidates += { OpenContainerBotScript(bot, container, duration).takeIf { it.isEligible() } }
        }
        for (nest in Nest.VALUES) {
            if (owned(bot, nest.id) > 0) {
                candidates += { SearchNestBotScript(bot, nest, duration) }
            }
        }
        return first + candidates.shuffled()
    }

    /**
     * Invokes each regular factory at most once. Startup is known to have worked if the child continues its
     * work loop or changes ownership or experience, including work completed in one pass.
     */
    internal fun outing(bot: Bot, regular: List<() -> BotScript?>, fallback: () -> BotScript?): BotScript? {
        val choices = regular.iterator()
        fun nextRegular(): BotScript? {
            while (choices.hasNext()) {
                val candidate = choices.next()()
                if (candidate != null && candidate !is VoidBotScript) return candidate
            }
            return null
        }
        val first = nextRegular() ?: return fallback()
        return object : DynamicBotScript(bot) {
            private var next = first
            private var attempted = false
            private var experience = 0.0
            private var stock = ImmutableMultiset.of<Int>()

            override val actionSafety = ActionSafety.NON_COMBAT

            override suspend fun run(): Boolean {
                if (!canWork(bot)) return true
                if (attempted) {
                    if (next.workContinued || experience != accountExperience() || stock != bot.itemTracker)
                        return true
                    val regular = nextRegular()
                    if (regular == null) {
                        fallback()?.let { bot.scriptStack.softPushHead(it) }
                        return true
                    }
                    next = regular
                }
                attempted = true
                experience = accountExperience()
                stock = ImmutableMultiset.copyOf(bot.itemTracker)
                bot.scriptStack.pushHead(next)
                return false
            }

            private fun accountExperience(): Double = bot.skills.sumOf { it.experience }
        }
    }

    private fun decantStarterPotions(bot: Bot, duration: Duration): BotScript? {
        if (bot.preferences.isMerchant) return NativeProductionOpportunities.starterDecanting(bot, duration)
        val capital = BotMerchantCapital.snapshot(bot)
        if (capital.cashRecovery || capital.electiveCoins <= 0L) return null
        val plan = starterMeleePotionFamilies.shuffled().firstNotNullOfOrNull { family ->
            BotProductionPlanner.select(bot, SKILL_HERBLORE, bot.skill(SKILL_HERBLORE).staticLevel,
                training = false, duration = minOf(duration, 10.minutes), keyPrefix = DecantPotionBotScript.recipe(family).key,
                merchantOutput = family.fourDose, maximumCoinsLimit = capital.electiveCoins,
                singlePurchaseBatch = true)
        }
        return plan ?: NativeProductionOpportunities.starterDecanting(bot, duration, starterMeleePotionFamilies)
    }

    private fun rawFlax(bot: Bot): BotScript? {
        val current = owned(bot, FLAX)
        if (current >= MAX_RAW_FLAX_STOCK) return null
        val quote = ProfitGatheringDemand.quote(bot, FLAX, ticks = 4.0) ?: return null
        val target = minOf(MAX_RAW_FLAX_STOCK.toLong(), current + quote.quantity).toInt()
        val duration = rand(20, 40).minutes
        return HarvestBotScript(bot, HarvestBotScript.Companion.Harvestable.FLAX, duration,
            ProductionZones.FLAX.toMutableList(), targetAmount = target, rawProfit = true).also {
            it.profitGathering = ProfitGatheringGoal.create(bot, quote, duration)
        }
    }

    private fun collectEggs(bot: Bot): BotScript? {
        val current = owned(bot, EGG)
        if (current >= MAX_EGG_STOCK) return null
        val quote = ProfitGatheringDemand.quote(bot, EGG, ticks = 6.0) ?: return null
        val target = minOf(MAX_EGG_STOCK.toLong(), current + minOf(16, quote.quantity)).toInt()
        val duration = rand(15, 30).minutes
        return CollectGroundItemBotScript(bot, EGG, target, duration,
            mutableListOf(SubZone.LUMBRIDGE_CHICKEN_COOP), sellOnCompletion = true).also {
            it.profitGathering = ProfitGatheringGoal.create(bot, quote, duration)
        }
    }

    private fun fillOwnedContainers(bot: Bot): BotScript? {
        val opportunities = buildList {
            for (source in Source.entries) {
                for (empty in source.emptyIds) {
                    if (empty == 229 || source == Source.SAND && empty == 1925) continue
                    val filled = source.resource.getFilled(empty) ?: continue
                    val available = ProfitGatheringGoal.physical(bot, empty)
                    val current = owned(bot, filled)
                    if (available < 1 || current >= Int.MAX_VALUE) continue
                    val quote = ProfitGatheringDemand.quote(bot, filled, ticks = 4.0,
                        unitCost = ProfitGatheringDemand.inputPrice(bot, empty)) ?: continue
                    val target = minOf(Int.MAX_VALUE.toLong(), current + minOf(available, quote.quantity.toLong())).toInt()
                    val duration = rand(20, 40).minutes
                    add(Triple(FillWaterBotScript(bot, empty, target, duration, source), quote, duration))
                }
            }
        }
        val selected = opportunities.randomOrNull() ?: return null
        return selected.first.also {
            it.profitGathering = ProfitGatheringGoal.create(bot, selected.second, selected.third)
        }
    }

    private fun owned(bot: Bot, id: Int): Long = ProfitGatheringGoal.physical(bot, id)
}
