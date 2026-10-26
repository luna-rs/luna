package engine.bot.coordinator.skill

import api.bot.script.BotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.PickpocketBotScript
import game.bot.scripts.skills.SearchBotScript
import game.bot.scripts.skills.StealBotScript
import game.skill.thieving.pickpocketNpc.ThievingNpcType
import game.skill.thieving.pickpocketNpc.ThievingNpcType.*
import game.skill.thieving.searchForTraps.ThievingChest
import game.skill.thieving.stealFromStall.ThievingStallType
import io.luna.game.model.mob.bot.Bot

/**
 * Describes pickpocket, stall, and chest methods with matching locations. Personality retains the
 * original method odds and early target progression; unavailable methods fall back to basic pickpocketing.
 * Watchmen at 65, Gnomes at 75 and Heroes at 80 extend both training and profit pickpocket selection through
 * the same level/personality policy. Existing pickpocket scripts own food, stun handling, loot and banking;
 * higher-level profit targets describe gathering choices rather than guaranteeing a market return.
 *
 * @author lare96
 */
object ThievingScriptFactory : SkillingScriptFactory(SKILL_THIEVING) {
    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        createScript(bot, level, zones, true)

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript =
        createScript(bot, level, zones, false)

    /**
     * Retains the existing rolls between pickpocketing, stalls, and chests.
     */
    private fun createScript(bot: Bot, level: Int, zones: MutableList<SubZone>, training: Boolean): BotScript {
        val shouldPickpocket = if (training) {
            bot.personality.isDextrous || rand(bot.personality.dexterity) ||
                rand(bot.personality.intelligence) || level < ThievingStallType.VEGETABLE.level
        } else {
            rand(bot.personality.intelligence) || level < ThievingStallType.SEED.level
        }
        if (shouldPickpocket) {
            return createPickpocketScript(bot, level, zones, training)
        }
        if (!training && level >= ThievingChest.STEEL_ARROWTIPS.level && !randBoolean()) {
            val chest: ThievingChest
            val zone: SubZone
            if (level >= ThievingChest.BLOOD_RUNES.level) {
                chest = ThievingChest.BLOOD_RUNES
                zone = SubZone.CHAOS_DRUID_TOWER_DUNGEON
            } else {
                chest = ThievingChest.STEEL_ARROWTIPS
                zone = SubZone.HEMENSTER_CHEST_ROOM
            }
            val activity = BotSkillingActivity(tokens = setOf(chest), zones = setOf(zone))
            zones.clear()
            zones.addAll(activity.zones)
            return SearchBotScript(bot, activity.tokens, getDuration(bot), zones)
        }
        val stalls = ArrayList<BotSkillingActivity<ThievingStallType>>()
        if (training) {
            stalls.add(stall(setOf(ThievingStallType.WINE, ThievingStallType.SEED), SubZone.DRAYNOR_MAIN))
            val ardougneStalls = setOf(
                ThievingStallType.BAKERY, ThievingStallType.SILK, ThievingStallType.FUR,
                ThievingStallType.SILVER, ThievingStallType.SPICE, ThievingStallType.GEM
            )
            stalls.add(stall(ardougneStalls, SubZone.ARDOUGNE_SQUARE_THIEVING))
        } else if (!bot.personality.isDumb && level >= ThievingStallType.GEM.level) {
            stalls.add(stall(setOf(ThievingStallType.GEM), SubZone.ARDOUGNE_SQUARE_THIEVING))
        } else {
            stalls.add(stall(setOf(ThievingStallType.SEED), SubZone.DRAYNOR_MAIN))
        }
        val activity = getBestSkillingActivity(bot, level, { it.level }, stalls)
        if (activity == null) {
            return createPickpocketScript(bot, level, zones, training)
        }
        zones.clear()
        zones.addAll(activity.zones)
        return StealBotScript(bot, activity.tokens, getDuration(bot), zones)
    }

    /**
     * Selects a pickpocket target and its matching route.
     */
    private fun createPickpocketScript(
        bot: Bot,
        level: Int,
        zones: MutableList<SubZone>,
        training: Boolean
    ): PickpocketBotScript {
        val options = pickpockets(bot, level, training) + listOf(
            npc(setOf(WATCHMAN), SubZone.YANILLE_WATCHMEN),
            npc(setOf(GNOME), SubZone.GNOME_STRONGHOLD_THIEVING),
            npc(setOf(HERO), SubZone.ARDOUGNE_SQUARE_THIEVING)
        )
        val activity = getBestSkillingActivity(bot, level, { it.level }, options)
            ?: error("No pickpocket activity is available at level $level.")
        zones.clear()
        zones.addAll(activity.zones)
        return PickpocketBotScript(bot, activity.tokens, getDuration(bot), zones)
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
    private fun stall(tokens: Set<ThievingStallType>, zone: SubZone) =
        BotSkillingActivity(tokens = tokens, zones = setOf(zone))
}
