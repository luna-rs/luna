package api.bot.zone

import com.google.common.collect.ImmutableSet
import io.luna.game.model.Entity
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.EnumSet

/**
 * Keeps Agility bots on their multi-plane courses without widening floor-specific subzones.
 *
 * @author lare96
 */
class AgilitySubZoneTest {

    private fun bot(position: Position): Bot {
        val bot = mock(Bot::class.java)
        Entity::class.java.getDeclaredField("position").apply {
            isAccessible = true
            set(bot, position)
        }
        var localZones = ImmutableSet.of<SubZone>()
        `when`(bot.position).thenReturn(position)
        `when`(bot.abs()).thenReturn(position)
        `when`(bot.subZones).thenReturn(EnumSet.noneOf(SubZone::class.java))
        `when`(bot.localSubZones).thenAnswer { localZones }
        doAnswer {
            localZones = it.getArgument(0)
            null
        }.`when`(bot).setLocalSubZones(any())
        return bot
    }

    private fun locate(position: Position): Bot = bot(position).also {
        SubZone.updateLocalSubZones(it, position.regionId)
        SubZone.updateSubZone(it)
    }

    @Test fun multiPlaneCoursesRetainMembershipOnEveryPlane() {
        for (zone in listOf(SubZone.GNOME_STRONGHOLD_AGILITY, SubZone.BARBARIAN_OUTPOST_AGILITY)) {
            for (plane in 0..3) {
                val position = zone.inside.setZ(plane)
                val bot = locate(position)
                assertTrue(zone.area.contains(position), "$zone must contain $position")
                assertTrue(zone in bot.localSubZones, "$zone must be indexed at $position")
                assertTrue(zone in bot.subZones, "$zone must retain membership at $position")
            }
        }
    }

    @Test fun courseMembershipRejectsDistantTilesOnEveryPlane() {
        val positions = listOf(
            SubZone.GNOME_STRONGHOLD_AGILITY to Position(2556, 3420),
            SubZone.BARBARIAN_OUTPOST_AGILITY to Position(2630, 3546)
        )
        for ((zone, position) in positions) {
            for (plane in 0..3) {
                val outside = position.setZ(plane)
                val bot = locate(outside)
                assertFalse(zone.area.contains(outside), "$zone must exclude $outside")
                assertFalse(zone in bot.subZones, "$zone must exclude $outside")
                assertFalse(zone.isWithinDistance(bot, 64), "$outside must require course travel")
            }
        }
    }

    @Test fun courseFloorsAreNearbyBeforeTheMembershipCacheRefreshes() {
        val positions = listOf(
            SubZone.GNOME_STRONGHOLD_AGILITY to Position(2477, 3420),
            SubZone.BARBARIAN_OUTPOST_AGILITY to Position(2537, 3546)
        )
        for ((zone, position) in positions) {
            for (plane in 0..3) {
                val onCourse = position.setZ(plane)
                val bot = bot(onCourse)
                assertTrue(bot.subZones.isEmpty())
                assertTrue(zone.isWithinDistance(bot, 64), "$onCourse is already on $zone")
            }
        }
    }

    @Test fun multiPlaneCourseGeometryKeepsConcreteTravelCoordinates() {
        for (zone in listOf(SubZone.GNOME_STRONGHOLD_AGILITY, SubZone.BARBARIAN_OUTPOST_AGILITY)) {
            assertEquals(0, zone.inside.z)
            assertEquals(-1, zone.area.plane)
            assertEquals(0, zone.area.anchorPosition.z)
            assertEquals(0, zone.area.centerPosition.z)
            for (corner in listOf(zone.area.southWest, zone.area.southEast,
                                  zone.area.northWest, zone.area.northEast)) {
                assertEquals(0, corner.z)
            }
            assertTrue(zone.area.contains(zone.inside))
            assertTrue(zone.area.tileRadius < 64)
        }
    }

    @Test fun theSurfaceOnlyWildernessCourseKeepsItsFloorRestriction() {
        val zone = SubZone.WILDERNESS_AGILITY
        assertEquals(0, zone.area.plane)
        for (plane in 0..3) {
            val position = Position(3004, 3950, plane)
            val bot = locate(position)
            assertEquals(plane == 0, zone.area.contains(position))
            assertEquals(plane == 0, zone in bot.subZones)
            assertEquals(plane == 0, zone.isWithinDistance(bot, 64))
        }
    }

    @Test fun unrelatedSubzonesStillRequireTheirOwnFloor() {
        val zone = SubZone.SLAYER_TOWER_FIRST_FLOOR
        for (plane in 0..3) {
            val bot = locate(Position(3434, 3563, plane))
            assertEquals(plane == 1, zone in bot.subZones)
            assertEquals(plane == 1, zone.isWithinDistance(bot, 64))
            assertEquals(plane == 2, SubZone.SLAYER_TOWER_SECOND_FLOOR in bot.subZones)
        }
    }
}
