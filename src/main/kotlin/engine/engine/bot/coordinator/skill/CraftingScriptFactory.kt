package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.script.InventoryBotScript
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.bot.scripts.HarvestBotScript
import game.bot.scripts.HarvestBotScript.Companion.Harvestable
import game.bot.scripts.skills.CollectHidesBotScript
import game.bot.scripts.skills.CraftArmorBotScript
import game.bot.scripts.skills.CutGemBotScript
import game.bot.scripts.skills.SpinFlaxBotScript
import game.bot.scripts.skills.TanHideBotScript
import game.skill.crafting.armorCrafting.HideArmor
import game.skill.crafting.gemCutting.Gem
import game.skill.crafting.hideTanning.Hide
import game.skill.crafting.textileCrafting.Textile
import io.luna.game.model.mob.bot.Bot
import io.luna.util.RandomUtils.roll

/**
 * Creates crafting scripts for bots.
 *
 * Training and profit selection first have a 25% chance to attempt precious-gem cutting when the bot owns a chisel
 * and an eligible uncut gem. Selection uses the inherited level/personality rules and excludes semi-precious gems.
 * The existing cowhide collection, tanning, armour, bowstring, and flax branches remain the fallback activities.
 * Choosing a profit activity does not guarantee a market margin.
 *
 * @author lare96
 */
object CraftingScriptFactory : SkillingScriptFactory(SKILL_CRAFTING) {

    /**
     * Cow fields with a bank close by. Intelligent bots only collect hides here.
     */
    private val NEAR_BANK_COW_FIELDS = listOf(SubZone.NORTH_YANILLE_COW_FIELD,
                                              SubZone.NORTH_LUMBRIDGE_COW_FIELD,
                                              SubZone.NORTH_ARDOUGNE_COW_FIELD,
                                              SubZone.CRAFTING_GUILD_COW_FIELD)

    /**
     * Every cow field bots collect hides in.
     */
    private val ALL_COW_FIELDS = NEAR_BANK_COW_FIELDS + SubZone.LUMBRIDGE_COW_PEN

    override fun getTrainingScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        if (rand(0.25)) {
            getProductionScript(bot, level, true)?.let { return it }
        }
        // TODO
        //  battlestaff crafting
        //  glass making
        //  jewellery making
        //  pottery crafting
        //  textile crafting (wool/silk etc.)

        // TODO pottery making as level 1 alternative when we don't have soft leather

        val hides = getUntannedHides(bot)
        val craftable = getActivities(level, { it.level }, HideArmor.ALL)
            .filter { CraftArmorBotScript.hasSupplies(bot, it) }

        // Hides alongside craftable leather means tanning was cut short, so it finishes before any crafting.
        if (hides >= CollectHidesBotScript.hideTarget(bot) || (hides > 0 && craftable.isNotEmpty())) {
            return TanHideBotScript(bot, getDuration(bot))
        }
        val craftArmor = getBestActivity(bot, level, { it.level }, craftable)
        if (craftArmor != null) {
            return CraftArmorBotScript(bot, craftArmor, getDuration(bot))
        }
        return getCollectHidesScript(bot, zones)
    }

    override fun getProfitScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        if (rand(0.25)) {
            getProductionScript(bot, level, false)?.let { return it }
        }
        if (randBoolean() && level >= Textile.BOWSTRING.level) {
            zones += SubZone.FLAX_SPINNING_MAIN
            return SpinFlaxBotScript(bot, getDuration(bot))
        }
        if (getUntannedHides(bot) >= CollectHidesBotScript.hideTarget(bot)) {
            return TanHideBotScript(bot, getDuration(bot))
        }
        if (randBoolean()) {
            return getCollectHidesScript(bot, zones)
        }
        zones += SubZone.SOUTH_SEERS_VILLAGE_FLAX
        return HarvestBotScript(bot, Harvestable.FLAX, getDuration(bot), zones)
    }

    /**
     * Counts every untanned hide the bot owns.
     *
     * @param bot The bot to check.
     * @return The number of untanned hides.
     */
    private fun getUntannedHides(bot: Bot): Int {
        return Hide.HIDE_TO_HIDE.keys.sumOf { bot.itemTracker.count(it) }
    }

    /**
     * Creates a script that collects cowhides.
     *
     * Intelligent bots only use [NEAR_BANK_COW_FIELDS]. Dumb bots sometimes leave the hides on the ground.
     *
     * @param bot The bot that will run the script.
     * @param zones The mutable zone list to populate with cow fields.
     * @return The script.
     */
    private fun getCollectHidesScript(bot: Bot, zones: MutableList<SubZone>): CollectHidesBotScript {
        // TODO Skillers that never train combat should pick up hides other players leave instead of fighting.
        zones += if (bot.personality.isIntelligent) NEAR_BANK_COW_FIELDS else ALL_COW_FIELDS
        val pickUpHides = !bot.personality.isDumb || !roll(1 of 3)
        return CollectHidesBotScript(bot, getDuration(bot), zones, pickUpHides)
    }

    /**
     * Creates the level-one leather-glove fallback with a personality-based session duration.
     *
     * @param bot The bot that will run the script.
     * @return The basic armour-crafting activity.
     */
    fun getBasicScript(bot: Bot): CraftArmorBotScript {
        // todo chance of pottery crafting
        return CraftArmorBotScript(bot, HideArmor.LEATHER_GLOVES, getDuration(bot))
    }

    /**
     * Selects a precious-gem recipe for which the bot owns both a chisel and at least one uncut gem.
     *
     * Semi-precious recipes are excluded. Candidates pass their permanent level and owned-supply checks before
     * the inherited level/personality selector chooses one. All candidates share the same session duration and
     * use the script's default processing zones. Actual current-level and safety checks occur at startup/execution.
     *
     * @param bot The bot whose inventory, bank, and personality determine eligibility and selection.
     * @param level The Crafting level supplied by the coordinator to the inherited recipe selector.
     * @param training Whether the caller wants training; precious gems are eligible for either activity mode.
     * @return An eligible gem-cutting script, or null so the caller can select its existing fallback.
     */
    internal fun getProductionScript(bot: Bot, level: Int, training: Boolean): InventoryBotScript? {
        val duration = getDuration(bot)
        val candidates = Gem.entries.filter { !it.isSemiPrecious() }
            .map { CutGemBotScript(bot, it, duration) }
            .filter { it.isEligible() }
            .map { it.requiredLevel to it }
        return getBestActivity(bot, level, { it.first }, candidates)?.second
    }
}
