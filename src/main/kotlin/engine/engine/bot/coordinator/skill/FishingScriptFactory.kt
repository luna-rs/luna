package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.FishBotScript
import game.skill.fishing.catchFish.Tool
import game.skill.fishing.catchFish.Tool.*
import io.luna.game.model.mob.bot.Bot

/**
 * Creates Fishing bot scripts.
 *
 * The factory chooses the best supported fishing tool available for the bot's current Fishing level, then assigns
 * a zone group that supports that tool.
 *
 * Zone selection is partly personality-based:
 * - Dumb and non-dextrous bots are sent to less efficient or lower-level areas.
 * - Smarter or more dextrous bots are sent to better fishing locations.
 *
 * Fly and pike fishing also use the native Stronghold, Seers, Ardougne and Shilo spots. Fishing Guild
 * cage/harpoon and big-net/shark spots join selection only at current Fishing 68. Training and profit
 * share the same location rules; the existing script owns tools, bait, moving spots and banking.
 *
 * @author lare96
 */
object FishingScriptFactory : SkillingScriptFactory(SKILL_FISHING) {

    /**
     * A group of fishing zones that support one or more fishing tools.
     *
     * Each group defines:
     * - The tools that can be used there.
     * - A zone provider that may change based on bot personality.
     *
     * The zone provider is evaluated against the bot, allowing each bot to pick locations that match its personality
     * traits. Unrestricted areas have no additional entry-level gate.
     *
     * @property requiredLevel Current Fishing level needed to enter this area, or zero for unrestricted areas.
     * @author lare96
     */
    enum class FishingZone(
        val tools: Set<Tool>,
        val requiredLevel: Int = 0,
        val zones: Bot.() -> MutableList<SubZone>
    ) {

        /**
         * Basic net and bait fishing locations.
         *
         * Less capable bots may use lower-tier or less efficient locations, while better bots prefer Draynor or
         * Catherby.
         */
        SMALL_NET_AND_BAIT(
            tools = setOf(SMALL_NET, FISHING_ROD),
            zones = {
                if (personality.isDumb || !personality.isDextrous) {
                    mutableListOf(
                        SubZone.MUSA_POINT_FISHING,
                        SubZone.AL_KHARID_BANK,
                        SubZone.DRAYNOR_MAIN,
                        SubZone.EAST_CATHERBY_FISHING
                    )
                } else {
                    mutableListOf(
                        SubZone.DRAYNOR_MAIN,
                        SubZone.EAST_CATHERBY_FISHING
                    )
                }
            }
        ),

        /**
         * Lure and bait fishing locations.
         *
         * Casual bots also use Seers and the Stronghold. More capable bots add Shilo and the Ardougne river
         * to the existing Barbarian Village choice. Each candidate supports both lure and bait options.
         */
        LURE_AND_BAIT(
            tools = setOf(FLY_FISHING_ROD, PIKE_FISHING_ROD),
            zones = {
                if (personality.isDumb || !personality.isDextrous) {
                    mutableListOf(SubZone.LUMBRIDGE_RIVER, SubZone.SEERS_VILLAGE_FISHING,
                                  SubZone.GNOME_STRONGHOLD_FISHING)
                } else {
                    mutableListOf(SubZone.BARBARIAN_VILLAGE, SubZone.SHILO_VILLAGE,
                                  SubZone.ARDOUGNE_RIVER_FISHING, SubZone.GNOME_STRONGHOLD_FISHING,
                                  SubZone.SEERS_VILLAGE_FISHING)
                }
            }
        ),

        /**
         * Lobster cage and harpoon fishing locations.
         *
         * Less capable bots use Musa Point, while better bots use Catherby.
         */
        CAGE_AND_HARPOON(
            tools = setOf(LOBSTER_POT, HARPOON),
            zones = {
                if (personality.isDumb || !personality.isDextrous || personality.isAntiSocial) {
                    mutableListOf(SubZone.MUSA_POINT_FISHING)
                } else {
                    mutableListOf(SubZone.EAST_CATHERBY_FISHING)
                }
            }
        ),

        /**
         * Big net and harpoon fishing at Catherby.
         */
        BIG_NET_AND_HARPOON(
            tools = setOf(BIG_NET, SHARK_HARPOON),
            zones = {
                mutableListOf(SubZone.EAST_CATHERBY_FISHING)
            }
        ),

        /**
         * Piscatoris fishing zone.
         *
         * Supports monkfish net fishing and tuna/swordfish harpooning.
         */
        SMALL_NET_AND_HARPOON(
            tools = setOf(MONKFISH_NET, HARPOON),
            zones = {
                mutableListOf(SubZone.PISCATORIS_FISHING_COLONY_MAIN)
            }
        ),

        /**
         * Native Guild spots for lobster, tuna/swordfish, big-net fish and sharks.
         * Current Fishing level includes boosts and must meet the source's entry requirement.
         */
        FISHING_GUILD(
            tools = setOf(LOBSTER_POT, HARPOON, BIG_NET, SHARK_HARPOON),
            requiredLevel = FishBotScript.FISHING_GUILD_LEVEL,
            zones = { mutableListOf(SubZone.FISHING_GUILD) }
        ),

        /**
         * Karambwan vessel fishing on northeast Karamja.
         */
        KARAMBWAN(
            tools = setOf(KARAMBWAN_VESSEL),
            zones = {
                mutableListOf(SubZone.KARAMJA_KARAMBWAN_FISHING)
            }
        )
    }

    /**
     * Tools with supported bot routes. Keep selection and routing based on the same declarations.
     */
    private val BOT_TOOLS = Tool.entries.filter { tool -> FishingZone.entries.any { tool in it.tools } }

    /**
     * Builds a Fishing training script.
     *
     * The selected tool is the best [Tool] the bot can use at its current Fishing level. If no valid tool can be
     * found, the script falls back to [SMALL_NET].
     *
     * The selected tool is then used to choose an appropriate fishing zone.
     */
    override fun getTrainingScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        val activities = BOT_TOOLS.map { tool ->
            BotSkillingActivity(tokens = setOf(tool), zones = getZones(bot, tool).toSet())
        }
        val activity = getBestSkillingActivity(bot, level, { it.level }, activities)
            ?: BotSkillingActivity(tokens = setOf(SMALL_NET), zones = getZones(bot, SMALL_NET).toSet())

        return FishBotScript(
            bot,
            activity.tokens.single(),
            getDuration(bot),
            activity.zones.toMutableList()
        )
    }

    /**
     * Builds a Fishing profit script.
     */
    override fun getProfitScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        return getTrainingScript(bot, level, zones)
    }

    /**
     * Selects a valid zone list for the given fishing [tool].
     *
     * Some tools can be used in multiple fishing zone groups. When multiple groups are available, one group is chosen
     * randomly to add variety between bots.
     *
     * The chosen zone group's provider is then evaluated against the bot so personality-based zone selection can be
     * applied. Guild access uses current Fishing so a drained bot cannot be selected for a restricted dock.
     */
    private fun getZones(bot: Bot, tool: Tool): MutableList<SubZone> {
        val zones = FishingZone.entries.filter { tool in it.tools && bot.fishing.level >= it.requiredLevel }

        return zones.random().zones(bot)
    }
}
