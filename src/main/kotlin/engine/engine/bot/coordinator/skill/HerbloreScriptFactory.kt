package engine.bot.coordinator.skill

import api.bot.script.InventoryBotScript
import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.bot.scripts.skills.IdentifyHerbBotScript
import game.bot.scripts.skills.GrindIngredientBotScript
import game.bot.scripts.skills.MakePotionBotScript
import game.bot.scripts.skills.TelegrabBotScript
import game.bot.scripts.SinisterChestBotScript
import game.skill.herblore.makePotion.FinishedPotion
import game.bot.scripts.skills.MakeUnfPotionBotScript
import game.bot.scripts.skills.HerbloreProduction
import game.skill.herblore.grindIngredient.Ingredient
import game.skill.herblore.makeUnfPotion.UnfPotion
import game.skill.herblore.identifyHerb.Herb
import io.luna.game.model.mob.bot.Bot

/**
 * Creates herb-identification and finished-potion sessions from the bot's owned supplies.
 *
 * Finished potions take precedence over preparation, and owned clean herbs can start a potion session even
 * without water or secondary ingredients. Native scripts acquire missing consumables and continue each
 * successful stage using the remaining session budget. Training ranks XP-bearing recipes by their XP award;
 * profit retains the ordinary level/personality selection. Grinding and unfinished-potion preparation award no XP;
 * selecting a profit activity does not guarantee a market margin.
 * Owned sinister keys can instead supply native chest herbs through a prepared Yanille Dungeon trip.
 *
 * When no recipe is available, identification queues one herb-gathering trip toward a stable 100-200-guam
 * restocking goal. Its continuation processes smaller hauls too; an empty return ends without recursively gathering.
 *
 * @author lare96
 */
object HerbloreScriptFactory : SkillingScriptFactory(SKILL_HERBLORE) {
    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        getProductionScript(bot, level, true)?.let { return it }
        getIngredientGatheringScript(bot)?.let { return it }
        return IdentifyHerbBotScript(bot, Herb.GUAM_LEAF, getDuration(bot))
    }

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        if (SinisterChestBotScript.ready(bot)) {
            zones += SubZone.YANILLE_DUNGEON_SINISTER_CHEST
            return SinisterChestBotScript(bot, getDuration(bot))
        }
        return getProductionScript(bot, level, false) ?: getIngredientGatheringScript(bot) ?:
            getTrainingScript(bot, level, zones)
    }

    /**
     * Gathers missing ranging-potion wine when the bot owns unfinished potions but cannot mix them yet.
     * This Herblore-owned trip continues into finished-potion mixing after collecting a real haul.
     */
    internal fun getIngredientGatheringScript(bot: Bot): TelegrabBotScript? {
        val potion = FinishedPotion.RANGING_POTION
        if (bot.herblore.staticLevel < potion.level || bot.itemTracker.count(potion.unf) < 1 ||
            bot.itemTracker.count(potion.secondary) > 0) return null
        return TelegrabBotScript(bot, TelegrabBotScript.WINE, false, getDuration(bot), TelegrabBotScript.wineZones(bot),
            herbloreContinuation = true)
            .takeIf { it.isEligible() }
    }

    /**
     * Prefers finished work, then preparation from owned herbs, then identification or ingredient grinding.
     *
     * Scripts use their default processing zones and recheck current level and safety during execution.
     *
     * @param bot The bot whose stock and personality determine eligibility and selection.
     * @param level The Herblore level supplied by the coordinator to the inherited recipe selector.
     * @param training Whether XP rather than recipe level ranks finished and identification work.
     * @return An eligible script, or null when no usable owned recipe is available in the requested mode.
     */
    internal fun getProductionScript(bot: Bot, level: Int, training: Boolean): InventoryBotScript? {
        val duration = getDuration(bot)
        val finished = FinishedPotion.entries.map { MakePotionBotScript(bot, it, duration) }
            .filter { it.isEligible() }
        val ready = finished.filter { bot.itemTracker.count(it.potion.secondary) > 0 }
        fun chooseFinished(options: List<MakePotionBotScript>): MakePotionBotScript? =
            if (training) selectRankedActivity(bot, options.sortedByDescending { it.potion.exp })
            else getBestActivity(bot, level, { it.requiredLevel }, options)
        chooseFinished(ready)?.let { return it }
        val preparation = finished.mapNotNull { script ->
            HerbloreProduction.mixing(bot, script.potion, duration)?.let { script to it }
        }
        val prepared = if (training) selectRankedActivity(bot, preparation.sortedByDescending { it.first.potion.exp })
            else getBestActivity(bot, level, { it.first.requiredLevel }, preparation)
        prepared?.second?.let { return it }
        val unfinished = UnfPotion.entries.map { MakeUnfPotionBotScript(bot, it, duration) }.filter { it.isEligible() }
        getBestActivity(bot, level, { it.requiredLevel }, unfinished)?.let { return it }
        val identification = Herb.entries.map { IdentifyHerbBotScript(bot, it, duration) }.filter { it.isEligible() }
        val identified = if (training) selectRankedActivity(bot, identification.sortedByDescending { it.herb.exp })
            else getBestActivity(bot, level, { it.requiredLevel }, identification)
        if (identified != null) return identified
        val grinding = Ingredient.entries.map { GrindIngredientBotScript(bot, it, duration) }.filter { it.isEligible() }
        return getBestActivity(bot, level, { it.requiredLevel }, grinding)
    }
}
