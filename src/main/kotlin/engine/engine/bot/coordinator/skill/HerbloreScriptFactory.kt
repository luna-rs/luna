package engine.bot.coordinator.skill

import api.bot.script.InventoryBotScript
import api.bot.script.BotScript
import api.bot.script.DynamicBotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.IdentifyHerbBotScript
import game.bot.scripts.skills.GrindIngredientBotScript
import game.skill.herblore.grindIngredient.Ingredient
import game.skill.herblore.identifyHerb.Herb
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.SkillSet

/**
 * Creates herb-identification sessions from the bot's owned unidentified herbs.
 *
 * Training selects herb identification. Profit mode also considers ingredient grinding when the bot owns
 * a pestle and mortar and an unprocessed ingredient. Candidates require their permanent level and supplies
 * in inventory or bank, then use the inherited level/personality selector. Grinding awards no experience;
 * selecting a profit activity does not guarantee a market margin.
 *
 * Before either mode checks recipes or supplies, bots below permanent level three receive only the experience
 * needed to reach that level. This supplies the initial Herblore unlock while bot questing is unavailable.
 * Normal experience-change listeners handle the level-up; bots already at or above level three receive no XP.
 *
 * When no recipe is available, a short-lived fallback requests unidentified guam and completes immediately.
 * It records demand without buying herbs.
 *
 * @author lare96
 */
object HerbloreScriptFactory : SkillingScriptFactory(SKILL_HERBLORE) {
    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        val selectionLevel = ensureMinimumLevel(bot, level)
        getProductionScript(bot, selectionLevel, true)?.let { return it }
        return object : DynamicBotScript(bot) {
            override suspend fun run(): Boolean {
                // The implemented herb-identification recipes begin at level 3.
                if (bot.herblore.staticLevel >= Herb.GUAM_LEAF.level) {
                    bot.preferences.addWantedItem(Herb.GUAM_LEAF.id, 28)
                }
                return true
            }
        }
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
     * Selects owned herb identification or profit-only grinding with inherited level/personality rules.
     *
     * Scripts use their default processing zones and recheck current level and safety during execution.
     *
     * @param bot The bot whose stock and personality determine eligibility and selection.
     * @param level The Herblore level supplied by the coordinator to the inherited recipe selector.
     * @param training Whether to exclude grinding, which prepares supplies without awarding experience.
     * @return An eligible script, or null when no usable owned recipe is available in the requested mode.
     */
    internal fun getProductionScript(bot: Bot, level: Int, training: Boolean): InventoryBotScript? {
        val duration = getDuration(bot)
        val candidates = buildList<Pair<Int, InventoryBotScript>> {
            addAll(Herb.entries.map { IdentifyHerbBotScript(bot, it, duration) }
                .filter { it.isEligible() }.map { it.requiredLevel to it })
            if (!training) {
                addAll(Ingredient.entries.map { GrindIngredientBotScript(bot, it, duration) }
                    .filter { it.isEligible() }.map { it.requiredLevel to it })
            }
        }
        return getBestActivity(bot, level, { it.first }, candidates)?.second
    }
}
