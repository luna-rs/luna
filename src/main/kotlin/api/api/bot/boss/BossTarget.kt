package api.bot.boss

import api.bot.zone.SubZone

/**
 * Supported shared boss destinations and their separate solo and group admission thresholds.
 * Readiness supplements real equipment, Hitpoints and supplies rather than replacing those checks.
 *
 * @property zone Native encounter footprint used for travel and local attendance.
 * @property soloReadiness Minimum combat readiness for an independent outing.
 * @property groupReadiness Minimum combat readiness for an outing supported by nearby bots.
 * @property bossNpcNames Exact configured NPC names for the encounter.
 * @author lare96
 */
enum class BossTarget(val zone: SubZone, val soloReadiness: Int, val groupReadiness: Int,
                      val bossNpcNames: Set<String>) {
    MOLE(SubZone.GIANT_MOLE_LAIR, 75, 55, setOf("Giant Mole")),
    KBD(SubZone.KBD_LAIR, 85, 65, setOf("King black dragon")),
    CHAOS_ELEMENTAL(SubZone.CHAOS_ELEMENTAL_WILDERNESS, 95, 80, setOf("Chaos Elemental")),
    KALPHITE_QUEEN(SubZone.KALPHITE_QUEEN, 110, 90, setOf("Kalphite Queen"))
}
