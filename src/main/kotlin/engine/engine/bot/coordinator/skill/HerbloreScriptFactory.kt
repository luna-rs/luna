package engine.bot.coordinator.skill

import api.bot.script.InventoryBotScript
import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.IdentifyHerbBotScript
import game.bot.scripts.skills.GrindIngredientBotScript
import game.bot.scripts.skills.MakePotionBotScript
import game.skill.herblore.makePotion.FinishedPotion
import game.bot.scripts.skills.MakeUnfPotionBotScript
import game.skill.herblore.grindIngredient.Ingredient
import game.skill.herblore.makeUnfPotion.UnfPotion
import game.skill.herblore.identifyHerb.Herb
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.SkillSet

/**
 * Creates herb-identification and finished-potion sessions from the bot's owned supplies.
 *
 * Training selects herb identification or finished-potion mixing. Finished recipes consume an unfinished
 * potion and its secondary ingredient through the existing player dialogue and award their configured XP.
 * Both modes also consider grinding with an owned pestle and mortar,
 * and unfinished potions with owned identified herbs and water vials. Candidates require their permanent level
 * and supplies in inventory or bank, then use the inherited level/personality selector.
 * Grinding and unfinished-potion preparation award no XP;
 * selecting a profit activity does not guarantee a market margin.
 *
 * Before either mode checks recipes or supplies, bots below permanent level three receive only the experience
 * needed to reach that level. This supplies the initial Herblore unlock while bot questing is unavailable.
 * Normal experience-change listeners handle the level-up; bots already at or above level three receive no XP.
 *
 * When no recipe is available, identification queues herb gathering until the stable 100-200-guam
 * batch is available. A failed gathering attempt ends its continuation without recursively gathering.
 *
 * @author lare96
 */
object HerbloreScriptFactory : SkillingScriptFactory(SKILL_HERBLORE) {
    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        val selectionLevel = ensureMinimumLevel(bot, level)
        getProductionScript(bot, selectionLevel, true)?.let { return it }
        return IdentifyHerbBotScript(bot, Herb.GUAM_LEAF, getDuration(bot))
    }

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        val selectionLevel = ensureMinimumLevel(bot, level)
        return getProductionScript(bot, selectionLevel, false) ?: getTrainingScript(bot, selectionLevel, zones)
    }

    /**
     * Supplies the XP missing from the level-three threshold before any recipe or stock checks.
     *
     * [io.luna.game.model.mob.Skill.addExperience] preserves normal skill-change events and uses an unscaled
     * multiplier for bots. Existing XP is retained, and the check is idempotent after the first adjustment.
     * The returned level accounts for callers that supplied the bot's level before the adjustment.
     *
     * @param bot The bot whose initial Herblore unlock is being supplied.
     * @param level The level supplied by the coordinator before entering the factory.
     * @return The selection level including any level gained from the adjustment.
     */
    private fun ensureMinimumLevel(bot: Bot, level: Int): Int {
        val herblore = bot.herblore
        if (herblore.staticLevel < Herb.GUAM_LEAF.level) {
            herblore.addExperience(SkillSet.experienceForLevel(Herb.GUAM_LEAF.level) - herblore.experience)
        }
        return maxOf(level, herblore.staticLevel)
    }

    /**
     * Selects identification, finished potions, or ingredient/potion preparation in either mode with inherited level/personality rules.
     *
     * Scripts use their default processing zones and recheck current level and safety during execution.
     *
     * @param bot The bot whose stock and personality determine eligibility and selection.
     * @param level The Herblore level supplied by the coordinator to the inherited recipe selector.
     * @param training Retained for callers; both modes permit preparation needed to reach finished potions.
     * @return An eligible script, or null when no usable owned recipe is available in the requested mode.
     */
    internal fun getProductionScript(bot: Bot, level: Int, training: Boolean): InventoryBotScript? {
        val duration = getDuration(bot)
        val candidates = buildList<Pair<Int, InventoryBotScript>> {
            addAll(Herb.entries.map { IdentifyHerbBotScript(bot, it, duration) }
                .filter { it.isEligible() }.map { it.requiredLevel to it })
            addAll(FinishedPotion.entries.map { MakePotionBotScript(bot, it, duration) }
                .filter { it.isEligible() }.map { it.requiredLevel to it })
            addAll(Ingredient.entries.map { GrindIngredientBotScript(bot, it, duration) }
                .filter { it.isEligible() }.map { it.requiredLevel to it })
            addAll(UnfPotion.entries.map { MakeUnfPotionBotScript(bot, it, duration) }
                .filter { it.isEligible() }.map { it.requiredLevel to it })
        }
        return getBestActivity(bot, level, { it.first }, candidates)?.second
    }
}
