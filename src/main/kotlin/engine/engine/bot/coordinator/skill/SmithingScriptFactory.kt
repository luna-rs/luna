package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.SmeltOreBotScript
import game.bot.scripts.skills.SmithBarBotScript
import game.skill.smithing.BarType
import game.skill.smithing.smithBar.SmithingTable
import io.luna.game.model.mob.bot.Bot

/**
 * Creates smithing scripts for bots.
 *
 * Smithing can currently be trained in two ways:
 *
 * - Smithing finished items from bars.
 * - Smelting ore into bars.
 *
 * Training prefers smithing level-appropriate equipment when possible, then falls back to smelting. Profit mode prefers
 * higher-value smithable items, then falls back to smelting any bar type the bot has ore for.
 *
 * @author lare96
 */
object SmithingScriptFactory : SkillingScriptFactory(SKILL_SMITHING) {

    /**
     * Smithable item types prioritized for profit-oriented smithing.
     *
     * These are generally the larger, more useful, or more valuable item categories available from smithing tables. The
     * flattened result contains every metal-tier variant of each selected table entry.
     */
    val PROFIT = setOf(
        SmithingTable.SCIMITAR,
        SmithingTable.TWO_HANDED_SWORD,
        SmithingTable.FULL_HELM,
        SmithingTable.PLATESKIRT,
        SmithingTable.PLATELEGS,
        SmithingTable.PLATEBODY,
        SmithingTable.ARROWTIPS,
        SmithingTable.KITESHIELD,
        SmithingTable.AXE
    ).flatMap { it.items }

    /**
     * Smithable item types used for general smithing training.
     *
     * This list covers a broad progression of smithable equipment and supplies. The flattened result contains every
     * metal-tier variant of each selected table entry, allowing the activity selector to choose the best item available
     * for the bot's level.
     */
    val TRAINING = setOf(
        SmithingTable.DAGGER,
        SmithingTable.AXE,
        SmithingTable.MACE,
        SmithingTable.MED_HELM,
        SmithingTable.MACE,
        SmithingTable.SWORD,
        SmithingTable.DART_TIP,
        SmithingTable.SCIMITAR,
        SmithingTable.ARROWTIPS,
        SmithingTable.LONGSWORD,
        SmithingTable.FULL_HELM,
        SmithingTable.THROWING_KNIVES,
        SmithingTable.SQ_SHIELD,
        SmithingTable.WARHAMMER,
        SmithingTable.CHAINBODY,
        SmithingTable.BATTLEAXE,
        SmithingTable.KITESHIELD,
        SmithingTable.CLAWS,
        SmithingTable.TWO_HANDED_SWORD,
        SmithingTable.PLATELEGS,
        SmithingTable.PLATESKIRT,
        SmithingTable.PLATEBODY
    ).flatMap { it.items }

    /**
     * Creates a smithing training script for the bot's current smithing level.
     *
     * The factory first attempts to select a smithable item from [TRAINING]. If a suitable item is found, the bot will
     * smith that item from bars.
     *
     * If no smithable item can be selected, the factory falls back to smelting the best available [BarType]. Since bronze
     * smelting starts at level 1, [BarType.BRONZE] is used as a final safety fallback.
     *
     * @param bot The bot that will run the script.
     * @param level The bot's current smithing level.
     * @param zones Candidate zones available to the script.
     * @return A smithing script suitable for training.
     */
    override fun getTrainingScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        val smithingItem = getBestActivity(bot, level, { it.level }, TRAINING)
        if (smithingItem != null) {
            return SmithBarBotScript(bot, mutableListOf(smithingItem), getDuration(bot))
        }

        // Smelting starts at level 1, so it is always the final training fallback.
        val smeltBar = getBestActivity(bot, level, { it.level }, BarType.VALUES) ?: BarType.BRONZE

        // TODO Add more zones. Dextrous bots should prefer better smithing/smelting routes.
        zones += SubZone.AL_KHARID_BANK
        zones += SubZone.FALADOR_WEST_BANK
        return SmeltOreBotScript(bot, smeltBar, getDuration(bot), zones)
    }

    /**
     * Creates a profit-oriented smithing script for the bot's current smithing level.
     *
     * Profit mode first attempts to smith profitable finished items from [PROFIT]. If no profitable smithing item is
     * available for the bot's level, the factory falls back to smelting.
     *
     * When [SmeltOreBotScript] receives a `null` bar type, it chooses a suitable bar by scanning the bot's available ore
     * supplies.
     *
     * @param bot The bot that will run the script.
     * @param level The bot's current smithing level.
     * @param zones Candidate zones available to the script.
     * @return A smithing script suitable for profit-oriented activity.
     */
    override fun getProfitScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        // Profit smithing prefers finished weapons, armor, and useful supplies when possible.
        val smithingScript = getSmithingScript(bot, level)
        if (smithingScript != null) {
            return smithingScript
        }

        // TODO Add more zones. Dextrous bots should prefer better smithing/smelting routes.
        zones += SubZone.AL_KHARID_BANK
        zones += SubZone.FALADOR_WEST_BANK
        return SmeltOreBotScript(bot, null, getDuration(bot), zones)
    }

    /**
     * Creates a [SmithBarBotScript] for profitable smithing items available at the bot's level.
     *
     * All eligible items from [PROFIT] are passed to the script. The script can then choose from that list based on its
     * own inventory, banking, and item-selection behavior.
     *
     * @param bot The bot that will run the script.
     * @param level The bot's current smithing level.
     * @return A smithing script when at least one profitable item is available, otherwise `null`.
     */
    fun getSmithingScript(bot: Bot, level: Int): SmithBarBotScript? {
        val smithingItems = getActivities(level, { it.level }, PROFIT)
        if (smithingItems.isNotEmpty()) {
            return SmithBarBotScript(bot, smithingItems.toMutableList(), getDuration(bot))
        }
        return null
    }
    //todo docs
    fun getSmeltingScript(bot: Bot, level: Int): SmeltOreBotScript{
        // Smelting starts at level 1, so it is always the final training fallback.
        val smeltBar = getBestActivity(bot, level, { it.level }, BarType.VALUES) ?: BarType.BRONZE
      val zones = mutableListOf<SubZone>()
        // TODO Add more zones. Dextrous bots should prefer better smithing/smelting routes.
   // todo boilerplate
        zones += SubZone.AL_KHARID_BANK
        zones += SubZone.FALADOR_WEST_BANK
        return SmeltOreBotScript(bot, smeltBar, getDuration(bot), zones)
    }
}