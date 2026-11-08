package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.script.VoidBotScript
import api.bot.zone.SubZone
import api.predef.*
import engine.bot.coordinator.CombatCoordinator
import game.bot.scripts.skills.AlchemyBotScript
import game.bot.scripts.RechargeIbanBotScript
import game.combat.spells.IbanStaff
import game.bot.scripts.skills.ChargeOrbBotScript
import game.bot.scripts.skills.EnchantJewelleryBotScript
import game.bot.scripts.skills.SplashBotScript
import game.bot.scripts.skills.SuperheatItemBotScript
import game.bot.scripts.skills.BonesToFoodBotScript
import game.bot.scripts.skills.TeleportTrainingBotScript
import game.bot.scripts.skills.TeleOtherBotScript
import game.skill.magic.teleOther.TeleOtherType
import game.bot.scripts.skills.TelegrabBotScript
import game.skill.magic.bonesToItems.BonesToItemsType
import game.skill.magic.chargeOrb.ChargeOrbType
import game.skill.magic.lowHighAlch.AlchemyType
import game.skill.magic.superheatItem.SuperheatItemAction
import game.skill.smithing.BarType
import io.luna.game.model.mob.bot.Bot
import io.luna.util.RandomUtils

/**
 * Creates bot scripts used to train Magic.
 *
 * Weighted method selection gives alchemy and wine collection regular opportunities alongside supplied production.
 * Non-skillers can also use the shared NPC camp preparation with an enforced Magic-only combat discipline.
 * Missing alchemy and wine runes are prepared after selection; protected supplies are never alchemy targets.
 * Unavailable methods are removed before another choice, so a missing recipe cannot starve the remaining methods.
 * Training retains city teleports and TeleOther as lower-weight alternatives and splashing as a fallback.
 * Profit selection excludes those rune-spending fallbacks and requires alchemy's positive net margin.
 *
 * @author lare96
 */
object MagicScriptFactory : SkillingScriptFactory(SKILL_MAGIC) {

    /**
     * Distinct methods compete by weight instead of a fixed production priority chain.
     *
     * @author lare96
     */
    internal enum class Method {
        COMBAT, ALCHEMY, WINE, ORBS, ENCHANTING, SUPERHEAT, BONES, TELEPORT, TELEOTHER
    }

    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        selectScript(bot, level, profit = false)

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

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        selectScript(bot, level, profit = true)

    /**
     * Runes are acquired by the inventory lifecycle after selection; profit alone requires a positive guide margin.
     */
    internal fun getTelegrabScript(bot: Bot, profit: Boolean): TelegrabBotScript? =
        TelegrabBotScript(bot, TelegrabBotScript.WINE, profit, getDuration(bot), TelegrabBotScript.wineZones(bot, profit))
            .takeIf { it.isEligible() }

    /**
     * Weights unlocked methods before their owned-input, account and location checks are resolved lazily.
     * Alchemy and wine are intentional choices; food conversion cannot always displace them.
     */
    internal fun methodWeights(bot: Bot, level: Int, profit: Boolean): Map<Method, Double> = buildMap {
        if (!bot.preferences.isSkiller) put(Method.COMBAT, if (profit) 2.0 else 4.0)
        if (level >= AlchemyType.LOW.level) put(Method.ALCHEMY, 4.0)
        if (level >= 33) put(Method.WINE, if (profit) 4.0 else 3.0)
        if (level >= 56) put(Method.ORBS, 2.0)
        if (level >= 7) put(Method.ENCHANTING, 1.0)
        if (level >= SuperheatItemAction.LEVEL) put(Method.SUPERHEAT, 1.0)
        if (level >= 15) put(Method.BONES, 1.0)
        if (!profit) {
            if (level >= 25) put(Method.TELEPORT, 1.0)
            if (level >= 74) put(Method.TELEOTHER, 0.5)
        }
    }

    /**
     * Resolves at most one candidate per method, retaining each method's native eligibility checks.
     * Expensive gear and reserve planning runs only when its method is actually drawn.
     */
    private fun selectScript(bot: Bot, level: Int, profit: Boolean): BotScript {
        getIbanRechargeScript(bot)?.let { return it }
        val remaining = methodWeights(bot, level, profit).toMutableMap()
        while (remaining.isNotEmpty()) {
            val method = RandomUtils.weightedRoll(remaining) ?: break
            remaining.remove(method)
            val script = when (method) {
                Method.COMBAT -> CombatCoordinator.magicTrainingScript(bot, !profit, getDuration(bot))
                Method.ALCHEMY -> getAlchemyScript(bot, level, profit)
                Method.WINE -> getTelegrabScript(bot, profit)
                Method.ORBS -> getOrbScript(bot, level)
                Method.ENCHANTING -> getEnchantScript(bot, level)
                Method.SUPERHEAT -> getSuperheatScript(bot, level)
                Method.BONES -> getBonesToFoodScript(bot, level)
                Method.TELEPORT -> getTeleportScript(bot, level)
                Method.TELEOTHER -> {
                    val choices = TeleOtherType.entries.map { TeleOtherBotScript(bot, it, getDuration(bot)) }
                        .filter { it.isEligible() }
                    getBestActivity(bot, level, { it.type.level }, choices)
                }
            }
            if (script != null) return script
        }
        return if (profit) VoidBotScript(bot) else SplashBotScript(bot, getDuration(bot))
    }

    /**
     * Restocks a nearly exhausted owned staff through the native well, without starting or completing a quest.
     */
    private fun getIbanRechargeScript(bot: Bot): RechargeIbanBotScript? =
        RechargeIbanBotScript(bot, getDuration(bot)).takeIf { IbanStaff.remaining(bot) < 20 && it.isEligible() }

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
    internal fun getAlchemyScript(bot: Bot, level: Int, profit: Boolean = false): AlchemyBotScript? {
        val type = when {
            level >= AlchemyType.HIGH.level -> AlchemyType.HIGH
            level >= AlchemyType.LOW.level -> AlchemyType.LOW
            else -> return null
        }

        return AlchemyBotScript(bot, type, getDuration(bot), profit = profit).takeIf { it.isEligible() }
    }
}
