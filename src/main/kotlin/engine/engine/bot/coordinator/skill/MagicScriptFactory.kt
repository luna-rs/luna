package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import engine.bot.coordinator.CombatCoordinator
import game.bot.scripts.skills.AlchemyBotScript
import game.bot.scripts.skills.ChargeOrbBotScript
import game.bot.scripts.skills.EnchantJewelleryBotScript
import game.bot.scripts.skills.SplashBotScript
import game.bot.scripts.skills.SuperheatItemBotScript
import game.bot.scripts.skills.BonesToFoodBotScript
import game.bot.scripts.skills.TeleportTrainingBotScript
import game.bot.scripts.skills.TelegrabBotScript
import game.skill.magic.bonesToItems.BonesToItemsType
import game.skill.magic.chargeOrb.ChargeOrbType
import game.skill.magic.lowHighAlch.AlchemyType
import game.skill.magic.superheatItem.SuperheatItemAction
import game.skill.smithing.BarType
import io.luna.game.model.mob.bot.Bot

/**
 * Creates bot scripts used to train Magic.
 *
 * Production selection is shared by training and profit; Telegrab gathering is restricted to profit selection.
 * Owned unpowered orbs and spell costs enable obelisk charging first. Owned jewellery and spell costs
 * enable all existing enchantments next, at the normal safe processing hubs. Owned ores and spell costs
 * then enable superheating when both skill requirements are met. Owned normal bones and spell costs can prepare
 * bananas or peaches up to the food stock target at the same safe hubs. Eligible alchemy follows; splashing
 * remains the fallback. Production activities use the normal eligibility and personality rules.
 * Orb selection uses the existing level/personality policy and the regular spellbook; each script carries
 * its matching obelisk subzone and uses the shared travel and banking system.
 *
 * Training also has a 25% chance to consider owned city-teleport supplies before production. Supported safe
 * teleports use the normal level/personality selector; profit selection never spends runes on teleport training.
 * Eligible combat-level-60 bots with an owned Ancient staff and fifty casts strongly prefer combat Magic (95%).
 * Training and profit combat share the normal camp selection and combat preparation lifecycle.
 *
 * @author lare96
 */
object MagicScriptFactory : SkillingScriptFactory(SKILL_MAGIC) {

    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        if (rand(0.95)) CombatCoordinator.ancientMagicScript(bot, true, getDuration(bot))?.let { return it }
        if (rand(0.25)) getTeleportScript(bot, level)?.let { return it }
        return getProductionScript(bot, level)
    }

    /**
     * Selects a safe city teleport from owned rune costs without changing the bot's equipped staff or spellbook.
     * The inherited policy preserves casual variety and prefers higher-level available spells for efficient bots.
     */
    internal fun getTeleportScript(bot: Bot, level: Int): TeleportTrainingBotScript? {
        val duration = getDuration(bot)
        val options = TeleportTrainingBotScript.SITES.keys.map { TeleportTrainingBotScript(bot, it, duration) }
            .filter { it.isEligible() }
        return getBestActivity(bot, level, { it.spell.level }, options)
    }

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        if (rand(0.95)) CombatCoordinator.ancientMagicScript(bot, false, getDuration(bot))?.let { return it }
        val telegrab = TelegrabBotScript(bot, TelegrabBotScript.WINE, true, getDuration(bot), TelegrabBotScript.wineZones(bot, profit = true))
        if (telegrab.isEligible()) return telegrab
        return getProductionScript(bot, level)
    }

    /**
     * Retains the existing production and fallback order without selecting profit-only gathering in training.
     */
    private fun getProductionScript(bot: Bot, level: Int): BotScript {
        getOrbScript(bot, level)?.let { return it }
        getEnchantScript(bot, level)?.let { return it }
        getSuperheatScript(bot, level)?.let { return it }
        getBonesToFoodScript(bot, level)?.let { return it }
        val alchemy = getAlchemyScript(bot, level)
        if (alchemy != null) {
            return alchemy
        }
        return SplashBotScript(bot, getDuration(bot))
    }

    /**
     * Selects an eligible native food-conversion spell with the normal level/personality policy. Existing stock
     * limits preparation sessions; missing inputs leave other Magic activities available.
     */
    internal fun getBonesToFoodScript(bot: Bot, level: Int): BonesToFoodBotScript? {
        val duration = getDuration(bot)
        val options = BonesToItemsType.entries.map { BonesToFoodBotScript(bot, it, duration) }
            .filter { it.isEligible() }
        return getBestActivity(bot, level, { it.type.level }, options)
    }

    /**
     * Selects an owned orb-charging activity using the inherited level and personality rules.
     * Each configured spell uses its own obelisk; missing supplies or a different spellbook exclude it.
     *
     * @param bot The bot whose stock, permanent level, and spellbook determine eligibility.
     * @param level Current Magic level used by the factory's normal selection policy.
     * @return An eligible charging script, or `null` so alchemy and splashing can be considered.
     */
    internal fun getOrbScript(bot: Bot, level: Int): ChargeOrbBotScript? {
        val duration = getDuration(bot)
        val options = mapOf(
            ChargeOrbType.WATER to SubZone.WATER_OBELISK,
            ChargeOrbType.EARTH to SubZone.EARTH_OBELISK,
            ChargeOrbType.FIRE to SubZone.FIRE_OBELISK,
            ChargeOrbType.AIR to SubZone.AIR_OBELISK
        ).map { (type, zone) -> ChargeOrbBotScript(bot, type, duration, mutableListOf(zone)) }
            .filter { it.isEligible() }
        return getBestActivity(bot, level, { it.requiredLevel }, options)
    }

    /**
     * Selects owned jewellery enchanting with the inherited level/personality policy.
     * Candidates come only from the player spell maps and require the regular spellbook and one cast's inputs.
     *
     * @param bot The bot whose jewellery, rune stock, equipment, and permanent level determine eligibility.
     * @param level Current Magic level used by the normal factory selection policy.
     * @return An eligible enchantment, or `null` to continue to alchemy/splashing.
     */
    internal fun getEnchantScript(bot: Bot, level: Int): EnchantJewelleryBotScript? {
        val duration = getDuration(bot)
        val options = EnchantJewelleryBotScript.RECIPES.map { (type, inputId) ->
            EnchantJewelleryBotScript(bot, type, inputId, duration)
        }.filter { it.isEligible() }
        return getBestActivity(bot, level, { it.requiredLevel }, options)
    }

    /**
     * Selects an owned superheating recipe using the inherited Smithing level and personality policy.
     * Magic must meet the spell's requirement; both permanent levels, spellbook, and supplies are validated.
     *
     * @param bot The bot whose ore stock, rune costs, and skill levels determine eligibility.
     * @param level Current Magic level supplied by the normal factory selection path.
     * @return An eligible bar conversion, or `null` to continue to alchemy and splashing.
     */
    internal fun getSuperheatScript(bot: Bot, level: Int): SuperheatItemBotScript? {
        if (level < SuperheatItemAction.LEVEL) return null
        val duration = getDuration(bot)
        val options = BarType.entries.map { SuperheatItemBotScript(bot, it, duration) }
            .filter { it.isEligible() }
        return getBestActivity(bot, bot.smithing.level, { it.barType.level }, options)
    }

    /**
     * Creates the highest-level alchemy script currently available to [bot].
     *
     * High alchemy is preferred from level 55 onward and low alchemy is used from level 21. The bot must also own at
     * least one suitable alchemy target. Rune requirements are handled later by [AlchemyBotScript].
     *
     * @param bot The bot that will run the script.
     * @param level The bot's current Magic level.
     * @return An alchemy script, or `null` when alchemy is unavailable.
     */
    private fun getAlchemyScript(bot: Bot, level: Int): AlchemyBotScript? {
        if (!AlchemyBotScript.hasAlchableItems(bot)) {
            return null
        }

        val type = when {
            level >= AlchemyType.HIGH.level -> AlchemyType.HIGH
            level >= AlchemyType.LOW.level -> AlchemyType.LOW
            else -> return null
        }

        return AlchemyBotScript(bot, type, getDuration(bot))
    }
}
