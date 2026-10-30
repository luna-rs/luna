package api.bot.webwalk.plan

import api.bot.webwalk.walk.crossing.AltarCrossing
import api.bot.webwalk.walk.crossing.JarvaldCrossing
import api.bot.webwalk.walk.crossing.WaterbirthPartyCrossing
import game.content.waterbirth.Jarvald
import api.bot.webwalk.walk.crossing.EssenceMineCrossing
import api.bot.zone.SubZone
import game.skill.runecrafting.essenceMine.EssenceMine
import game.skill.runecrafting.essenceMine.EssenceMineWizard
import game.skill.runecrafting.enterAltar.Altar
import game.content.sailing.ShipSearch
import game.content.craftingGuild.CraftingGuild
import game.obj.webs.Web
import io.luna.game.model.mob.bot.Bot

/**
 * Conditions that a bot meets or not, as flags that requirements can ask for. The
 * data names a flag, and the rule that decides it stays in the game's code.
 *
 * There is a flag for each [ShipSearch] that searches for something, which a bot has if the search finds nothing on it,
 * such as [shipSearchFlag] of [ShipSearch.ENTRANA] for a bot without weapons or armour.
 *
 * Can also be used to check if spirit trees are unlocked, or if Zanaris is unlocked, and if a dramen or lunar staff is available in the inventory.
 *
 * @author Hydrozoa
 */
object BotConditions {

    /**
     * The start of the flags of [ShipSearch]es.
     */
    const val SHIP_SEARCH_PREFIX = "ship_search_"

    /**
     * Entry needs the current Crafting level and an equipped brown apron, not a carried or banked one.
     * A flag also preserves boosts and drains instead of using the snapshot's static skill levels.
     */
    const val CRAFTING_GUILD_ENTRY = "crafting_guild_entry"

    /**
     * The bot carries something that cuts a web: a weapon with a slash attack, wielded or not, or a knife.
     */
    const val SLASH_WEB = "slash_web"

    /**
     * The conditions, keyed by the name of their flag.
     */
    private val conditions: Map<String, (Bot) -> Boolean> =
        ShipSearch.entries.mapNotNull { search ->
            shipSearchFlag(search)?.let { flag -> flag to { bot: Bot -> !search.turnsAway(bot) } }
        }.toMap() + mapOf(WaterbirthPartyCrossing.READY to { bot: Bot -> WaterbirthPartyCrossing.ready(bot) },
                         JarvaldCrossing.PAYMENT to { bot: Bot ->
            Jarvald.fare(bot) == 0 || bot.inventory.computeAmountForId(995) >= Jarvald.FARE
        }, CRAFTING_GUILD_ENTRY to { bot: Bot -> CraftingGuild.canEnter(bot) },
                      SLASH_WEB to { bot: Bot -> Web.blade(bot) != null }) +
                Altar.entries.associate { altar -> AltarCrossing.flag(altar) to { bot: Bot -> AltarCrossing.canEnter(bot, altar) } } +
                EssenceMineWizard.entries.associate { wizard -> EssenceMineCrossing.exitFlag(wizard) to { bot: Bot ->
                    SubZone.ESSENCE_MINE.area.contains(bot) && EssenceMine.returnWizard(bot) == wizard
                } }

    /**
     * The names of every condition flag.
     */
    val flags: Set<String>
        get() = conditions.keys

    /**
     * The flag that a bot has when [search] finds nothing on it, or `null` for [ShipSearch.NONE], which needs no flag.
     */
    fun shipSearchFlag(search: ShipSearch): String? =
        if (search == ShipSearch.NONE) null else SHIP_SEARCH_PREFIX + search.name.lowercase()

    /**
     * Finds the flags of the conditions that a bot meets. Must be called on the game thread, because it reads the bot as it
     * is.
     *
     * @param bot The bot.
     * @return The flags.
     */
    fun met(bot: Bot): Set<String> = conditions.filterValues { it(bot) }.keys
}
