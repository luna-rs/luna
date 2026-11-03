package api.bot.webwalk.generate

import api.bot.webwalk.model.Requirements
import api.bot.webwalk.walk.crossing.NativeAccessCrossing
import game.obj.entrances.PortPhasmatysBarrier
import game.obj.doors.MournerHeadquartersDoors
import game.obj.doors.ArdougneWallDoors
import game.skill.agility.GnomeStrongholdRocks

/**
 * Native city, headquarters and directed Stronghold passages shared with player interactions.
 * Only the rock shortcut checks current Agility; the other passages have free temporary quest admission.
 *
 * @author lare96
 */
object NativeAccessCrossingSpec {

    val BARRIERS = CrossingSpec(NativeAccessCrossing.ID, setOf(PortPhasmatysBarrier.ID), cost = 4,
        placements = PortPhasmatysBarrier.PASSAGES.map {
            CrossingPlacement(PortPhasmatysBarrier.ID, it.position, it.outside, it.inside)
        })

    val ROCKS = CrossingSpec(NativeAccessCrossing.ID, GnomeStrongholdRocks.PASSAGES.map { it.id }.toSet(),
        requirements = Requirements(flags = setOf(NativeAccessCrossing.ROCKS_READY)), bidirectional = false, cost = 6,
        placements = GnomeStrongholdRocks.PASSAGES.map { CrossingPlacement(it.id, it.position, it.from, it.to) })

    val HEADQUARTERS = CrossingSpec(NativeAccessCrossing.ID, setOf(MournerHeadquartersDoors.ID), cost = 4,
        placements = MournerHeadquartersDoors.PASSAGES.map {
            CrossingPlacement(MournerHeadquartersDoors.ID, it.position, it.from, it.to)
        })

    val CITY_WALLS = CrossingSpec(NativeAccessCrossing.ID, ArdougneWallDoors.IDS, bidirectional = false, cost = 5,
        placements = ArdougneWallDoors.PASSAGES.map { CrossingPlacement(it.id, it.position, it.from, it.to) })

    val ALL = listOf(BARRIERS, ROCKS, HEADQUARTERS, CITY_WALLS)
}
