package engine.bot.coordinator

import api.bot.zone.SubZone
import api.predef.rand
import api.predef.randBoolean
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
    /* TODO@0.5.0
            Low level popular spots (10-30)
                - the most intelligent bots will always go to rock crabs as soon as possible. > 50% intelligence have a
                   chance to go to rock crabs but not guaranteed
            Other low level spots
                - varrock dark wizards by the circle
            Mid level popular spots (30-60)
                - rock crabs (> 80% intelligence always goes)
                - varrock sewers (moss giants)
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
        // TODO varrock sewers
        val zones = ArrayList<SubZone>()
        if (bot.combatLevel < 10) {
            if(randBoolean()) {
                zones += SubZone.LUMBRIDGE_SWAMP
                zones += SubZone.LUMBRIDGE_RIVER
                zones += SubZone.EAST_DRAYNOR_YEWS
                zones += SubZone.GOBLIN_VILLAGE
                zones += SubZone.LUMBRIDGE_COW_PEN
            } else {
                zones += SubZone.LUMBRIDGE_SWAMP
                zones += SubZone.LUMBRIDGE_RIVER
                zones += SubZone.LUMBRIDGE_COW_PEN
            }
        } else if (bot.combatLevel < 20) {
            // varrock sewers
            zones += SubZone.BARBARIAN_VILLAGE
            if (rand(1.0 - bot.personality.intelligence)) {
                zones += SubZone.LUMBRIDGE_RIVER
                zones += SubZone.EAST_DRAYNOR_YEWS
                zones += SubZone.GOBLIN_VILLAGE
            }
            if(bot.personality.isIntelligent) {
                zones += SubZone.AL_KHARID_PALACE
                zones += SubZone.EDGEVILLE_MONASTERY
            }
        } else if (bot.combatLevel < 40) {
            // dark wizards at varrock entry
            if(bot.personality.isIntelligent) {
                zones += SubZone.ROCK_CRABS
            }
            zones += SubZone.NORTH_FALADOR_CHAOS_TEMPLE
            zones += SubZone.BARBARIAN_VILLAGE
            zones += SubZone.AL_KHARID_PALACE
            zones += SubZone.EDGEVILLE_MONASTERY
            if(bot.personality.isIntelligent) {
                zones += SubZone.EDGEVILLE_DUNGEON_HILL_GIANTS
            }
        } else if (bot.combatLevel < 80) {
            if(bot.personality.isIntelligent) {
                zones += SubZone.ROCK_CRABS
            }
            zones += SubZone.EDGEVILLE_DUNGEON_HILL_GIANTS
            zones += SubZone.CHAOS_DRUID_TOWER
            zones += SubZone.NORTH_FALADOR_CHAOS_TEMPLE
            zones += SubZone.EDGEVILLE_DUNGEON_MINE
            zones += SubZone.ARDOUGNE_SQUARE_THIEVING

        } else  {
            // TODO higher level monsters, demons, dragons, etc.
        }
       bot.scriptStack.pushHead(CombatTrainingScript(bot, 100.minutes, zones))
    }
}