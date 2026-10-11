package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.AlchemyBotScript
import game.bot.scripts.skills.ChargeOrbBotScript
import game.bot.scripts.skills.SplashBotScript
import game.skill.magic.chargeOrb.ChargeOrbType
import game.skill.magic.lowHighAlch.AlchemyType
import io.luna.game.model.mob.bot.Bot

/**
 * Creates bot scripts used to train Magic.
 *
 * Magic currently shares its activity selection between normal and profit-oriented training.
 * Owned unpowered orbs and spell costs enable obelisk charging. Eligible alchemy items provide the next
 * choice when the required level is met; splashing remains the fallback.
 * Orb selection uses the existing level/personality policy and the regular spellbook; each script carries
 * its matching obelisk subzone and uses the shared travel and banking system.
 *
 * TODO Add teleportation-based Magic training. Bots should be able to repeatedly cast suitable teleport spells when
 *      their level and rune supply make teleporting a reasonable training method.
 *
 * @author lare96
 */
object MagicScriptFactory : SkillingScriptFactory(SKILL_MAGIC) {

    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        return getProfitScript(bot, level, zones)
    }

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        getOrbScript(bot, level)?.let { return it }
        val alchemy = getAlchemyScript(bot, level)
        if (alchemy != null) {
            return alchemy
        }
        return SplashBotScript(bot, getDuration(bot))
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
            ChargeOrbType.AIR to SubZone.AIR_OBELISK
        ).map { (type, zone) -> ChargeOrbBotScript(bot, type, duration, mutableListOf(zone)) }
            .filter { it.isEligible() }
        return getBestActivity(bot, level, { it.requiredLevel }, options)
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