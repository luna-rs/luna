package game.content.barrows

import io.luna.game.model.def.VarpDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.mob.varp.PersistentVarpManager
import io.luna.game.model.mob.varp.Varp
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.random.Random

/**
 * Checks every maze layout and the dig/reset persistence boundary.
 *
 * @author lare96
 */
class BarrowsRunTest {
    @Test
    fun allLayoutsConnectEveryRoomWithExactlyOneEntranceExitAndChestDoor() {
        // Corridor endpoints from the diagram in barrows_catacombs.rs2, in A..P order.
        val edges = listOf("AC", "AG", "AD", "AB", "BE", "BC", "CF", "CI",
                           "DE", "EF", "DG", "EH", "FI", "GH", "HI", "GI")
        val layouts = HashSet<Int>()
        for (chamber in BarrowsChamber.entries) {
            for (exit in chamber.doors) {
                for (chest in BarrowsMaze.chestDoors) {
                    val value = BarrowsMaze.layout(-1, chamber, exit, chest)
                    layouts += value
                    assertEquals(listOf(chamber), BarrowsChamber.entries.filter { it.enabled.read(value) == 1 })
                    assertEquals(listOf(exit), chamber.doors.filter { it.locked.read(value) == 0 })
                    assertEquals(listOf(chest), BarrowsMaze.chestDoors.filter { it.locked.read(value) == 0 })
                    val outsideMask = ((1 shl 26) - (1 shl 6)).inv()
                    assertEquals(outsideMask, value and outsideMask)
                    val reachable = mutableSetOf(chamber.name.single())
                    repeat(9) {
                        for ((door, edge) in BarrowsDoor.entries.zip(edges)) {
                            if (door.locked.read(value) == 0 && edge.any { it in reachable }) {
                                reachable.addAll(edge.toList())
                            }
                        }
                    }
                    assertEquals(('A'..'I').toSet(), reachable, "$chamber/$exit/$chest")
                }
            }
        }
        assertEquals(64, layouts.size)
    }

    @Test
    fun digStartsRunOnceAndOnlyCollapseTriggersTheNextReset() {
        for (id in listOf(452, 453)) {
            if (VarpDefinition.ALL.get(id).isEmpty) VarpDefinition.ALL.storeDefinition(VarpDefinition(id, 0))
        }
        val player = mock(Player::class.java)
        val manager = PersistentVarpManager(player)
        `when`(player.varpManager).thenReturn(manager)
        `when`(player.cachedVarps).thenReturn(HashMap())
        val random = Random(688)
        BarrowsRun.enterCrypt(player, random)
        val first = BarrowsRun.state(player)
        assertTrue(first != 0)
        assertNotNull(BarrowsRun.tunnelBrother(player))
        assertNotNull(BarrowsRun.chamber(player))
        manager.setValue(PersistentVarp.BARROWS_KILLS, -1)
        clearInvocations(player)
        repeat(6) { BarrowsRun.enterCrypt(player, random) }
        assertEquals(first, BarrowsRun.state(player))
        assertEquals(-1, manager.getValue(PersistentVarp.BARROWS_KILLS))
        verify(player, never()).sendVarp(any(Varp::class.java))

        val saved = manager.toMap()
        val restored = PersistentVarpManager(player)
        restored.fromMap(saved)
        `when`(player.varpManager).thenReturn(restored)
        BarrowsRun.enterCrypt(player, random)
        assertEquals(saved, restored.toMap())
        restored.setValue(PersistentVarp.BARROWS, first or BarrowsRun.COLLAPSING_MASK or (3 shl 29))
        BarrowsRun.enterCrypt(player, random)
        assertEquals(0, restored.getValue(PersistentVarp.BARROWS_KILLS))
        assertEquals(0, BarrowsRun.state(player) and BarrowsRun.COLLAPSING_MASK)
        assertEquals(0, BarrowsVarbit.PUZZLE.read(BarrowsRun.state(player)))
        assertNotNull(BarrowsRun.chamber(player))
        verify(player, times(2)).sendVarp(any(Varp::class.java))
    }
}
