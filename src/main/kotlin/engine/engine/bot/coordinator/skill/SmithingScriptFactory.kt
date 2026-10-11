package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.bot.scripts.skills.MakeCannonballBotScript
import game.bot.scripts.skills.SmeltOreBotScript
import game.bot.scripts.skills.SmithBarBotScript
import game.skill.smithing.BarType
import game.skill.smithing.smithBar.SmithingTable
import io.luna.game.model.mob.bot.Bot

/**
 * Creates smithing scripts for bots.
 *
 * Eligible bots with owned steel bars and an ammo mould first produce cannonballs through a furnace.
 * Other training sessions prefer level-appropriate equipment, then smelting. Other profit sessions prefer
 * higher-value smithable items, then smelting. Cannonball selection requires permanent level 35 and owned
 * supplies; the script rechecks current level and inventory requirements before each player interaction.
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

    override fun getTrainingScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        getCannonballScript(bot, level)?.let { return it }
        val smithingItem = getBestActivity(bot, level, { it.level }, TRAINING.filter {
            bot.itemTracker.count(it.barType.id) >= SmithingTable.ID_TO_TABLE[it.item.id]!!.bars
        })
        if (smithingItem != null) {
            return SmithBarBotScript(bot, mutableListOf(smithingItem), getDuration(bot))
        }

        // Prefer owned ore combinations; the script gathers supplies when none are available.
        val smeltBar = getBestActivity(bot, level, { it.level }, BarType.VALUES.filter { bar ->
            bar.oreList.all { bot.itemTracker.count(it.id) >= it.amount }
        })

        // TODO Add more zones. Dextrous bots should prefer better smithing/smelting routes.
        zones += SubZone.AL_KHARID_BANK
        zones += SubZone.FALADOR_WEST_BANK
        return SmeltOreBotScript(bot, smeltBar, getDuration(bot), zones)
    }

    override fun getProfitScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        getCannonballScript(bot, level)?.let { return it }
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

    /** Selects cannonball production only when the supplied level, permanent level, and owned supplies qualify. */
    internal fun getCannonballScript(bot: Bot, level: Int): MakeCannonballBotScript? {
        if (level < game.skill.smithing.makeCannonball.MakeCannonballAction.LEVEL) return null
        return MakeCannonballBotScript(bot, getDuration(bot)).takeIf { it.isEligible() }
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
        val smithingItems = getActivities(level, { it.level }, PROFIT).filter {
            bot.itemTracker.count(it.barType.id) >= SmithingTable.ID_TO_TABLE[it.item.id]!!.bars
        }
        if (smithingItems.isNotEmpty()) {
            return SmithBarBotScript(bot, smithingItems.toMutableList(), getDuration(bot))
        }
        return null
    }
    //todo docs
    fun getSmeltingScript(bot: Bot, level: Int): SmeltOreBotScript{
        // Prefer owned ore combinations; the script gathers supplies when none are available.
        val smeltBar = getBestActivity(bot, level, { it.level }, BarType.VALUES.filter { bar ->
            bar.oreList.all { bot.itemTracker.count(it.id) >= it.amount }
        })
      val zones = mutableListOf<SubZone>()
        // TODO Add more zones. Dextrous bots should prefer better smithing/smelting routes.
   // todo boilerplate
        zones += SubZone.AL_KHARID_BANK
        zones += SubZone.FALADOR_WEST_BANK
        return SmeltOreBotScript(bot, smeltBar, getDuration(bot), zones)
    }
}