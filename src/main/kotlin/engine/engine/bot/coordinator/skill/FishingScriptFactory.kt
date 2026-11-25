package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.script.ProfitGatheringGoal
import api.bot.script.VoidBotScript
import api.bot.script.recentEscape
import game.player.item.consume.food.Food as CookedFood
import game.skill.cooking.cookFood.Food as RawFood
import api.bot.action.trading.ProfitGatheringDemand
import game.skill.Skills
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.FishBotScript
import game.skill.fishing.catchFish.Tool
import game.skill.fishing.catchFish.Fish
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.bot.scripts.GiantMoleTrip
import game.obj.entrances.SwampCaveEntrance.swampCavesRoped
import game.skill.fishing.catchFish.Tool.*
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration.Companion.minutes

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
                        SubZone.EAST_CATHERBY_FISHING,
                        SubZone.RIMMINGTON_COAST_FISHING,
                        SubZone.ENTRANA_SEA_FISHING,
                        SubZone.RELLEKKA_SOUTH_FISHING
                    )
                } else {
                    mutableListOf(
                        SubZone.DRAYNOR_MAIN,
                        SubZone.EAST_CATHERBY_FISHING,
                        SubZone.RELLEKKA_SOUTH_FISHING
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
                                  SubZone.GNOME_STRONGHOLD_FISHING,
                                  SubZone.ENTRANA_RIVER_SOUTH_FISHING, SubZone.ENTRANA_RIVER_NORTH_FISHING)
                } else {
                    mutableListOf(SubZone.BARBARIAN_VILLAGE, SubZone.SHILO_VILLAGE,
                                  SubZone.ARDOUGNE_RIVER_FISHING, SubZone.GNOME_STRONGHOLD_FISHING,
                                  SubZone.SEERS_VILLAGE_FISHING,
                                  SubZone.ENTRANA_RIVER_SOUTH_FISHING, SubZone.ENTRANA_RIVER_NORTH_FISHING)
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
                    mutableListOf(SubZone.MUSA_POINT_FISHING, SubZone.RELLEKKA_SOUTH_FISHING)
                } else {
                    mutableListOf(SubZone.EAST_CATHERBY_FISHING, SubZone.RELLEKKA_SOUTH_FISHING)
                }
            }
        ),

        /**
         * Big net and harpoon fishing at Catherby.
         */
        BIG_NET_AND_HARPOON(
            tools = setOf(BIG_NET, SHARK_HARPOON),
            zones = {
                mutableListOf(SubZone.EAST_CATHERBY_FISHING, SubZone.RELLEKKA_NORTH_FISHING)
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
        ),

        /**
         * Native pond bait, swamp eels, lit-cave eels and oily-rod lava eels.
         */
        KARAMBWANJI(setOf(KARAMBWANJI_NET), zones = { mutableListOf(SubZone.KARAMJA_KARAMBWANJI_FISHING) }),
        SLIMY_EEL(setOf(SLIMY_EEL_FISHING_ROD), zones = { mutableListOf(SubZone.MORT_MYRE_SLIMY_EEL_FISHING) }),
        CAVE_EEL(setOf(CAVE_EEL_FISHING_ROD), zones = { mutableListOf(SubZone.LUMBRIDGE_WEST_CAVE_EEL_FISHING,
            SubZone.LUMBRIDGE_EAST_CAVE_EEL_FISHING) }),
        LAVA_EEL(setOf(LAVA_EEL_FISHING_ROD), zones = { mutableListOf(SubZone.TAVERLY_LAVA_EEL_FISHING) })
    }

    /**
     * Tools with supported bot routes. Keep selection and routing based on the same declarations.
     */
    private val BOT_TOOLS = Tool.entries.filter { tool -> FishingZone.entries.any { tool in it.tools } }

    internal fun forFoodReserve(bot: Bot, quantity: Int): FishBotScript? {
        if (quantity <= 0) return null
        val fishing = minOf(bot.fishing.level, bot.fishing.staticLevel)
        val cooking = minOf(bot.cooking.level, bot.cooking.staticLevel)
        val edible = RawFood.entries.filter { it.lvl <= cooking }
            .mapNotNull { raw -> CookedFood.ID_TO_FOOD[raw.cooked]?.heal?.takeIf { it > 0 }?.let { raw.raw to it } }
            .toMap()
        val candidates = BOT_TOOLS.asSequence().filter { tool ->
            tool.level <= fishing && bot.itemTracker.contains(
                if (tool == KARAMBWAN_VESSEL) Tool.EMPTY_KARAMBWAN_VESSEL else tool.id) &&
                (if (tool == KARAMBWAN_VESSEL) Fish.KARAMBWANJI.id else tool.bait)
                    ?.let { bot.itemTracker.contains(it) } != false &&
                (tool != CAVE_EEL_FISHING_ROD || GiantMoleTrip.lightOwned(bot) &&
                    (bot.swampCavesRoped || bot.itemTracker.contains(954)))
        }.mapNotNull { tool ->
            val heal = tool.fish.filter { it.level <= fishing }.mapNotNull { edible[it.id] }.maxOrNull()
                ?: return@mapNotNull null
            val zones = FishingZone.entries.filter { tool in it.tools && fishing >= it.requiredLevel }
                .flatMap { it.zones(bot) }.distinct().filter { it.entryBlocker(bot) == null &&
                    bot.recentEscape?.avoids(it) != true }
            zones.takeIf { it.isNotEmpty() }?.let { Triple(tool, heal, it) }
        }
        val (tool, _, zones) = candidates.maxByOrNull { it.second } ?: return null
        return FishBotScript(bot, tool, 20.minutes, zones.toMutableList(),
            reserveCatchTarget = quantity)
    }

    /**
     * Builds a Fishing training script.
     *
     * Tools are ranked by their Fishing requirements, with harpoon fishing ranked at level 50 once it can
     * catch swordfish. If no valid tool can be found, the script falls back to [SMALL_NET].
     *
     * The selected tool is then used to choose an appropriate fishing zone.
     */
    override fun getTrainingScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>
    ): BotScript {
        val fishing = minOf(level, bot.fishing.level)
        val activities = BOT_TOOLS.filter { tool ->
            when (tool) {
                LAVA_EEL_FISHING_ROD -> bot.itemTracker.contains(tool.id)
                CAVE_EEL_FISHING_ROD -> GiantMoleTrip.lightOwned(bot) &&
                    (bot.swampCavesRoped || bot.itemTracker.contains(954))
                else -> true
            }
        }.map { tool ->
            BotSkillingActivity(tokens = setOf(tool), zones = getZones(bot, tool).toSet())
        }
        val activity = getBestSkillingActivity(bot, fishing, { tool ->
            if (tool == HARPOON && fishing >= Fish.SWORDFISH.level) Fish.SWORDFISH.level else tool.level
        }, activities)
            ?: BotSkillingActivity(tokens = setOf(SMALL_NET), zones = getZones(bot, SMALL_NET).toSet())

        return FishBotScript(
            bot,
            activity.tokens.single(),
            getDuration(bot, skillId),
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
    ): BotScript = profitCandidate(bot, level)?.start(zones) ?: VoidBotScript(bot)

    internal fun profitCandidate(bot: Bot, level: Int, marketOnly: Boolean = false,
                                 items: Set<Int>? = null): GatheringOpportunity? {
        val choices = linkedMapOf<Tool, ProfitGatheringDemand.Quote>()
        val destinations = HashMap<Tool, List<SubZone>>()
        for (tool in BOT_TOOLS) {
            if (items != null && tool.fish.none { it.id in items }) continue
            val baitId = if (tool == KARAMBWAN_VESSEL) Fish.KARAMBWANJI.id else tool.bait
            val ownsTool = ProfitGatheringGoal.physical(bot, tool.id) > 0L ||
                tool == KARAMBWAN_VESSEL && ProfitGatheringGoal.physical(bot, Tool.EMPTY_KARAMBWAN_VESSEL) > 0L
            if (tool == BIG_NET || tool.level > minOf(level, bot.fishing.level) || !ownsTool ||
                baitId?.let { ProfitGatheringGoal.physical(bot, it) < 1L } == true ||
                tool == LAVA_EEL_FISHING_ROD && !bot.itemTracker.contains(tool.id) ||
                tool == CAVE_EEL_FISHING_ROD && (!GiantMoleTrip.lightOwned(bot) ||
                    !bot.swampCavesRoped && !bot.itemTracker.contains(954))) continue
            val locations = getZones(bot, tool).filter { it.entryBlocker(bot) == null &&
                bot.recentEscape?.avoids(it) != true }
            if (locations.isEmpty()) continue
            val probabilities = linkedMapOf<Int, Double>()
            var remaining = 1.0
            for (fish in tool.fish.filter { it.level <= bot.fishing.level }) {
                val native = Skills.successRate(fish.chance.first, fish.chance.second, bot.fishing.level)
                    .coerceIn(0.0, 1.0)
                probabilities[fish.id] = native * remaining
                remaining *= 1.0 - native
            }
            val bait = baitId?.let { ProfitGatheringDemand.inputPrice(bot, it) } ?: 0.0
            val baitPerAttempt = bait * if (tool == KARAMBWAN_VESSEL) 1.0 else probabilities.values.sum()
            val outputs = probabilities.mapNotNull { (id, probability) ->
                if (probability <= 0.0 || items != null && id !in items) null
                else ProfitGatheringDemand.quote(bot, id,
                    tool.delay.toDouble() / probability, baitPerAttempt / probability, marketOnly = marketOnly)
            }
            val best = outputs.maxByOrNull { it.score } ?: continue
            choices[tool] = best.copy(score = outputs.sumOf { it.score })
            destinations[tool] = locations
        }
        val tool = ProfitGatheringDemand.select(bot, choices) ?: return null
        return GatheringOpportunity(choices.getValue(tool), destinations.getValue(tool), bot, getDuration(bot, skillId),
            tool.fish.mapTo(linkedSetOf()) { fish -> fish.id }) { duration, zones ->
            FishBotScript(bot, tool, duration, zones)
        }
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
