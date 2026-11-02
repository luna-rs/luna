package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.script.recentEscape
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.MineBotScript
import game.bot.scripts.skills.MiningBehavior
import game.bot.scripts.skills.MiningBehavior.Style
import game.bot.scripts.skills.recentlyFull
import game.content.miningGuild.MiningGuild
import game.content.craftingGuild.CraftingGuild
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.mining.Ore
import game.skill.mining.Ore.*
import io.luna.game.model.mob.bot.Bot

/**
 * Mining methods keep each mine's rocks together with its access and personality preferences.
 * Training retains iron-focused efficient personalities and broader casual routes. Profit progresses
 * from essence/basic ores to gold, coal, gems, and high-tier metals. Shilo gem rocks are available to
 * casual trainers and profit miners at level 40. Crafting Guild rocks also require current Crafting 40 and
 * an owned brown apron. The Mining Guild retains its existing preference once accessible.
 * Power-mining training selects iron outside the guild; collecting sessions continue to bank their ores.
 * The selected method stays first, with other eligible mines that contain a preferred ore retained for reactive
 * retries. A bot discovers competition after arrival; no occupancy or live-rock estimate changes its first choice.
 *
 * @author lare96
 */
object MiningScriptFactory : SkillingScriptFactory(SKILL_MINING) {
    /**
     * Supported rock types from the subzone definitions; essence is a separate mining method.
     */
    internal val rocks = linkedMapOf(
        SubZone.VARROCK_SW_MINE to setOf(CLAY, TIN, IRON, SILVER),
        SubZone.VARROCK_SE_MINE to setOf(COPPER, TIN, IRON),
        SubZone.FALADOR_WEST_MINE to setOf(COPPER, TIN, IRON, COAL),
        SubZone.RIMMINGTON_MINE to setOf(CLAY, COPPER, TIN, IRON, GOLD),
        SubZone.PORT_KHAZARD_SOUTH_WEST_MINE to setOf(CLAY, COPPER, TIN, IRON, COAL, MITHRIL),
        SubZone.PORT_KHAZARD_NORTH_EAST_MINE to setOf(COPPER, TIN, MITHRIL),
        SubZone.WEST_ARDOUGNE_MINE to setOf(COPPER, TIN),
        SubZone.BARBARIAN_VILLAGE_MINE to setOf(TIN, COAL),
        SubZone.PISCATORIS_MINE to setOf(CLAY, COPPER, TIN, IRON),
        SubZone.KELDAGRIM_ENTRANCE_SOUTH_MINE to setOf(COPPER, COAL),
        SubZone.COAL_TRUCKS_MINE to setOf(COAL),
        SubZone.KARAMJA_NORTH_SHILO_MINE to setOf(MITHRIL, ADAMANT),
        SubZone.VARROCK_LIMESTONE_QUARRY to setOf(LIMESTONE),
        SubZone.DESERT_QUARRY to setOf(SANDSTONE, GRANITE),
        SubZone.DWARVEN_MINE to setOf(CLAY, COPPER, TIN, IRON, COAL, GOLD, MITHRIL, ADAMANT),
        SubZone.CRAFTING_GUILD_MINE to setOf(CLAY, SILVER, GOLD),
        SubZone.MINING_GUILD to setOf(COAL, MITHRIL),
        SubZone.LEGENDS_GUILD_MINE to setOf(IRON, COAL),
        SubZone.SOUTH_EAST_ARDOUGNE_MINE to setOf(IRON, COAL),
        SubZone.AL_KHARID_MINE to setOf(COPPER, TIN, IRON, SILVER, COAL, GOLD, MITHRIL, ADAMANT),
        SubZone.SOUTH_LUMBRIDGE_MINE to setOf(COAL, MITHRIL, ADAMANT),
        SubZone.EDGEVILLE_DUNGEON_MINE to setOf(COPPER, TIN, IRON, SILVER, COAL, MITHRIL, ADAMANT),
        SubZone.RELLEKKA_MINE to setOf(CLAY, SILVER, COAL),
        SubZone.ESSENCE_MINE to setOf(RUNE_ESSENCE),
        SubZone.SHILO_VILLAGE to setOf(GEM_ROCK),
        SubZone.DEEP_WILDERNESS_RUNITE_ROCKS to setOf(RUNE)
    )

    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        createScript(bot, level, zones, true)

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        createScript(bot, level, zones, false)

    /**
     * Selects a complete mining activity with the existing personality rule.
     */
    private fun createScript(bot: Bot, level: Int, zones: MutableList<SubZone>, training: Boolean): MineBotScript {
        val powerMining = training && level >= IRON.level && MiningBehavior.styleOf(bot.personality) == Style.POWER_MINER
        // A mine the bot just left for lack of free rocks is skipped while another option remains.
        val options = activities(bot, level, training).let { all ->
            all.filterNot { method -> method.zones.all { bot.recentlyFull(it) } }.ifEmpty { all }
        }
        val guild = options.firstOrNull { SubZone.MINING_GUILD in it.zones && prefersGuild(bot, training) }
        val powerMethod = if (powerMining) options
            .filter { method -> method.zones.none { bot.recentEscape?.avoids(it) == true } }.ifEmpty { options }
            .sortedBy { method -> method.zones.minOf { bot.position.computeLongestDistance(it.inside) } }
            .take(3).randomOrNull() else null
        val activity = powerMethod ?: guild ?: getBestSkillingActivity(bot, level, { it.level }, options)
            ?: error("No mining activity is available at level $level.")
        val preferred = when {
            training && (bot.personality.isDextrous || bot.personality.isIntelligent) ->
                if (IRON in activity.tokens) setOf(IRON) else setOf(COAL).intersect(activity.tokens)
            else -> activity.tokens.filterTo(linkedSetOf()) { ore -> ore.level == activity.tokens.maxOf { it.level } }
        }.ifEmpty { activity.tokens }
        val alternatives = compatibleZones(activity, preferred, options) - activity.zones
        zones.clear()
        zones.addAll(activity.zones)
        zones.addAll(if (bot.personality.isDextrous)
            alternatives.sortedBy { bot.position.computeLongestDistance(it.inside) }
        else alternatives.shuffled())
        return MineBotScript(bot, activity.tokens, getDuration(bot), zones, preferred, powerMining)
    }

    /**
     * Retains eligible retry destinations without changing the selected ore set or its priority. An alternative
     * needs at least one preferred ore; rocks of other types cannot become targets through a location change.
     * Access, training-mode restrictions and current skill gates have already filtered [options].
     */
    internal fun compatibleZones(selected: BotSkillingActivity<Ore>, preferred: Set<Ore>,
                                 options: List<BotSkillingActivity<Ore>>): Set<SubZone> =
        linkedSetOf<SubZone>().apply {
            addAll(selected.zones)
            for (option in options) {
                if (option.tokens.any { it in preferred && it in selected.tokens }) addAll(option.zones)
            }
        }

    /**
     * Keeps ore and access decisions in the mining factory rather than adding a generic policy system.
     */
    internal fun activities(bot: Bot, level: Int, training: Boolean): List<BotSkillingActivity<Ore>> {
        val powerTraining = training && level >= IRON.level && MiningBehavior.styleOf(bot.personality) == Style.POWER_MINER
        val efficient = bot.personality.isDextrous || bot.personality.isIntelligent
        val desired = (if (training) trainingOres(bot, level) else profitOres(bot, level)).let {
            if (level >= GEM_ROCK.level && (!training || !efficient)) it + GEM_ROCK else it
        }.let {
            // Coal cushions scarcity for metal collectors; essence and gem-only methods remain separate.
            if (!powerTraining && training && efficient && level >= MiningGuild.REQUIRED_LEVEL ||
                level >= COAL.level && it.any { ore -> ore in setOf(SILVER, GOLD, MITHRIL, ADAMANT, RUNE) }) it + COAL
            else it
        }
        val activities = ArrayList<BotSkillingActivity<Ore>>()
        for ((zone, supported) in rocks) {
            if (zone == SubZone.CRAFTING_GUILD_MINE &&
                (bot.crafting.level < CraftingGuild.REQUIRED_LEVEL || !bot.itemTracker.contains(CraftingGuild.BROWN_APRON))) {
                continue
            }
            if (zone == SubZone.MINING_GUILD &&
                (level < MiningGuild.REQUIRED_LEVEL || bot.mining.level < MiningGuild.REQUIRED_LEVEL)) {
                continue
            }
            if (training && zone == SubZone.AL_KHARID_MINE && bot.combatLevel <= 30) {
                continue
            }
            if (training && zone in setOf(SubZone.EDGEVILLE_DUNGEON_MINE, SubZone.DEEP_WILDERNESS_RUNITE_ROCKS)) {
                continue
            }
            val tokens = linkedSetOf<Ore>()
            val mineOres = if (zone == SubZone.MINING_GUILD && training && efficient && !powerTraining)
                setOf(COAL, MITHRIL) else desired
            for (ore in mineOres) {
                if (ore in supported && ore.level <= level) {
                    tokens.add(ore)
                }
            }
            if (tokens.isNotEmpty()) {
                activities.add(BotSkillingActivity(tokens = tokens, zones = setOf(zone)))
            }
        }
        return activities
    }

    /** A recent escape prevents a guild preference from sending the bot straight back to an attacker. */
    private fun prefersGuild(bot: Bot, training: Boolean): Boolean =
        !(training && MiningBehavior.styleOf(bot.personality) == Style.POWER_MINER) &&
        (if (training) bot.personality.isDextrous || bot.personality.isIntelligent else bot.personality.isIntelligent) &&
                bot.recentEscape?.avoids(SubZone.MINING_GUILD) != true

    /**
     * Chooses training ores using the existing personality preferences.
     */
    private fun trainingOres(bot: Bot, level: Int): Set<Ore> {
        if (level < IRON.level) {
            return setOf(COPPER, TIN, CLAY)
        }
        if (MiningBehavior.styleOf(bot.personality) == Style.POWER_MINER) {
            return setOf(IRON)
        }
        if (bot.personality.isDextrous || bot.personality.isIntelligent) {
            val coalChance = if (bot.personality.isDextrous) 1.0 / 3.0 else 0.5
            if (rand(coalChance)) {
                return setOf(IRON, COAL)
            }
            return setOf(IRON)
        }
        if (bot.personality.isDumb) {
            return Ore.entries.toSet() - setOf(RUNE_ESSENCE, PURE_ESSENCE)
        }
        return setOf(IRON, COAL, GOLD, MITHRIL, ADAMANT, RUNE)
    }

    /**
     * Chooses profit ores using the existing level progression and intelligence rolls.
     */
    private fun profitOres(bot: Bot, level: Int): Set<Ore> {
        if (level < GOLD.level) {
            if (!rand(bot.personality.intelligence)) {
                return setOf(RUNE_ESSENCE)
            }
            if (level >= COAL.level) {
                return setOf(COAL)
            }
            if (level >= IRON.level) {
                return setOf(IRON)
            }
            return setOf(COPPER, TIN)
        }
        if (level < MITHRIL.level) {
            if (rand(bot.personality.intelligence)) {
                return setOf(GOLD)
            }
            return setOf(COAL, RUNE_ESSENCE)
        }
        if (level < ADAMANT.level) {
            if (rand(bot.personality.intelligence)) {
                return setOf(COAL, MITHRIL)
            }
            return setOf(GOLD, SILVER, COAL)
        }
        return setOf(MITHRIL, ADAMANT, RUNE)
    }
}
