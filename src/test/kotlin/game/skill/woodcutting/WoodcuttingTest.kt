package game.skill.woodcutting

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Checks the population boundaries and integer rounding of Lost City's tree respawn adjustment.
 *
 * @author lare96
 */
class WoodcuttingTest {

    @Test
    fun populationReducesRespawnToHalfTheBaseAndCapsAtAFullWorld() {
        assertEquals(86, Woodcutting.scaleRespawn(86, 0))
        assertEquals(64, Woodcutting.scaleRespawn(86, 1000))
        assertEquals(43, Woodcutting.scaleRespawn(86, 2000))
        assertEquals(43, Woodcutting.scaleRespawn(86, 3000))
        assertEquals(86, Woodcutting.scaleRespawn(86, -1))
    }

    @Test
    fun populationNeverMakesAnyTreeRespawnMoreSlowly() {
        for (tree in game.skill.woodcutting.cutTree.Tree.values()) {
            for (base in tree.respawnTicks) {
                var previous = base
                for (players in 0..2000) {
                    val ticks = Woodcutting.scaleRespawn(base, players)
                    assertTrue(ticks in 1..previous, "${tree.name}: $players players")
                    previous = ticks
                }
                assertEquals(base / 2, previous)
            }
        }
    }
}
