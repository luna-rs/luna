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
import game.bot.scripts.skills.MakePotteryBotScript
import game.bot.scripts.skills.CraftJewelleryBotScript
import game.bot.scripts.skills.MakeMoltenGlassBotScript
import game.bot.scripts.skills.BlowGlassBotScript
import game.skill.crafting.potteryCrafting.Unfired
import game.skill.crafting.glassMaking.GlassMaterial
import game.bot.scripts.skills.CutGemBotScript
import game.bot.scripts.skills.MakeBattlestaffBotScript
import game.bot.scripts.skills.MakeSoftClayBotScript
import game.bot.scripts.skills.WeaveTextileBotScript
import game.bot.scripts.skills.SpinTextileBotScript
import game.bot.scripts.skills.StringJewelleryBotScript
import game.bot.scripts.skills.TanHideBotScript
import game.skill.crafting.armorCrafting.HideArmor
import game.skill.crafting.battlestaffCrafting.Battlestaff
import game.skill.crafting.gemCutting.Gem
import game.skill.crafting.hideTanning.Hide
import game.skill.crafting.textileCrafting.Textile
import game.skill.crafting.textileCrafting.TextileType
import game.obj.resource.fillable.WaterResource
import io.luna.game.model.mob.bot.Bot
import io.luna.util.RandomUtils.roll

/**
 * Creates crafting scripts for bots.
 *
 * Training and profit selection first have a 25% chance to attempt gem cutting, battlestaff assembly,
 * or jewellery stringing when the bot owns the required inputs and tools. Selection uses the inherited
 * level/personality rules and includes owned opal, jade and red topaz; their native crushing outcomes remain
 * possible. Battlestaff recipes require matching charged orbs rather than an orb-charging route.
 * Stringing accepts existing unstrung gold amulets or silver symbols with wool at Crafting level one.
 * Non-training mode can also prepare soft clay using owned clay and filled water containers, without experience.
 * The existing cowhide collection, tanning, armour, bowstring, and flax branches remain the fallback activities.
 * Choosing a profit activity does not guarantee a market margin.
 *
 * Owned leather, snakeskin, dragonhide, or leather armour and studs enable armour production in both modes.
 * A single recipe qualifies; leather production also requires a needle and consumable thread.
 *
 * Owned molten glass and a glassblowing pipe also enable all seven existing glass products at levels 1–49.
 * Glassblowing is available in training and profit selection, using the player interface and configured XP.
 * Owned soda ash and buckets of sand also enable molten-glass production on the existing furnace routes.
 * Non-training production can first fill owned empty buckets at a sandpit when soda ash is already available.
 * Gold and silver jewellery recipes use the same furnace routes when the required bars, gems, and mould are owned.
 * Owned soft clay and unfired pottery enable all existing shaping/firing recipes in Barbarian Village.
 * Owned wool, jute, and willow branches also enable existing loom recipes in Falador and Iorwerth camp.
 * Owned wool, flax, and magic roots enable the three existing spinning-wheel recipes on the established wheel routes.
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

        val craftable = getActivities(level, { it.level }, HideArmor.ALL)
            .filter { CraftArmorBotScript.hasSupplies(bot, it) }

        // Use ready leather before spending more coins on unfinished hides.
        val craftArmor = getBestActivity(bot, level, { it.level }, craftable)
        if (craftArmor != null) {
            return CraftArmorBotScript(bot, craftArmor, getDuration(bot))
        }
        return TanHideBotScript(bot, getDuration(bot))
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
            return SpinTextileBotScript(bot, getDuration(bot))
        }
        if (getUntannedHides(bot) > 0 || randBoolean()) {
            return TanHideBotScript(bot, getDuration(bot))
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
    internal fun getCollectHidesScript(
        bot: Bot, zones: MutableList<SubZone>, pickUpHides: Boolean? = null
    ): CollectHidesBotScript {
        // TODO Skillers that never train combat should pick up hides other players leave instead of fighting.
        zones += if (bot.personality.isIntelligent) NEAR_BANK_COW_FIELDS else ALL_COW_FIELDS
        val collect = pickUpHides ?: (!bot.personality.isDumb || !roll(1 of 3))
        return CollectHidesBotScript(bot, getDuration(bot), zones, collect)
    }

    /**
     * Creates the level-one leather-glove fallback with a personality-based session duration.
     *
     * @param bot The bot that will run the script.
     * @return The basic armour-crafting activity.
     */
    fun getBasicScript(bot: Bot): CraftArmorBotScript = getBasicScript(bot, Textile.BOWSTRING.level)

    /**
     * Trains only through the existing glove recipe until the pending activity's real level gate is reached.
     */
    fun getBasicScript(bot: Bot, targetLevel: Int): CraftArmorBotScript =
        CraftArmorBotScript(bot, HideArmor.LEATHER_GLOVES, getDuration(bot), targetLevel = targetLevel)

    /**
     * Selects an owned Crafting production recipe or non-training soft-clay preparation activity.
     *
     * All native gem recipes are supported, including semi-precious gems. Candidates pass their permanent level
     * and owned-supply checks before the inherited level/personality selector chooses one. All candidates share
     * the same session duration and use the script's default processing zones. Actual current-level and safety
     * checks occur at startup/execution.
     *
     * @param bot The bot whose inventory, bank, and personality determine eligibility and selection.
     * @param level The Crafting level supplied by the coordinator to the inherited recipe selector.
     * @param training Whether the caller wants training; soft-clay preparation is excluded because it grants no XP.
     * @return An eligible production script, or null so the caller can select its existing fallback.
     */
    internal fun getProductionScript(bot: Bot, level: Int, training: Boolean): InventoryBotScript? {
        val duration = getDuration(bot)
        val candidates = buildList<Pair<Int, InventoryBotScript>> {
            addAll(Gem.entries.map { CutGemBotScript(bot, it, duration) }
                .filter { it.isEligible() }.map { it.requiredLevel to it })
            addAll(HideArmor.ALL.map { CraftArmorBotScript(bot, it, duration) }
                .filter { it.isEligible() }.map { it.requiredLevel to it })
            addAll(Battlestaff.entries.map { MakeBattlestaffBotScript(bot, it, duration) }
                .filter { it.isEligible() }.map { it.requiredLevel to it })
            addAll(StringJewelleryBotScript.UNSTRUNG_IDS.map { StringJewelleryBotScript(bot, it, duration) }
                .filter { it.isEligible() }.map { it.requiredLevel to it })
            addAll(GlassMaterial.entries.map { BlowGlassBotScript(bot, it, duration) }
                .filter { it.isEligible() }.map { it.requiredLevel to it })
            addAll(CraftJewelleryBotScript.RECIPES.keys.map { CraftJewelleryBotScript(bot, it, duration) }
                .filter { it.isEligible() }.map { it.requiredLevel to it })
            addAll(Unfired.entries.flatMap { material ->
                MakePotteryBotScript.Companion.Stage.entries.map { MakePotteryBotScript(bot, material, it, duration) }
            }.filter { it.isEligible() }.map { it.requiredLevel to it })
            addAll(Textile.entries.filter { it.type == TextileType.SPINNING_WHEEL }
                .map { SpinTextileBotScript(bot, duration, textile = it) }
                .filter { it.isEligible() }.map { it.requiredLevel to it })
            addAll(Textile.entries.filter { it.type == TextileType.LOOM }
                .map { WeaveTextileBotScript(bot, it, duration) }
                .filter { it.isEligible() }.map { it.requiredLevel to it })
            val moltenGlass = MakeMoltenGlassBotScript(bot, duration)
            if (moltenGlass.isEligible() || (!training && moltenGlass.canPrepareSand()))
                add(moltenGlass.requiredLevel to moltenGlass)
            if (!training) {
                addAll(WaterResource.FILLED_IDS.map { MakeSoftClayBotScript(bot, it, duration) }
                    .filter { it.isEligible() || it.canPrepareWater() }.map { it.requiredLevel to it })
            }
        }
        return getBestActivity(bot, level, { it.first }, candidates)?.second
    }
}
