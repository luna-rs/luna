package api.bot.webwalk

import api.bot.webwalk.generate.WebWalkDoorGenerator
import api.bot.webwalk.model.EdgeType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Checks that the Paterdomus members fence and temple doors generate usable native crossings.
 *
 * @author lare96
 */
class PaterdomusGateTest {

    @Test
    fun membersFenceAndTempleGenerateEdgesAtEveryCachedPlacement() {
        val directory = Path.of("data/game/world/doors")
        val closedIds = setOf(1598, 1599, 3489, 3490)
        val doors = WebWalkDoorGenerator.readDoors(directory).filter { it.closed in closedIds }.associateBy { it.closed }
        assertEquals(closedIds, doors.keys)
        assertEquals(1552, doors.getValue(1598).open)
        assertEquals(1556, doors.getValue(1599).open)
        assertEquals(3491, doors.getValue(3489).open)
        assertEquals(3492, doors.getValue(3490).open)
        doors.values.forEach {
            assertEquals(it.closed == 1598 || it.closed == 3489, it.left)
            val gate = it.closed == 1598 || it.closed == 1599
            assertEquals(if (gate) EdgeType.GATE else EdgeType.DOOR, it.type)
            assertEquals(if (gate) WebWalkDoorGenerator.Swing.GATE else WebWalkDoorGenerator.Swing.DOUBLE, it.swing)
        }

        assumeTrue(Files.isRegularFile(Path.of("data/game/cache/main_file_cache.dat")))
        val table = WebWalkDoorGenerator.loadMap()
        val placements = table.objectSet.filter { it.objectId in closedIds }
        assertEquals(closedIds, placements.map { it.objectId }.toSet())
        val ids = WebWalkDoorGenerator.readClosedIds(directory).filterKeys { it in closedIds }
        val (crossings, report) = WebWalkDoorGenerator.generate(table, ids)
        assertEquals(placements.size, crossings.size, report.skipped.joinToString("\n"))
        for (placement in placements) {
            val crossing = crossings.single { it.objectId == placement.objectId && it.pos == placement.position }
            assertEquals(doors.getValue(placement.objectId).type, crossing.type)
            assertEquals(placement.position.z, crossing.from.z)
            assertEquals(crossing.from.z, crossing.to.z)
            assertEquals(1, crossing.from.computeLongestDistance(crossing.to))
            assertTrue(crossing.from.x == crossing.to.x || crossing.from.y == crossing.to.y)
        }
    }
}
