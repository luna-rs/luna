package api.bot.webwalk.generate

import api.bot.webwalk.model.Requirements
import api.bot.webwalk.walk.crossing.NativeAccessCrossing
import game.obj.entrances.PortPhasmatysBarrier
import game.obj.entrances.IceQueenRockSlide
import game.obj.doors.MournerHeadquartersDoors
import game.obj.doors.ArdougneWallDoors
import game.skill.agility.GnomeStrongholdRocks

/**
 * Native city, headquarters and directed Stronghold passages shared with player interactions.
 * The Stronghold shortcut checks Agility, and White Wolf Mountain checks Mining and a carried pickaxe.
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

    val ICE_QUEEN_ROCKSLIDE = CrossingSpec(NativeAccessCrossing.ID, setOf(IceQueenRockSlide.ID),
        requirements = Requirements(flags = setOf(NativeAccessCrossing.ICE_QUEEN_ROCKSLIDE_READY)), cost = 5,
        placements = IceQueenRockSlide.LANES.map { (west, east) ->
            CrossingPlacement(IceQueenRockSlide.ID, IceQueenRockSlide.POSITION, west, east)
        })

    val ALL = listOf(BARRIERS, ROCKS, HEADQUARTERS, CITY_WALLS, ICE_QUEEN_ROCKSLIDE)
}
