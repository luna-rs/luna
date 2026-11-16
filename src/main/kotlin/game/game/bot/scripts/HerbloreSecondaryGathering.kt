package game.bot.scripts

import api.bot.zone.SubZone
import api.predef.slayer
import api.predef.ext.totalItemAmount
import com.google.common.collect.ImmutableSetMultimap
import engine.bot.coordinator.CombatCoordinator
import engine.bot.gear.BotGearPurpose
import game.skill.slayer.Slayer.activeSlayerTask
import game.skill.slayer.SlayerTaskType
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType
import kotlin.time.Duration

/**
 * Selects source-backed Herblore secondary drops from named revision 377 NPC camps.
 * Specialist dungeon monsters require the bot's live matching Slayer assignment and the native owned melee kit.
 * Boss drops stay with their dedicated Giant Mole and Kalphite Queen combat trips.
 *
 * @author lare96
 */
object HerbloreSecondaryGathering {
    /**
     * One native drop, spawn camp and minimum melee readiness for a focused profit trip.
     *
     * @author lare96
     */
    private data class Camp(
        val itemId: Int,
        val batch: Int,
        val zone: SubZone,
        val npcName: String,
        val minimum: Int,
        val weight: Int = 1,
        val slayerTask: SlayerTaskType? = null
    )

    /**
     * Drops and coordinates come from Luna's configured NPC tables and spawn list, not later OSRS versions.
     * Common limpwurt roots and guaranteed horns receive more selection weight than rare warrior drops.
     */
    private val camps = listOf(
        Camp(225, 20, SubZone.EDGEVILLE_DUNGEON_HILL_GIANTS, "Hill giant", 35, 3),
        Camp(225, 20, SubZone.TAVERLY_HILL_GIANTS, "Hill giant", 35, 3),
        Camp(225, 20, SubZone.EDGEVILLE_DUNGEON_HOBGOBLINS, "Hobgoblin", 45, 2),
        Camp(237, 20, SubZone.EAST_VARROCK_UNICORNS, "Unicorn", 30, 4),
        Camp(237, 20, SubZone.RELLEKKA_BLACK_UNICORNS, "Black unicorn", 45, 3),
        Camp(225, 12, SubZone.YANILLE_DUNGEON_CHAOS_DRUIDS, "Chaos druid warrior", 45),
        Camp(239, 8, SubZone.YANILLE_DUNGEON_ZAMORAK_WINE, "Chaos druid warrior", 45),
        Camp(235, 5, SubZone.YANILLE_DUNGEON_ZAMORAK_WINE, "Chaos druid warrior", 45),
        Camp(231, 5, SubZone.YANILLE_DUNGEON_ZAMORAK_WINE, "Chaos druid warrior", 45),
        Camp(227, 10, SubZone.YANILLE_DUNGEON_ZAMORAK_WINE, "Chaos druid warrior", 45),
        Camp(227, 20, SubZone.FREMENNIK_SLAYER_DUNGEON, "Cave crawler", 40, 2,
            SlayerTaskType.CAVE_CRAWLER),
        Camp(239, 8, SubZone.FREMENNIK_SLAYER_DUNGEON, "Cave crawler", 40, slayerTask =
            SlayerTaskType.CAVE_CRAWLER),
        Camp(235, 5, SubZone.FREMENNIK_SLAYER_DUNGEON, "Cave crawler", 40, slayerTask =
            SlayerTaskType.CAVE_CRAWLER),
        Camp(221, 5, SubZone.FREMENNIK_SLAYER_DUNGEON, "Cave crawler", 40, slayerTask =
            SlayerTaskType.CAVE_CRAWLER),
        Camp(223, 5, SubZone.FREMENNIK_SLAYER_DUNGEON, "Cave crawler", 40, slayerTask =
            SlayerTaskType.CAVE_CRAWLER),
        Camp(225, 5, SubZone.FREMENNIK_SLAYER_DUNGEON, "Cave crawler", 40, slayerTask =
            SlayerTaskType.CAVE_CRAWLER),
        Camp(231, 5, SubZone.FREMENNIK_SLAYER_DUNGEON, "Cave crawler", 40, slayerTask =
            SlayerTaskType.CAVE_CRAWLER),
        Camp(225, 20, SubZone.FREMENNIK_SLAYER_DUNGEON, "Cockatrice", 50, 2,
            SlayerTaskType.COCKATRICE),
        Camp(239, 48, SubZone.FREMENNIK_SLAYER_DUNGEON, "Kurask", 100, 2,
            SlayerTaskType.KURASK),
        Camp(5974, 20, SubZone.FREMENNIK_SLAYER_DUNGEON, "Kurask", 100, 1,
            SlayerTaskType.KURASK)
    )

    /**
     * Maximum unsold stock for each gathered secondary before another profit outing is suppressed.
     */
    private val stockCaps = mapOf(
        221 to 100,
        223 to 100,
        225 to 250,
        227 to 300,
        231 to 100,
        235 to 100,
        237 to 250,
        239 to 200,
        5974 to 200
    )

    /**
     * Offers one drop-focused combat trip when its camp, assignment and owned melee equipment are ready.
     */
    fun candidate(bot: Bot, duration: Duration): NpcDropGatheringScript? {
        if (bot.preferences.isSkiller || bot.personality.type == PersonalityTemplateType.SKILLER) return null
        val readiness = CombatCoordinator.combatReadiness(bot, BotGearPurpose.MELEE)
        val assignment = bot.activeSlayerTask
        val ready = camps.filter { camp ->
            if (camp.minimum > readiness || camp.zone.entryBlocker(bot) != null ||
                !NpcCombatScript.campSuppliesOwned(bot, camp.zone) ||
                bot.totalItemAmount(camp.itemId) >= stockCaps.getValue(camp.itemId) ||
                bot.world.economy.getPrice(camp.itemId) <= 0) return@filter false
            val task = camp.slayerTask
            (task == null || assignment != null && assignment.task == task && assignment.remaining > 0 &&
                bot.slayer.level >= task.level) &&
                NpcCombatScript.slayerLoadout(bot, BotGearPurpose.MELEE, task) != null
        }
        val selected = ready.flatMap { camp -> List(camp.weight) { camp } }.randomOrNull() ?: return null
        val owned = bot.totalItemAmount(selected.itemId)
        val target = minOf(stockCaps.getValue(selected.itemId), owned + selected.batch)
        if (target <= owned) return null
        val task = selected.slayerTask
        return NpcDropGatheringScript(bot, selected.itemId, target, duration,
            mutableListOf(selected.zone), ImmutableSetMultimap.of(selected.zone, selected.npcName),
            slayerTask = task, slayerMaster = if (task == null) null else assignment?.assignee)
    }
}
