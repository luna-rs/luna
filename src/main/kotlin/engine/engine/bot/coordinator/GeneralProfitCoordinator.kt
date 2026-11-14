package engine.bot.coordinator

import api.bot.action.trading.MerchantStoreBargains
import api.bot.script.BotScript
import api.bot.script.BotScript.ActionSafety
import api.bot.zone.ProductionZones
import api.bot.zone.SubZone
import api.predef.rand
import api.predef.world
import engine.controllers.Controllers.inWilderness
import game.bot.scripts.HarvestBotScript
import game.bot.scripts.CollectGroundItemBotScript
import game.bot.scripts.FillWaterBotScript
import game.bot.scripts.FillWaterBotScript.Source
import game.bot.scripts.MakeCrystalKeyBotScript
import game.bot.scripts.OpenContainerBotScript
import game.bot.scripts.OpenCrystalChestBotScript
import game.bot.scripts.SearchNestBotScript
import game.bot.scripts.TradeMolePartsBotScript
import game.bot.scripts.VialWaterProfitBotScript
import game.bot.scripts.WildernessScavengeBotScript
import game.bot.scripts.trading.BuyStoreBargainBotScript
import game.bot.scripts.trading.SellToStoreBotScript
import game.skill.woodcutting.searchNest.Nest
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator
import kotlin.time.Duration.Companion.minutes

/**
 * Selects money-making tasks that do not train a skill or initiate combat.
 *
 * Existing paid rune deliveries and population-limited scavenging retain their own admission rules. Store bargains,
 * owned reward containers, key processing, mole exchanges, raw flax, Lumbridge eggs, and filled containers use their
 * native scripts.
 * Sale visits advertise actual transferable stock through the existing player marketplace. Candidate factories are
 * shuffled before invocation so unchosen rune reservations and store-bargain polls have no side effects.
 *
 * @author lare96
 */
object GeneralProfitCoordinator : BotCoordinator {
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
     * Chooses the first currently eligible task after shuffling independent profit opportunities.
     */
    internal fun getScript(bot: Bot): BotScript? {
        if (!ActionSafety.NON_COMBAT.allows(bot) || bot.inWilderness()) return null
        val duration = rand(30, 90).minutes
        val candidates = mutableListOf<() -> BotScript?>(
            { RuneRunningCoordinator.profitScript(bot) },
            { MerchantStoreBargains.poll(bot)?.let { BuyStoreBargainBotScript(bot, it) } },
            { starterVials(bot) },
            { fillOwnedContainers(bot) },
            { rawFlax(bot) },
            { collectEggs(bot) },
            { MerchantingCoordinator.getSellingScript(bot) },
            {
                if (!bot.preferences.isMerchant && bot.personality.isDumb)
                    SellToStoreBotScript(bot).takeIf { it.saleCandidates().isNotEmpty() }
                else null
            }
        )
        candidates += {
            if (ScavengingCoordinator.canDispatch(bot) && ScavengingCoordinator.permits(bot))
                WildernessScavengeBotScript(bot, introductory = true, duration = 30.minutes)
            else null
        }
        if (owned(bot, OpenCrystalChestBotScript.KEY) > 0) {
            candidates += {
                chestPositions.minByOrNull { it.computeLongestDistance(bot.position) }?.let {
                    OpenCrystalChestBotScript(bot, it, duration)
                }
            }
        }
        candidates += { MakeCrystalKeyBotScript(bot, duration).takeIf { it.isEligible() } }
        candidates += { TradeMolePartsBotScript(bot, duration).takeIf { it.isEligible() } }
        for (container in OpenContainerBotScript.Container.entries) {
            candidates += { OpenContainerBotScript(bot, container, duration).takeIf { it.isEligible() } }
        }
        for (nest in Nest.VALUES) {
            if (owned(bot, nest.id) > 0) {
                candidates += { SearchNestBotScript(bot, nest, duration) }
            }
        }
        for (candidate in candidates.shuffled()) {
            candidate()?.let { return it }
        }
        return null
    }

    private fun rawFlax(bot: Bot): BotScript? {
        val current = owned(bot, FLAX)
        if (current >= MAX_RAW_FLAX_STOCK || world.economy.getPrice(FLAX) <= 0) return null
        val target = minOf(MAX_RAW_FLAX_STOCK.toLong(), current + 56).toInt()
        return HarvestBotScript(bot, HarvestBotScript.Companion.Harvestable.FLAX, rand(20, 40).minutes,
            ProductionZones.FLAX.toMutableList(), targetAmount = target, rawProfit = true)
    }

    private fun collectEggs(bot: Bot): BotScript? {
        val current = owned(bot, EGG)
        if (current >= MAX_EGG_STOCK || world.economy.getPrice(EGG) <= 0) return null
        val target = minOf(MAX_EGG_STOCK.toLong(), current + 16).toInt()
        return CollectGroundItemBotScript(bot, EGG, target, rand(15, 30).minutes,
            mutableListOf(SubZone.LUMBRIDGE_CHICKEN_COOP), sellOnCompletion = true)
    }

    private fun fillOwnedContainers(bot: Bot): BotScript? {
        val opportunities = buildList {
            for (source in Source.entries) {
                for (empty in source.emptyIds) {
                    if (empty == 229) continue
                    val filled = source.resource.getFilled(empty) ?: continue
                    val available = owned(bot, empty)
                    val current = owned(bot, filled)
                    if (available < 1 || current >= Int.MAX_VALUE) continue
                    val outputPrice = world.economy.getPrice(filled)
                    if (outputPrice <= 0 || outputPrice <= world.economy.getPrice(empty)) continue
                    val target = minOf(Int.MAX_VALUE.toLong(), current + minOf(available, 56)).toInt()
                    add(FillWaterBotScript(bot, empty, target, rand(20, 40).minutes, source))
                }
            }
        }
        return opportunities.randomOrNull()
    }

    private fun owned(bot: Bot, id: Int): Long = bot.bank.computeAmountForId(id).toLong() +
        bot.inventory.computeAmountForId(id)
}
