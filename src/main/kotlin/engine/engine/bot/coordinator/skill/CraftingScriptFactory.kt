package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.HarvestBotScript
import game.bot.scripts.HarvestBotScript.Companion.Harvestable
import game.bot.scripts.skills.CraftArmorBotScript
import game.bot.scripts.skills.SpinFlaxBotScript
import game.bot.scripts.skills.TanHideBotScript
import game.skill.crafting.armorCrafting.HideArmor
import game.skill.crafting.hideTanning.Hide
import game.skill.crafting.textileCrafting.Textile
import io.luna.game.model.mob.bot.Bot

/**
 * Creates crafting scripts for bots.
 *
 * Crafting currently supports leather armour crafting for training, and flax, bowstring, or hide-tanning behaviour for
 * profit. Additional crafting branches can be added here as their bot scripts become available.
 *
 * @author lare96
 */
object CraftingScriptFactory : SkillingScriptFactory(SKILL_CRAFTING) {

    /**
     * Creates a crafting training script for the bot's current level.
     *
     * Training currently chooses the best available [HideArmor] option for the bot. If no armour option can be selected,
     * the bot falls back to harvesting flax so it still performs a simple crafting-related activity.
     *
     * @param bot The bot that will run the script.
     * @param level The bot's current crafting level.
     * @param zones The candidate zones available to the factory.
     *
     * @return A crafting script suitable for training.
     */
    override fun getTrainingScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        // TODO
        //  gem cutting
        //  battlestaff crafting
        //  glass making
        //  jewellery making
        //  pottery crafting
        //  textile crafting (wool/silk etc.)

        // Armor crafting starts at level 1 so it's always the fallback.
        val craftArmor = getBestActivity(bot, level, { it.level }, HideArmor.ALL)
        if (craftArmor != null) {
            return CraftArmorBotScript(bot, craftArmor, getDuration(bot))
        } else {
            // Worst case scenario, go pick flax.
            zones += SubZone.SOUTH_SEERS_VILLAGE_FLAX
            return HarvestBotScript(bot, Harvestable.FLAX, getDuration(bot), zones)
        }
    }

    /**
     * Creates a crafting profit script for the bot's current level and bank contents.
     *
     * Bots that can make bowstrings may spin flax when the random branch is selected. Otherwise, the factory scans the
     * bot's bank for any recognised hide and sends the bot to tan hides if one is found. If neither bowstring spinning
     * nor hide tanning is available, the bot falls back to harvesting flax.
     *
     * @param bot The bot that will run the script.
     * @param level The bot's current crafting level.
     * @param zones The candidate zones available to the factory.
     *
     * @return A crafting script suitable for profit-oriented activity.
     */
    override fun getProfitScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        if (randBoolean() && level >= Textile.BOWSTRING.level) {
            zones += SubZone.FLAX_SPINNING_MAIN
            return SpinFlaxBotScript(bot, getDuration(bot))
        } else {
            for (item in bot.bank) {
                if (item != null) {
                    val hide = Hide.HIDE_TO_HIDE[item.id]
                    if (hide == null) {
                        continue
                    }
                    return TanHideBotScript(bot, getDuration(bot))
                }
            }
        }
        zones += SubZone.SOUTH_SEERS_VILLAGE_FLAX
        return HarvestBotScript(bot, Harvestable.FLAX, getDuration(bot), zones)
    }
}