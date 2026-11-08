package game.obj.resource

import api.bot.zone.SubZone
import io.luna.game.model.mob.bot.Bot

/**
 * Activity areas covering every configured persistent Zamorak wine source. Collection and combat learning share
 * these footprints, while the native subzones retain their entrance requirements and banking parents.
 *
 * @author lare96
 */
object ZamorakWineSources {

    /**
     * All nine source groups from the configured ground-item spawns.
     */
    val zones = listOf(
        SubZone.NORTH_FALADOR_CHAOS_TEMPLE,
        SubZone.SOUTH_ARDOUGNE_ZAMORAK_MONKS,
        SubZone.CHAOS_DRUID_TOWER,
        SubZone.TAVERLEY_DUNGEON_CHAOS_DRUIDS,
        SubZone.PATERDOMUS_ZAMORAK_MONKS,
        SubZone.TAVERLEY_DUNGEON_ZAMORAK_MONKS,
        SubZone.YANILLE_DUNGEON_ZAMORAK_WINE,
        SubZone.LVL_12_WILDERNESS_CHAOS_TEMPLE,
        SubZone.LVL_40_WILDERNESS_CHAOS_TEMPLE)

    /**
     * Dangerous sources bank in Edgeville, so their banking parent alone does not establish Wilderness risk.
     */
    val wildernessZones = setOf(SubZone.LVL_12_WILDERNESS_CHAOS_TEMPLE,
        SubZone.LVL_40_WILDERNESS_CHAOS_TEMPLE)

    /**
     * Checks whether the source requires the activity's Wilderness confidence and risk policy.
     */
    fun isWilderness(zone: SubZone): Boolean = zone in wildernessZones

    /**
     * Applies the source's existing entrance checks before an activity selects it.
     */
    fun canUse(bot: Bot, zone: SubZone): Boolean = zone in zones && zone.entryBlocker(bot) == null
}
