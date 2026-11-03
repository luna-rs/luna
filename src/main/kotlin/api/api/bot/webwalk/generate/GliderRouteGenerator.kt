package api.bot.webwalk.generate

import api.bot.webwalk.model.EdgeType
import api.bot.webwalk.walk.crossing.GliderCrossing
import api.predef.world
import engine.obj.LadderDestination
import game.content.gnomeGliders.GliderSite
import io.luna.game.model.Position
import io.luna.game.model.World

/**
 * Connects loaded Gnome Air pilots with the directed routes offered by the native glider map.
 * The Digsite crash has no return flight. Pilot and legal landing evidence is required for each generated edge;
 * absent NPCs or missing terrain are reported rather than replaced by synthetic travel endpoints.
 * Native deferred quest-admission TODOs remain owned by the player glider handlers.
 *
 * @author lare96
 */
object GliderRouteGenerator {
    /**
     * Matches GnomeGliders.select without assuming that every pair of glider sites is connected.
     */
    internal fun flies(from: GliderSite, to: GliderSite): Boolean = from != to && when {
        from == GliderSite.TA_QUIR_PRIW -> to.outbound != null
        to == GliderSite.TA_QUIR_PRIW -> from.inbound != null
        else -> false
    }

    /**
     * Generates NPC crossings from actual spawn anchors, with one legal stand from each native landing radius.
     */
    fun gather(report: GenerationReport, gameWorld: World = world,
               canLand: (Position) -> Boolean = LadderDestination::canLand): List<GeneratedObstacle> {
        val pilots = gameWorld.npcs.filter { it.id == 170 || it.id == 1800 }
        fun landing(site: GliderSite): Position? = buildList {
            add(site.landing)
            for (x in -1..1) for (y in -1..1) if (x != 0 || y != 0) add(site.landing.translate(x, y))
        }.firstOrNull(canLand)
        return buildList {
            for (from in GliderSite.entries) for (to in GliderSite.entries) {
                if (!flies(from, to)) continue
                val pilot = pilots.firstOrNull {
                    GliderSite.near(it.basePosition) == from &&
                        (if (from == GliderSite.LEMANTOLLY_UNDRI) it.id == 1800 else it.id == 170)
                }
                val departure = landing(from)
                val arrival = landing(to)
                if (pilot == null || departure == null || arrival == null) {
                    report.skipped += "glider $from to $to: missing pilot or legal landing"
                    continue
                }
                add(GeneratedObstacle(EdgeType.CROSSING, null, pilot.basePosition, departure, arrival,
                    handler = GliderCrossing.ID, cost = 12, bidirectional = false, npcId = pilot.id))
                report.count("Gnome glider flights")
            }
        }
    }
}
