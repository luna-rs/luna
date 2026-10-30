package engine.combat.drops

import engine.bot.coordinator.skill.ScriptFixtures
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Checks shared jewel selection, native death context and unchanged direct item definitions.
 *
 * @author lare96
 */
class StaticNpcDropRollTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun initialize() {
            ScriptFixtures.initialize()
        }
    }

    private fun drop(id: Int, rarity: Double, quantity: String = "1", noted: Boolean = false,
                     shared: String? = null) = StaticNpcDrop(id, "", quantity, noted, rarity, 1, shared)

    private fun player(ring: Boolean = false, y: Int = 3200): Player =
        mock(Player::class.java, RETURNS_DEEP_STUBS).also {
            `when`(it.equipment.contains(2572)).thenReturn(ring)
            `when`(it.position).thenReturn(Position(3200, y))
        }

    @Test
    fun sharedJewelsCompeteWithDirectLootWhileGuaranteedBonesRemainIndependent() {
        val table = StaticNpcDropRoll(listOf(drop(526, 1.0), drop(995, 0.75),
                                             drop(-1, 0.25, shared = "JEWEL")))
        val entries = table.computeTable(null, null)
        assertEquals(0.75, entries.single { it.id == 995 }.chance)
        assertEquals(0.25, entries.filter { it.id != 526 && it.id != 995 }.sumOf { it.chance }, 1e-12)
        repeat(512) {
            val rolled = table.roll(null, null)
            assertEquals(1, rolled.count { it.id == 526 })
            assertTrue(rolled.size in 1..2)
            assertTrue(rolled.count { it.id != 526 } <= 1)
        }
    }

    @Test
    fun nativeKillerControlsRingAndUndergroundTalismanWithoutBoostingDirectLoot() {
        val table = StaticNpcDropRoll(listOf(drop(995, 0.75), drop(-1, 0.25, shared = "JEWEL")))
        val victim = mock(Npc::class.java)
        val killer = player(ring = true, y = 9500)
        val entries = table.computeTable(victim, killer)
        assertEquals(0.75, entries.single { it.id == 995 }.chance)
        assertEquals(0.25 * 32 / 65, entries.single { it.id == 1623 }.chance, 1e-12)
        assertTrue(entries.any { it.id == 1452 })
        assertFalse(entries.any { it.id == 1462 || it.id == -1 })
        assertEquals(1.0, entries.sumOf { it.chance }, 1e-12)

        val reversed = table.computeTable(killer, victim)
        assertEquals(0.25 * 32 / 128, reversed.single { it.id == 1623 }.chance, 1e-12)
        assertTrue(reversed.any { it.id == 1462 })
        assertTrue(reversed.any { it.id == -1 })
    }

    @Test
    fun surfaceKillerKeepsSurfaceTalismanAndPreviewIncludesBothLocations() {
        val table = StaticNpcDropRoll(listOf(drop(-1, 1.0, shared = "JEWEL")))
        val victim = mock(Npc::class.java)
        `when`(victim.position).thenReturn(Position(3200, 9500))
        val entries = table.computeTable(victim, player())
        assertTrue(entries.any { it.id == 1462 })
        assertFalse(entries.any { it.id == 1452 })
        assertTrue(table.computePossibleItems().map { it.id }.containsAll(listOf(1452, 1462)))
    }

    @Test
    fun quantitiesAndNumericNoteIdsKeepTheirConfiguredMeaning() {
        val table = StaticNpcDropRoll(listOf(drop(2364, 0.5, "2-4", noted = true),
                                             drop(995, 0.25, "25"),
                                             drop(-1, 0.25, shared = "JEWEL")))
        val entries = table.computeTable(null, null)
        assertEquals(2..4, entries.single { it.id == 2364 }.amount)
        assertEquals(25..25, entries.single { it.id == 995 }.amount)
        assertFalse(entries.any { it.id == 2363 })
    }

    @Test
    fun unsupportedSharedTableFailsInsteadOfSilentlyDroppingNothing() {
        assertThrows(IllegalArgumentException::class.java) {
            StaticNpcDropRoll(listOf(drop(-1, 0.25, shared = "UNKNOWN")))
        }
    }
}
