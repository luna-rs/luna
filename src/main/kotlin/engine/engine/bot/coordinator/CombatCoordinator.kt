package engine.bot.coordinator

import api.bot.zone.SubZone
import game.bot.scripts.CombatTrainingScript
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator
import kotlin.time.Duration.Companion.minutes

class CombatCoordinator(private val training: Boolean) : BotCoordinator {

    // todo test retrieving items after death
    // todo 0.75 boost to combat training chance if intelligent 0.5 for average, 0.25 below average, and no boost for the dumbest bots
    //TODO@0.5.0 Before this is done, item sets need to be added so we can determine what to equip, etc.
    // also need to add support for magic sets maybe? ranged sets? sasfespotting?
    /* Combat:
     * - Kill profitable but dangerous monsters such as green dragons, red dragons, and lava dragons.
     * - Add bossing behavior later for King Black Dragon and Kalphite Queen.
     // todo switching styles to train correct stats
 */
    /* TODO@0.5.0 Training spots (once combat training coordinator and equipment selection are complete).
            We may also need to do obstacles like doors and ladders to completion before starting this as well.
            SkillingBotScript needs to be opened up to be general purpose ZonedBotScript (stays within a zone and relays
            processing to subclasses). Instead of just taking a list of zones, scripts should take a list of zones with weights
            (to determine how likely the bot is to go to that zone regardless of distance, etc).
            Beginner popular spots (1-10)
                - lumby chicken pen (3184, 3265, 3191, 3279)
                - lumby north-east cow field (3241, 3255, 3265, 3297)
            Other beginner spots
                - lumby north cow field (3190, 3282, 3213, 3303)
                - lumby swamp (3203, 3168, 3231, 3193)
                - lumby goblins
            Low level popular spots (10-30)
                - al kharid palace (smart bots) (3281, 3158, 3304, 3178)
                - the most intelligent bots will always go to rock crabs as soon as possible. > 50% intelligence have a
                   chance to go to rock crabs but not guaranteed
            Other low level spots
                - edgeville monastary (3039, 3478, 3063, 3510)
                - barbarian village (3068, 3404, 3100, 3449)
                - varrock dark wizards by the circle
            Mid level popular spots (30-60)
                - rock crabs (> 80% intelligence always goes)
                - varrock sewers (moss giants)
                - edgeville dungeon (hill giants)
            Other mid level spots
                - chaos druid tower
                - taverly dungeon (chaos druids)
                - guards in popular cities (dumb bots)
            High level popular spots (60+)
                - intelligent bots will just train slayer instead of explicitly training combat
                - crandor (lesser demons, other stuff)
            Other high level spots
                - white wolf mountain (ice giants, other stuff)
                - wilderness (greater demons)
                - wilderness (green dragons)
                - taverly dungeon (poison spiders, hellhound)
                - brimhaven dungeon (everything)
         */

    override fun accept(bot: Bot) {
        // For now, travel to any zone without transitions and fight any attackable mobs.
        // TODO Combat script for the above ^ and basic equipment selection
        // for now, ONLY BEGINNER AREAS!!
        val zones = ArrayList<SubZone>()
        if (bot.combatLevel < 10) {
            zones += SubZone.LUMBRIDGE_RIVER
            zones += SubZone.EAST_DRAYNOR_YEWS
            // cow field lumby, varrock sewers
        } else if (bot.combatLevel < 20) {
            zones += SubZone.BARBARIAN_VILLAGE
            zones += SubZone.WIZARDS_TOWER
            zones += SubZone.GOBLIN_VILLAGE
            if (bot.personality.isDumb) {
                // use rand(1.0 - intelligence)
                zones += SubZone.LUMBRIDGE_RIVER
                zones += SubZone.EAST_DRAYNOR_YEWS
            }
            // al kharid palace
        } else if (bot.combatLevel < 40) {
            // edgeville monastery, rock crabs, dark wizards at verrock entry lol lol
            zones += SubZone.NORTH_FALADOR_CHAOS_TEMPLE
            zones += SubZone.BARBARIAN_VILLAGE

        } else  {
            zones += SubZone.CHAOS_DRUID_TOWER
            zones += SubZone.NORTH_FALADOR_CHAOS_TEMPLE
            zones += SubZone.EDGEVILLE_DUNGEON_MINE
            zones += SubZone.ARDOUGNE_SQUARE_THIEVING

        }
        // al kharid palace
       bot.scriptStack.pushHead(CombatTrainingScript(bot, 100.minutes, zones))
    }
}