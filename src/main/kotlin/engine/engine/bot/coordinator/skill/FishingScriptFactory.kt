package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.FishBotScript
import game.skill.fishing.Tool
import game.skill.fishing.Tool.*
import io.luna.game.model.mob.bot.Bot

/**
 * Creates Fishing bot scripts.
 *
 * The factory chooses the best fishing tool available for the bot's current Fishing level, then assigns a zone group
 * that supports that tool.
 *
 * Zone selection is partly personality-based:
 * - Dumb and non-dextrous bots are sent to less efficient or lower-level areas.
 * - Smarter or more dextrous bots are sent to better fishing locations.
 *
 * Profit fishing currently uses the same behavior as training fishing.
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
     * traits.
     */
    enum class FishingZone(
        val tools: Set<Tool>,
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
                if (personality.isDumb && !personality.isDextrous) {
                    mutableListOf(
                        SubZone.SOUTH_LUMBRIDGE_MINE,
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
         * Less capable bots use Lumbridge River, while better bots use Barbarian Village.
         */
        LURE_AND_BAIT(
            tools = setOf(FLY_FISHING_ROD, FISHING_ROD),
            zones = {
                if (personality.isDumb && !personality.isDextrous) {
                    mutableListOf(SubZone.LUMBRIDGE_RIVER)
                } else {
                    mutableListOf(SubZone.BARBARIAN_VILLAGE)
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
                if (personality.isDumb && !personality.isDextrous) {
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
            tools = setOf(BIG_NET, HARPOON),
            zones = {
                mutableListOf(SubZone.EAST_CATHERBY_FISHING)
            }
        ),

        /**
         * Piscatoris fishing zone.
         *
         * Used for higher-level fishing activities such as monkfish-style fishing routes.
         */
        SMALL_NET_AND_HARPOON(
            tools = setOf(BIG_NET, HARPOON),
            zones = {
                mutableListOf(SubZone.PISCATORIS_FISHING_COLONY_MAIN)
            }
        )
    }

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
        val tool = getBestActivity(bot, level, { it.level }, Tool.entries) ?: SMALL_NET

        return FishBotScript(
            bot,
            tool,
            getDuration(bot),
            getZones(bot, tool)
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
     * applied.
     */
    private fun getZones(bot: Bot, tool: Tool): MutableList<SubZone> {
        val zones = when (tool) {
            SMALL_NET -> listOf(FishingZone.SMALL_NET_AND_BAIT)

            FISHING_ROD -> listOf(
                FishingZone.SMALL_NET_AND_BAIT,
                FishingZone.LURE_AND_BAIT
            )

            FLY_FISHING_ROD -> listOf(FishingZone.LURE_AND_BAIT)

            BIG_NET,
            SHARK_HARPOON -> listOf(FishingZone.BIG_NET_AND_HARPOON)

            HARPOON -> listOf(
                FishingZone.SMALL_NET_AND_HARPOON,
                FishingZone.CAGE_AND_HARPOON
            )

            LOBSTER_POT -> listOf(FishingZone.CAGE_AND_HARPOON)

            MONKFISH_NET -> listOf(FishingZone.SMALL_NET_AND_HARPOON)

            KARAMBWAN_VESSEL -> listOf(
                FishingZone.SMALL_NET_AND_HARPOON
            ) // TODO Vessel fishing
        }

        return zones.random().zones(bot)
    }
}