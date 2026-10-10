package engine.bot.coordinator

import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.common.collect.HashMultimap
import game.bot.scripts.NpcCombatScript
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator
import io.luna.game.model.mob.bot.brain.BotPersonalityManager
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType
import io.luna.util.RandomUtils.roll
import kotlin.time.Duration.Companion.minutes

/**
 * Selects combat sessions for training or profit according to the bot's combat level and personality.
 *
 * Training and profit modes select NPC combat using their existing zone and session-duration rules.
 * Supply preparation and crystal-key activities are selected by [GeneralActivityCoordinator].
 *
 * @property training Whether to prioritize combat training instead of profit activities.
 * @author lare96
 */
class CombatCoordinator(private val training: Boolean) : BotCoordinator {

    // TODO Add and test behaviour for retrieving items after dying. Very relevant to combat.
    // TODO Support for magic and ranged, along with safe-spotting.
    // TODO When in profit mode, do not auto-bury bones after looting, bank them for profit.
    // TODO Profit combat includes bosses along with dangerous locations and monsters. As well as monsters with notable
    //  drops (KBD, chromatic dragons, lava dragons, lesser and greater demons, slayer tasks, hill giants, chaos druids).
    // TODO In training mode, whenever 2 levels or more are gained, switch the current combat skill we're training.
    // TODO Profit vs training.
    // TODO if training in wild choose less good gear
    // TODO Make cleaner

    override fun accept(bot: Bot) {
        if(bot.personality.type == PersonalityTemplateType.SKILLER) {
            // Skillers remain level 3 and do not train combat.
            if(training) {
                BotActivity.TRAINING_SKILLS.coordinator.accept(bot)
            } else {
                BotActivity.PROFIT_SKILLS.coordinator.accept(bot)
            }
            return
        }
        val duration =
            if (bot.preferences.likesActivity(BotActivity.TRAINING_COMBAT) ||
                bot.preferences.likesActivity(BotActivity.PROFIT_COMBAT)
            )
                rand(100, 350).minutes else rand(45, 120).minutes
        val names = HashMultimap.create<SubZone, String>()
        val zones = ArrayList<SubZone>()
        bot.log("Selecting combat zones for bot combat level ${bot.combatLevel}.")
        if (bot.combatLevel < 10) {
            if (bot.personality.isIntelligent || roll(1 of 4)) {
                zones += SubZone.ROCK_CRABS
            }
            if (randBoolean()) {
                zones += SubZone.LUMBRIDGE_SWAMP
                zones += SubZone.LUMBRIDGE_RIVER
                zones += SubZone.GOBLIN_VILLAGE
                zones += SubZone.LUMBRIDGE_COW_PEN
                zones += SubZone.LUMBRIDGE_CHICKEN_COOP
            } else {
                zones += SubZone.LUMBRIDGE_SWAMP
                zones += SubZone.LUMBRIDGE_RIVER
                zones += SubZone.LUMBRIDGE_COW_PEN
            }
        } else if (bot.combatLevel < 20) {
            // TODO chaos druids
            if (bot.personality.isIntelligent || roll(1 of 4)) {
                zones += SubZone.ROCK_CRABS
                zones += SubZone.ICE_MOUNTAIN
                zones += SubZone.HAM_CULT
                zones += SubZone.VARROCK_DARK_WIZARDS
                names.put(SubZone.ICE_MOUNTAIN, "Dwarf")
            } else {
                zones += SubZone.CHAOS_DRUID_TOWER
                zones += SubZone.ROCK_CRABS
                zones += SubZone.AL_KHARID_PALACE
                zones += SubZone.EDGEVILLE_MONASTERY
                zones += SubZone.BARBARIAN_VILLAGE
                zones += SubZone.ICE_MOUNTAIN
                names.put(SubZone.ICE_MOUNTAIN, "Icefiend")
            }
        } else if (bot.combatLevel < 30) {
            if (bot.personality.isIntelligent || roll(1 of 4)) {
                zones += SubZone.ROCK_CRABS
                zones += SubZone.ICE_MOUNTAIN
                zones += SubZone.HAM_CULT
                zones += SubZone.DRAYNOR_SEWERS
                names.put(SubZone.ICE_MOUNTAIN, "Dwarf")
            } else {
                zones += SubZone.HAM_CULT
                zones += SubZone.AL_KHARID_MINE
                zones += SubZone.ICE_MOUNTAIN
                zones += SubZone.DRAYNOR_SEWERS
                zones += SubZone.VARROCK_DARK_WIZARDS
                names.put(SubZone.ICE_MOUNTAIN, "Icefiend")
            }
        } else if (bot.combatLevel < 40) {
            if (bot.personality.isIntelligent || roll(1 of 4)) {
                zones += SubZone.ROCK_CRABS
                zones += SubZone.HAM_CULT
            }
            zones += SubZone.DRAYNOR_SEWERS
            zones += SubZone.EDGEVILLE_DUNGEON_HILL_GIANTS
            zones += SubZone.NORTH_FALADOR_CHAOS_TEMPLE
            if(bot.personality.isDumb) {
                zones+= SubZone.LVL_20_WILDERNESS_CHAOS_TEMPLE
            }
        } else if (bot.combatLevel < 50) {
            if (bot.personality.isIntelligent || roll(1 of 4)) {
                zones += SubZone.ROCK_CRABS
                zones += SubZone.VARROCK_SEWERS
            }
            zones += SubZone.WHITE_KNIGHTS_CASTLE
            zones += SubZone.DRAYNOR_SEWERS
            zones += SubZone.HAM_CULT
            zones += SubZone.EDGEVILLE_DUNGEON_HILL_GIANTS
            zones += SubZone.NORTH_FALADOR_CHAOS_TEMPLE
            if(bot.personality.isDumb) {
                zones+= SubZone.LVL_20_WILDERNESS_CHAOS_TEMPLE
            }
        } else if (bot.combatLevel < 60) {
            if (bot.personality.isIntelligent || roll(1 of 4)) {
                zones += SubZone.ROCK_CRABS
            }
            zones += SubZone.VARROCK_SEWERS
            zones += SubZone.WHITE_KNIGHTS_CASTLE
            zones += SubZone.DRAYNOR_SEWERS
            zones += SubZone.HAM_CULT
            zones += SubZone.EDGEVILLE_DUNGEON_HILL_GIANTS
            if(bot.personality.isDumb) {
              zones+= SubZone.LVL_20_WILDERNESS_CHAOS_TEMPLE
            }
        } else if (bot.combatLevel < 80) {
            // TODO ice giant, ice warrior, earth warrior, jogre, cyclops,
            // zamorak wizard, infernal mage, mountain troll
            if (bot.personality.isIntelligent || roll(1 of 4)) {
                zones += SubZone.KARAMJA_DUNGEON
            }
            zones += SubZone.ROCK_CRABS
            zones += SubZone.EDGEVILLE_DUNGEON_HILL_GIANTS
            zones += SubZone.VARROCK_SEWERS
            zones += SubZone.CHAOS_DRUID_TOWER_DUNGEON

            if (bot.combatLevel > 70 && !training) {
                zones += SubZone.GREEN_DRAGONS
            }
        } else if (bot.combatLevel < 100) {
            if (!training) {
                zones += SubZone.GREEN_DRAGONS
                zones += SubZone.KBD_LAIR

            }
            zones += SubZone.CHAOS_DRUID_TOWER_DUNGEON

            // TODO Fire giant, Turoth, Lesser demon, Greater demon, Kalphite Soldier
            //Dust devil, Aberrant specter, Greater demon
            zones += SubZone.KARAMJA_DUNGEON

            names.put(SubZone.KARAMJA_DUNGEON, "Lesser demon")
        } else {
            if (!training) {
                zones += SubZone.GREEN_DRAGONS
                zones += SubZone.KBD_LAIR
            }
            zones += SubZone.KARAMJA_DUNGEON

            names.put(SubZone.KARAMJA_DUNGEON, "Lesser demon")

            // TODO Fire giant, Turoth, Lesser demon, Greater demon, Kalphite Soldier
            // Kurask, Blue dragon, Saradomin/Zamorak Wizards
            // Gargoyle, Nechryael, Abyssal demon, Ice troll, Blue dragon

        }
        if(zones.isEmpty()) {
            zones += SubZone.ROCK_CRABS
        }
        bot.scriptStack.push(NpcCombatScript(bot, duration, zones, names))
    }
}
