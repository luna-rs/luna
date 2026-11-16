package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.PickpocketBotScript
import game.bot.scripts.skills.StealBotScript
import game.bot.scripts.skills.RoguesDenBotScript
import game.skill.thieving.pickpocketNpc.ThievingNpcType
import game.skill.thieving.pickpocketNpc.ThievingNpcType.*
import game.skill.thieving.stealFromStall.ThievingStallType
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivityWeights
import kotlin.time.Duration.Companion.minutes

/**
 * Describes pickpocket and stall methods with matching locations. Personality retains the
 * training method odds and early target progression; unavailable methods fall back to basic pickpocketing.
 * Watchmen at 65, Gnomes at 75 and Heroes at 80 extend both training and profit pickpocket selection through
 * the same level/personality policy. Existing pickpocket scripts own food, stun handling, loot and banking;
 * profit outings pickpocket or steal gems and offer saleable loot at the market on completion.
 * Confident bots can pickpocket the two outdoor Wilderness rogues, subject to their normal death-loss budget.
 * Native crafting, magic and scimitar stalls join the same level/personality selection as other stalls.
 * Eligible training bots also attempt Rogues' Den with its independent Agility and Thieving point budgets.
 *
 * @author lare96
 */
object ThievingScriptFactory : SkillingScriptFactory(SKILL_THIEVING) {
    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        createTrainingScript(bot, level, zones)

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        val needsCoins = BotActivityWeights.needsStarterCoins(bot)
        if (needsCoins || level < ThievingStallType.GEM.level || rand(bot.personality.intelligence)) {
            return createPickpocketScript(bot, level, zones, training = false, coinsOnly = needsCoins)
        }
        zones.clear()
        zones += SubZone.ARDOUGNE_SQUARE_THIEVING
        return StealBotScript(bot, setOf(ThievingStallType.GEM), getDuration(bot), zones,
            sellOnCompletion = true)
    }

    /**
     * Retains the existing training rolls between pickpocketing, stalls, and Rogues' Den.
     */
    private fun createTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {
        if (RoguesDenBotScript.eligible(bot) && rand(0.15)) {
            zones.clear()
            zones += SubZone.ROGUES_DEN_MAZE
            return RoguesDenBotScript(bot, getDuration(bot).coerceAtLeast(20.minutes))
        }
        val shouldPickpocket = bot.personality.isDextrous || rand(bot.personality.dexterity) ||
            rand(bot.personality.intelligence) || level < ThievingStallType.VEGETABLE.level
        if (shouldPickpocket) {
            return createPickpocketScript(bot, level, zones, training = true)
        }
        val stalls = ArrayList<BotSkillingActivity<ThievingStallType>>()
        stalls.add(stall(setOf(ThievingStallType.WINE, ThievingStallType.SEED), SubZone.DRAYNOR_MAIN))
        val ardougneStalls = setOf(
            ThievingStallType.BAKERY, ThievingStallType.SILK, ThievingStallType.FUR,
            ThievingStallType.SILVER, ThievingStallType.SPICE, ThievingStallType.GEM
        )
        stalls.add(stall(ardougneStalls, SubZone.ARDOUGNE_SQUARE_THIEVING))
        stalls.add(stall(setOf(ThievingStallType.TEA), SubZone.VARROCK_TEA_STALL))
        stalls.add(stall(setOf(ThievingStallType.VEGETABLE),
            SubZone.MISCELLANIA_MARKET_STALLS, SubZone.ETCETERIA_MARKET_STALLS))
        stalls.add(stall(setOf(ThievingStallType.FISH), SubZone.RELLEKKA_MARKET_STALLS,
            SubZone.MISCELLANIA_MARKET_STALLS, SubZone.ETCETERIA_MARKET_STALLS))
        stalls.add(stall(setOf(ThievingStallType.FUR),
            SubZone.ARDOUGNE_SQUARE_THIEVING, SubZone.RELLEKKA_MARKET_STALLS))
        stalls.add(stall(setOf(ThievingStallType.CRAFTING),
            SubZone.KELDAGRIM_CRAFTING_STALL, SubZone.APE_ATOLL_MARKET_STALLS))
        stalls.add(stall(setOf(ThievingStallType.MAGIC, ThievingStallType.SCIMITAR), SubZone.APE_ATOLL_MARKET_STALLS))
        val activity = getBestSkillingActivity(bot, level, { it.level }, stalls)
        if (activity == null) {
            return createPickpocketScript(bot, level, zones, training = true)
        }
        zones.clear()
        zones.addAll((activity.zones + stalls.filter { option ->
            option.tokens.any { it in activity.tokens }
        }.flatMap { it.zones }).distinct())
        return StealBotScript(bot, activity.tokens, getDuration(bot), zones)
    }

    /**
     * Selects a pickpocket target and its matching route.
     */
    private fun createPickpocketScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>,
        training: Boolean,
        coinsOnly: Boolean = false
    ): PickpocketBotScript {
        val options = if (coinsOnly) coinPickpockets(level) else pickpockets(bot, level, training) + listOfNotNull(
            npc(setOf(ROGUE), SubZone.WILDERNESS_ROGUE_CAMP).takeIf { bot.personality.confidence >= 0.5 },
            npc(setOf(WATCHMAN), SubZone.YANILLE_WATCHMEN),
            npc(setOf(GNOME), SubZone.GNOME_STRONGHOLD_THIEVING),
            npc(setOf(HERO), SubZone.ARDOUGNE_SQUARE_THIEVING)
        )
        val activity = getBestSkillingActivity(bot, level, { it.level }, options)
            ?: error("No pickpocket activity is available at level $level.")
        zones.clear()
        zones.addAll(activity.zones)
        for (target in activity.tokens) zones.addAll(when (target) {
            MAN_AND_WOMAN -> listOf(SubZone.LUMBRIDGE_COURT_YARD, SubZone.SEERS_VILLAGE_MAIN,
                SubZone.VARROCK_EAST_BANK, SubZone.DRAYNOR_MAIN)
            GUARD -> listOf(SubZone.FALADOR_WEST_BANK, SubZone.HOME)
            FARMER -> listOf(SubZone.NORTH_ARDOUGNE_COW_FIELD)
            else -> emptyList()
        })
        val expanded = zones.distinct()
        zones.clear()
        zones.addAll(expanded)
        return PickpocketBotScript(bot, activity.tokens, getDuration(bot), zones,
            sellOnCompletion = !training)
    }

    /**
     * Uses level-appropriate coin-bearing NPCs when the bot needs immediately spendable gold.
     */
    private fun coinPickpockets(level: Int): List<BotSkillingActivity<ThievingNpcType>> {
        val target = when {
            level >= PALADIN.level -> PALADIN
            level >= KNIGHT_OF_ARDOUGNE.level -> KNIGHT_OF_ARDOUGNE
            level >= GUARD.level -> GUARD
            level >= WARRIOR.level -> WARRIOR
            level >= FARMER.level -> FARMER
            else -> MAN_AND_WOMAN
        }
        val zone = when (target) {
            PALADIN, KNIGHT_OF_ARDOUGNE, GUARD -> SubZone.ARDOUGNE_SQUARE_THIEVING
            WARRIOR -> SubZone.ARDOUGNE_WARRIOR_THIEVING
            FARMER -> SubZone.NORTH_LUMBRIDGE_FARMING
            else -> SubZone.LUMBRIDGE_COURT_YARD
        }
        return listOf(npc(setOf(target), zone))
    }

    /**
     * Builds the existing staged pickpocket progression without mixing NPCs across unrelated locations.
     */
    private fun pickpockets(bot: Bot, level: Int, training: Boolean): List<BotSkillingActivity<ThievingNpcType>> {
        if (!training) {
            val target = when {
                level >= PALADIN.level -> PALADIN
                level >= KNIGHT_OF_ARDOUGNE.level -> KNIGHT_OF_ARDOUGNE
                level >= MASTER_FARMER.level -> MASTER_FARMER
                else -> MAN_AND_WOMAN
            }
            val zone = when (target) {
                PALADIN, KNIGHT_OF_ARDOUGNE -> SubZone.ARDOUGNE_SQUARE_THIEVING
                MASTER_FARMER -> SubZone.DRAYNOR_MAIN
                else -> SubZone.LUMBRIDGE_COURT_YARD
            }
            return listOf(npc(setOf(target), zone))
        }
        if (level >= GUARD.level) {
            if (bot.personality.isDextrous || rand(bot.personality.dexterity)) {
                val target = when {
                    level >= PALADIN.level -> PALADIN
                    level >= KNIGHT_OF_ARDOUGNE.level -> KNIGHT_OF_ARDOUGNE
                    else -> GUARD
                }
                return listOf(npc(setOf(target), SubZone.ARDOUGNE_SQUARE_THIEVING))
            }
            if (rand(bot.personality.intelligence)) {
                return listOf(npc(setOf(GUARD, KNIGHT_OF_ARDOUGNE, PALADIN), SubZone.ARDOUGNE_SQUARE_THIEVING))
            }
            return listOf(npc(setOf(MAN_AND_WOMAN, MASTER_FARMER), SubZone.DRAYNOR_MAIN))
        }
        val casual = !bot.personality.isDextrous && bot.personality.isDumb
        val activities = ArrayList<BotSkillingActivity<ThievingNpcType>>()
        if (level < FARMER.level || casual) {
            activities.add(npc(setOf(MAN_AND_WOMAN), SubZone.LUMBRIDGE_COURT_YARD))
        }
        if (level >= FARMER.level && (level < WARRIOR.level || casual)) {
            activities.add(npc(setOf(FARMER), SubZone.NORTH_LUMBRIDGE_FARMING))
        }
        if (level >= WARRIOR.level) {
            activities.add(npc(setOf(WARRIOR), SubZone.ARDOUGNE_WARRIOR_THIEVING))
        }
        return activities
    }

    /**
     * Associates a supported group of NPCs with its pickpocket route.
     */
    private fun npc(tokens: Set<ThievingNpcType>, zone: SubZone) =
        BotSkillingActivity(tokens = tokens, zones = setOf(zone))

    /**
     * Associates a supported group of stalls with its theft route.
     */
    private fun stall(tokens: Set<ThievingStallType>, vararg zones: SubZone) =
        BotSkillingActivity(tokens = tokens, zones = zones.toSet())
}
