package engine.bot.coordinator

import api.bot.zone.SubZone
import api.predef.*
import com.google.common.collect.HashMultimap
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import api.bot.action.CombatLoadout
import game.bot.scripts.NpcCombatScript
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotBrain.BotCoordinator
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType
import kotlin.time.Duration.Companion.minutes

/**
 * Selects supply-ready combat disciplines and level-appropriate, explicitly named NPC camps.
 * Candidate camps share the existing zone travel fallback. KBD requires combat readiness of at least
 * 100, profit mode, a one-handed melee loadout and an owned anti-dragon shield. The combat script
 * equips protection and withdraws stronger food before travelling to the lair.
 *
 * @property training Whether to select training camps instead of camps with useful resource drops.
 * @author lare96
 */
class CombatCoordinator(private val training: Boolean) : BotCoordinator {
    companion object {
        /** A configured spawn group and the minimum combat readiness needed for deliberate attacks. */
        internal data class Camp(val zone: SubZone, val minimum: Int, val names: Set<String>, val profit: Boolean = false)

        /** Existing subzones and exact names verified against the configured spawn list. */
        internal val camps = listOf(
            Camp(SubZone.LUMBRIDGE_CHICKEN_COOP, 3, setOf("Chicken")),
            Camp(SubZone.LUMBRIDGE_COW_PEN, 3, setOf("Cow", "Cow calf")),
            Camp(SubZone.GOBLIN_VILLAGE, 3, setOf("Goblin")),
            Camp(SubZone.LUMBRIDGE_SWAMP, 3, setOf("Giant rat")),
            Camp(SubZone.LUMBRIDGE_RIVER, 3, setOf("Goblin", "Giant spider")),
            Camp(SubZone.ICE_MOUNTAIN, 10, setOf("Icefiend", "Dwarf")),
            Camp(SubZone.EDGEVILLE_MONASTERY, 10, setOf("Monk")),
            Camp(SubZone.ROCK_CRABS, 10, setOf("Rock Crab")),
            Camp(SubZone.HAM_CULT, 15, setOf("H.A.M. Guard")),
            Camp(SubZone.AL_KHARID_PALACE, 15, setOf("Al-Kharid warrior")),
            Camp(SubZone.BARBARIAN_VILLAGE, 20, setOf("Barbarian")),
            Camp(SubZone.CHAOS_DRUID_TOWER, 25, setOf("Chaos druid"), true),
            Camp(SubZone.DRAYNOR_SEWERS, 25, setOf("Zombie", "Skeleton")),
            Camp(SubZone.EDGEVILLE_DUNGEON_HILL_GIANTS, 35, setOf("Hill giant"), true),
            Camp(SubZone.NORTH_FALADOR_CHAOS_TEMPLE, 35, setOf("Monk of Zamorak")),
            Camp(SubZone.WHITE_KNIGHTS_CASTLE, 40, setOf("White Knight")),
            Camp(SubZone.VARROCK_SEWERS, 50, setOf("Moss giant"), true),
            Camp(SubZone.CHAOS_DRUID_TOWER_DUNGEON, 55, setOf("Ogre"), true),
            Camp(SubZone.KARAMJA_DUNGEON, 65, setOf("Lesser demon"), true),
            Camp(SubZone.GREEN_DRAGONS, 75, setOf("Green dragon"), true),
            Camp(SubZone.KBD_LAIR, 100, setOf("King black dragon"), true)
        )
        // TODO Add Fire Giant and Blue Dragon camps when lare96 supplies subzone data.
        // Camp(SubZone.FIRE_GIANT_CAMP, 65, setOf("Fire giant"), true)
        // Camp(SubZone.BLUE_DRAGON_CAMP, 75, setOf("Blue dragon"), true)

        /** Caps camp difficulty by the current offensive levels used by this session, including melee pures. */
        internal fun combatReadiness(bot: Bot, purpose: BotGearPurpose): Int {
            val offensive = when (purpose) {
                BotGearPurpose.RANGED -> bot.ranged.level
                BotGearPurpose.MAGIC -> bot.magic.level
                else -> minOf(bot.attack.level, bot.strength.level)
            }
            return minOf(bot.combatLevel, offensive * 2 + 3)
        }

        /** Restricts camps by offensive readiness, profit intent and dragon-shield compatibility. */
        internal fun eligibleCamps(level: Int, training: Boolean, purpose: BotGearPurpose,
                                   hasDragonShield: Boolean): List<Camp> {
            val eligible = camps.filter { it.minimum <= level &&
                (it.zone !in NpcCombatScript.DRAGON_ZONES ||
                    (!training && purpose == BotGearPurpose.MELEE && hasDragonShield)) }
            val preferred = if (training) eligible else eligible.filter { it.profit }.ifEmpty { eligible }
            // Keep several adjacent tiers available so sessions distribute across camps instead of one top-level zone.
            val threshold = (preferred.maxOfOrNull { it.minimum } ?: 3) - 25
            return preferred.filter { it.minimum >= threshold || (training && it.zone == SubZone.ROCK_CRABS) }
        }
    }

    override fun accept(bot: Bot) {
        if (bot.personality.type == PersonalityTemplateType.SKILLER) {
            (if (training) BotActivity.TRAINING_SKILLS else BotActivity.PROFIT_SKILLS).coordinator.accept(bot)
            return
        }
        val ready = listOf(BotGearPurpose.MELEE, BotGearPurpose.RANGED, BotGearPurpose.MAGIC)
            .mapNotNull { purpose -> CombatLoadout.find(bot, purpose)?.let { purpose to it } }
        val (purpose, loadout) = ready.randomOrNull() ?: return
        val level = combatReadiness(bot, purpose)
        val selected = eligibleCamps(level, training, purpose, bot.itemTracker.contains(1540) &&
            loadout.gear[io.luna.game.model.item.Equipment.WEAPON]?.let { !equipDef(it).isTwoHanded } == true).shuffled().take(4)
        if (selected.isEmpty()) return
        val names = HashMultimap.create<SubZone, String>()
        selected.forEach { names.putAll(it.zone, it.names) }
        val duration = if (bot.preferences.likesActivity(BotActivity.TRAINING_COMBAT) ||
            bot.preferences.likesActivity(BotActivity.PROFIT_COMBAT)) rand(100, 350).minutes else rand(45, 120).minutes
        bot.scriptStack.push(NpcCombatScript(bot, duration, selected.map { it.zone }.toMutableList(),
            names, purpose, loadout.spell?.spell))
    }
}
