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
 * Keeps native Agility floors and failure pits inside their course without widening ordinary subzones.
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

    @Test fun nativeCourseFloorsRetainTheirMembership() {
        val positions = listOf(
            SubZone.GNOME_STRONGHOLD_AGILITY to Position(2474, 3437),
            SubZone.GNOME_STRONGHOLD_AGILITY to Position(2473, 3423, 1),
            SubZone.GNOME_STRONGHOLD_AGILITY to Position(2477, 3420, 2),
            SubZone.BARBARIAN_OUTPOST_AGILITY to Position(2551, 3549),
            SubZone.BARBARIAN_OUTPOST_AGILITY to Position(2537, 3546, 1),
            SubZone.WILDERNESS_AGILITY to Position(3004, 3950)
        )
        for ((zone, position) in positions) {
            val bot = locate(position)
            assertTrue(zone in bot.localSubZones, "$zone must be indexed at $position")
            assertTrue(zone in bot.subZones, "$zone must contain $position")
        }
    }

    @Test fun courseMembershipRejectsUnsupportedFloorsAndDistantTiles() {
        val positions = listOf(
            SubZone.GNOME_STRONGHOLD_AGILITY to Position(2477, 3420, 3),
            SubZone.GNOME_STRONGHOLD_AGILITY to Position(2556, 3420, 2),
            SubZone.BARBARIAN_OUTPOST_AGILITY to Position(2537, 3546, 2),
            SubZone.WILDERNESS_AGILITY to Position(3004, 3950, 1)
        )
        for ((zone, position) in positions) {
            val bot = locate(position)
            assertFalse(zone in bot.subZones, "$zone must exclude $position")
            assertFalse(zone.isWithinDistance(bot, 64), "$position must require course travel")
        }
    }

    @Test fun upperCourseFloorsAreNearbyBeforeTheMembershipCacheRefreshes() {
        val positions = listOf(
            SubZone.GNOME_STRONGHOLD_AGILITY to Position(2473, 3423, 1),
            SubZone.GNOME_STRONGHOLD_AGILITY to Position(2477, 3420, 2),
            SubZone.BARBARIAN_OUTPOST_AGILITY to Position(2537, 3546, 1)
        )
        for ((zone, position) in positions) {
            val bot = bot(position)
            assertTrue(bot.subZones.isEmpty())
            assertTrue(zone.isWithinDistance(bot, 64), "$position is already on $zone")
        }
    }

    @Test fun courseSearchAreasAndTravelAnchorsStayOnTheSurface() {
        for (zone in listOf(SubZone.GNOME_STRONGHOLD_AGILITY,
                            SubZone.BARBARIAN_OUTPOST_AGILITY, SubZone.WILDERNESS_AGILITY)) {
            assertEquals(0, zone.inside.z)
            assertEquals(0, zone.area.plane)
            assertTrue(zone.area.contains(zone.inside))
            assertFalse(zone.area.contains(zone.inside.setZ(1)))
            assertTrue(zone.area.tileRadius < 64)
        }
    }

    @Test fun nativeFailureLandingsAndPitLaddersRemainIndexedInTheirCourses() {
        val positions = listOf(
            SubZone.BARBARIAN_OUTPOST_AGILITY to Position(2551, 9948),
            SubZone.BARBARIAN_OUTPOST_AGILITY to Position(2547, 9951),
            SubZone.WILDERNESS_AGILITY to Position(3004, 10357),
            SubZone.WILDERNESS_AGILITY to Position(2998, 10345),
            SubZone.WILDERNESS_AGILITY to Position(3005, 10363)
        )
        for ((zone, position) in positions) {
            val bot = locate(position)
            assertTrue(zone in bot.localSubZones, "$zone must be indexed in its pit region")
            assertTrue(zone in bot.subZones, "$zone must contain its native pit tile $position")
            assertTrue(zone.isWithinDistance(bot, 64), "Pit recovery must precede course travel")
        }
    }

    @Test fun pitsExcludeOtherFloorsAndTilesBeyondTheirBounds() {
        val positions = listOf(
            SubZone.BARBARIAN_OUTPOST_AGILITY to Position(2547, 9951, 1),
            SubZone.BARBARIAN_OUTPOST_AGILITY to Position(2556, 9951),
            SubZone.BARBARIAN_OUTPOST_AGILITY to Position(2547, 9956),
            SubZone.WILDERNESS_AGILITY to Position(3005, 10363, 1),
            SubZone.WILDERNESS_AGILITY to Position(3011, 10363),
            SubZone.WILDERNESS_AGILITY to Position(3005, 10369)
        )
        for ((zone, position) in positions) {
            val bot = locate(position)
            assertFalse(zone in bot.subZones, "$zone must exclude $position")
            assertFalse(zone.isWithinDistance(bot, 64))
        }
    }

    @Test fun unrelatedSubzonesStillRequireTheirOwnFloor() {
        val zone = SubZone.SLAYER_TOWER_FIRST_FLOOR
        val firstFloor = locate(Position(3434, 3563, 1))
        assertTrue(zone in firstFloor.subZones)
        assertTrue(zone.isWithinDistance(firstFloor, 64))
        val secondFloor = locate(Position(3434, 3563, 2))
        assertFalse(zone in secondFloor.subZones)
        assertFalse(zone.isWithinDistance(secondFloor, 64))
        assertTrue(SubZone.SLAYER_TOWER_SECOND_FLOOR in secondFloor.subZones)
    }
}
