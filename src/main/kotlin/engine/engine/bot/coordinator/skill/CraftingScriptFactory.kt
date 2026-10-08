package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.bot.scripts.HarvestBotScript
import game.bot.scripts.HarvestBotScript.Companion.Harvestable
import game.bot.scripts.skills.CollectHidesBotScript
import game.bot.scripts.skills.CraftArmorBotScript
import game.bot.scripts.skills.SpinFlaxBotScript
import game.bot.scripts.skills.TanHideBotScript
import game.skill.crafting.armorCrafting.HideArmor
import game.skill.crafting.hideTanning.Hide
import game.skill.crafting.textileCrafting.Textile
import io.luna.game.model.mob.bot.Bot
import io.luna.util.RandomUtils.roll

/**
 * Creates crafting scripts for bots.
 *
 * Crafting currently supports leather armour crafting for training, fed by collecting and tanning cowhides, and flax,
 * bowstring, hide-collecting or hide-tanning behaviour for profit. Additional crafting branches can be added here as
 * their bot scripts become available.
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

    /**
     * Creates a crafting training script for the bot's current level.
     *
     * Bots collect cowhides until they own [CollectHidesBotScript.hideTarget], then tan every hide they own, then craft
     * the best [HideArmor] they have the materials for until the leather runs out.
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

    /**
     * Creates a crafting profit script for the bot's current level and bank contents.
     *
     * Bots that can make bowstrings may spin flax when the random branch is selected. Otherwise, bots that own
     * [CollectHidesBotScript.hideTarget] hides tan them. Everyone else either collects cowhides or harvests flax.
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

    fun getBasicScript(bot: Bot): CraftArmorBotScript {
        // todo chance of pottery crafting
        return CraftArmorBotScript(bot, HideArmor.LEATHER_GLOVES, getDuration(bot))
    }
}