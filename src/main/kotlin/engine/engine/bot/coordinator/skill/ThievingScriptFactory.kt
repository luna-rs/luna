package engine.bot.coordinator.skill

import api.bot.BotScript
import api.bot.zone.SubZone
import api.predef.*
import game.bot.scripts.skills.PickpocketBotScript
import game.bot.scripts.skills.StealBotScript
import game.skill.thieving.pickpocketNpc.ThievingNpcType
import game.skill.thieving.stealFromStall.ThievingStallType
import io.luna.game.model.mob.bot.Bot
import java.util.*

/**
 * Creates Thieving scripts based on the bot's level, personality, and objective.
 *
 * Training scripts favor experience-oriented routes. Dexterous bots are more likely to pickpocket because
 * pickpocketing is more active and generally more efficient. Intelligent bots may also prefer stronger pickpocket
 * routes, while less focused bots may fall back to simpler stall-stealing.
 *
 * Profit scripts favor higher-value Thieving methods when the bot's level allows it.
 *
 * @author lare96
 */
object ThievingScriptFactory : SkillingScriptFactory(SKILL_THIEVING) {

    override fun getTrainingScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {

        /**
         * Creates a stall-stealing training script.
         *
         * The bot may steal from any stall type it has the level to use.
         */
        fun stealingFromStalls(bot: Bot): StealBotScript {
            val stalls = EnumSet.noneOf(ThievingStallType::class.java)

            ThievingStallType.FULL_STALLS.values.forEach { stalls += it }

            zones += SubZone.DRAYNOR_MAIN
            zones += SubZone.ARDOUGNE_SQUARE_THIEVING

            // TODO@1.0 Add Ape Atoll access, stall fixes, and an Ape Atoll thieving sub-zone.

            stalls.removeIf { level < it.level }
            return StealBotScript(bot, stalls, getDuration(bot), zones)
        }

        /**
         * Creates a pickpocketing training script.
         *
         * Early levels use staged targets so bots progress through sensible training
         * routes. Higher levels unlock broader target selection, with dexterous bots
         * preferring focused Ardougne pickpocketing.
         */
        fun pickpocketing(bot: Bot): PickpocketBotScript {
            val npcs = EnumSet.noneOf(ThievingNpcType::class.java)

            if (level >= ThievingNpcType.GUARD.level) {
                // Once guard pickpocketing is available, bots can start using stronger routes.
                ThievingNpcType.NAME_TO_NPC.values.forEach { npcs += it }

                if (bot.personality.isDextrous || rand(bot.personality.dexterity)) {
                    // Highly dexterous bots focus on guard or Ardougne knight pickpocketing.
                    npcs.clear()

                    npcs +=
                        if (level >= ThievingNpcType.KNIGHT_OF_ARDOUGNE.level) {
                            ThievingNpcType.KNIGHT_OF_ARDOUGNE
                        } else {
                            ThievingNpcType.GUARD
                        }

                    zones += SubZone.ARDOUGNE_SQUARE_THIEVING
                } else if (rand(bot.personality.intelligence)) {
                    // Intelligent bots tend to use Ardougne when possible.
                    zones += SubZone.ARDOUGNE_SQUARE_THIEVING
                } else {
                    // Otherwise, use easier general-purpose pickpocketing areas.
                    zones += SubZone.DRAYNOR_MAIN
                }
            } else {
                // Before efficient guard pickpocketing, use staged low-level routes.
                if (level < 10) {
                    npcs += ThievingNpcType.MAN_AND_WOMAN
                    zones += SubZone.LUMBRIDGE_COURT_YARD
                } else if (level < 25) {
                    if (!bot.personality.isDextrous && bot.personality.isDumb) {
                        npcs += ThievingNpcType.MAN_AND_WOMAN
                        zones += SubZone.LUMBRIDGE_COURT_YARD
                    }

                    npcs += ThievingNpcType.FARMER
                    zones += SubZone.NORTH_LUMBRIDGE_FARMING
                } else {
                    if (!bot.personality.isDextrous && bot.personality.isDumb) {
                        npcs += ThievingNpcType.MAN_AND_WOMAN
                        npcs += ThievingNpcType.FARMER

                        zones += SubZone.NORTH_LUMBRIDGE_FARMING
                        zones += SubZone.LUMBRIDGE_COURT_YARD
                    }

                    npcs += ThievingNpcType.WARRIOR
                    zones += SubZone.ARDOUGNE_SQUARE_THIEVING
                }
            }

            npcs.removeIf { level < it.level }
            return PickpocketBotScript(bot, npcs, getDuration(bot), zones)
        }

        // Dexterous bots strongly favor pickpocketing for faster Thieving training.
        if (bot.personality.isDextrous || rand(bot.personality.dexterity)) {
            return pickpocketing(bot)
        }

        // Intelligent bots also lean toward pickpocketing, but less aggressively.
        return if (rand(bot.personality.intelligence) || level < ThievingStallType.VEGETABLE.level) {
            pickpocketing(bot)
        } else {
            stealingFromStalls(bot)
        }
    }

    override fun getProfitScript(bot: Bot, level: Int, zones: MutableList<SubZone>): BotScript {

        /**
         * Creates a stall-stealing profit script.
         *
         * Dumb bots use easier seed stalls, while other bots prefer gem stalls.
         */
        fun stealingFromStalls(bot: Bot): StealBotScript {
            val stalls = EnumSet.noneOf(ThievingStallType::class.java)

            if (bot.personality.isDumb) {
                stalls += ThievingStallType.SEED
                zones += SubZone.DRAYNOR_MAIN
            } else {
                stalls += ThievingStallType.GEM
                zones += SubZone.ARDOUGNE_SQUARE_THIEVING
            }

            if (stalls.isEmpty()) {
                stalls += ThievingStallType.SEED
                zones += SubZone.DRAYNOR_MAIN
            }

            stalls.removeIf { level < it.level }
            return StealBotScript(bot, stalls, getDuration(bot), zones)
        }

        /**
         * Creates a pickpocketing profit script.
         *
         * Higher-level bots target more profitable NPCs, while lower-level bots fall back to basic pickpocketing
         * routes until better targets are unlocked.
         */
        fun pickpocketing(bot: Bot): PickpocketBotScript {
            val npcs = EnumSet.noneOf(ThievingNpcType::class.java)

            if (level >= ThievingNpcType.PALADIN.level) {
                npcs += ThievingNpcType.PALADIN
                zones += SubZone.ARDOUGNE_SQUARE_THIEVING
            } else if (level >= ThievingNpcType.KNIGHT_OF_ARDOUGNE.level) {
                npcs += ThievingNpcType.KNIGHT_OF_ARDOUGNE
                zones += SubZone.ARDOUGNE_SQUARE_THIEVING
            } else if (level >= ThievingNpcType.MASTER_FARMER.level) {
                npcs += ThievingNpcType.MASTER_FARMER
                zones += SubZone.DRAYNOR_MAIN
            } else {
                npcs += ThievingNpcType.MAN_AND_WOMAN
                zones += SubZone.LUMBRIDGE_COURT_YARD
            }

            npcs.removeIf { level < it.level }
            return PickpocketBotScript(bot, npcs, getDuration(bot), zones)
        }

        return if (rand(bot.personality.intelligence) || level < ThievingStallType.SEED.level) {
            pickpocketing(bot)
        } else {
            stealingFromStalls(bot)
        }
    }
}