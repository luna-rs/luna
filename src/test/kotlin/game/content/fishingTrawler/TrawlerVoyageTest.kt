package game.content.fishingTrawler

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests boat failure, repairs, timing and reward accounting independently of map loading.
 *
 * @author lare96
 */
class TrawlerVoyageTest {
    @Test
    fun `unattended leaks flood then sink the boat and prohibit later actions`() {
        val boat = TrawlerVoyage { 0 }
        boat.tick(1)
        assertEquals(4, (0..15).count(boat::leaking))
        repeat(30) { boat.tick(1) }
        assertTrue(boat.flooded)
        assertTrue(boat.sunk)
        assertTrue(boat.finished)
        assertFalse(boat.repairHull(0))
        assertFalse(boat.repairNet())
        assertFalse(boat.bail())
        assertEquals(0, boat.catch)
    }

    @Test
    fun `repair cannot consume a second supply for the same leak`() {
        val boat = TrawlerVoyage { 0 }
        boat.tick(2)
        assertEquals(7, (0..15).count(boat::leaking))
        assertTrue(boat.repairHull(0))
        assertFalse(boat.repairHull(0))
        assertFalse(boat.repairHull(-1))
        assertTrue(boat.repairNet())
        assertFalse(boat.repairNet())
    }

    @Test
    fun `successful voyage ends at two hundred control cycles with equal catch shares`() {
        val boat = TrawlerVoyage { bound -> if (bound == 10) 0 else bound - 1 }
        repeat(199) { boat.tick(2) }
        assertFalse(boat.finished)
        assertEquals(398, boat.catch)
        boat.tick(2)
        assertTrue(boat.finished)
        assertFalse(boat.sunk)
        assertEquals(199, boat.catch / 2)
        boat.tick(2)
        assertEquals(398, boat.catch)
    }

    @Test
    fun `empty boat stops without processing or rewards`() {
        val boat = TrawlerVoyage { error("No random roll expected") }
        boat.tick(0)
        assertTrue(boat.finished)
        assertEquals(0, boat.cycles)
        assertEquals(0, boat.catch)
    }
}
