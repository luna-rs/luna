package api.bot.webwalk

import api.bot.webwalk.generate.BankSeed
import api.bot.webwalk.generate.GenerationReport
import api.bot.webwalk.generate.WebWalkLiveGenerator
import com.google.common.collect.ImmutableList
import engine.bank.Banking
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.mob.Npc
import io.luna.game.model.`object`.GameObject
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*

/**
 * Exercises anchored NPC-bank discovery without starting a world or substituting for cache collision coverage.
 *
 * @author lare96
 */
class NpcBankDiscoveryTest {

    private val anchor = Position(2352, 3166)
    private lateinit var world: World
    private lateinit var report: GenerationReport
    private var npcs: List<Npc> = emptyList()

    @BeforeEach fun setUp() {
        world = mock(World::class.java, RETURNS_DEEP_STUBS)
        report = GenerationReport()
        npcs = emptyList()
        `when`(world.npcs.iterator()).thenAnswer { npcs.iterator() }
        `when`(world.locator.findObjectsOnTile(any(), any())).thenReturn(emptySet())
        `when`(world.collisionManager.reached(any(), any(), same(Banking.BANKER_POLICY))).thenReturn(true)
    }

    private fun banker(id: Int = 2354, position: Position = anchor, actions: List<String> = listOf("Talk-to", "Bank")): Npc =
        mock(Npc::class.java, RETURNS_DEEP_STUBS).also { npc ->
            `when`(npc.id).thenReturn(id)
            `when`(npc.basePosition).thenReturn(position)
            `when`(npc.position).thenReturn(position)
            `when`(npc.state).thenReturn(EntityState.ACTIVE)
            `when`(npc.isAlive).thenReturn(true)
            `when`(npc.def().actions).thenReturn(ImmutableList.copyOf(actions))
        }

    private fun discover(position: Position = anchor, canStand: (Position) -> Boolean = { true }): BankSeed? =
        WebWalkLiveGenerator.bankSeed("tirannwn", position, report, canStand, setOf(2354, 2355), world)

    @ParameterizedTest
    @CsvSource("2354, 2352, 3166", "2355, 2354, 3166")
    fun tirannwnBankersProduceImmutableNpcSeeds(id: Int, x: Int, y: Int) {
        val position = Position(x, y)
        val npc = banker(id, position)
        npcs = listOf(npc)

        val seed = requireNotNull(discover(position))

        assertEquals(id, seed.npcId)
        assertEquals(position, seed.booth)
        assertEquals(4, seed.candidates.size)
        assertTrue(report.skipped.isEmpty())
        `when`(npc.position).thenReturn(position.translate(10, 0))
        assertEquals(position, seed.booth)
        assertTrue(seed.candidates.all { it.isWithinDistance(position, 1) })
    }

    @Test fun existingBankObjectsTakePrecedenceAndKeepTheirOriginalCardinalCandidates() {
        val booth = mock(GameObject::class.java)
        `when`(world.locator.findObjectsOnTile(any(), any())).thenReturn(setOf(booth))
        npcs = listOf(banker())

        val seed = requireNotNull(discover())

        assertNull(seed.npcId)
        assertEquals(listOf(anchor.translate(-1, 0), anchor.translate(0, -1),
            anchor.translate(0, 1), anchor.translate(1, 0)), seed.candidates)
        verify(world.collisionManager, never()).reached(any(), any(), any())
    }

    @ParameterizedTest
    @ValueSource(strings = ["unregistered", "inactive", "dead", "other_spawn", "moved"])
    fun unavailableOrUnrelatedNpcsAreNotMadeIntoBankDestinations(reason: String) {
        val npc = banker()
        when (reason) {
            "unregistered" -> `when`(npc.id).thenReturn(1234)
            "inactive" -> `when`(npc.state).thenReturn(EntityState.INACTIVE)
            "dead" -> `when`(npc.isAlive).thenReturn(false)
            "other_spawn" -> `when`(npc.basePosition).thenReturn(anchor.translate(1, 0))
            "moved" -> `when`(npc.position).thenReturn(anchor.translate(1, 0))
        }
        npcs = listOf(npc)

        assertNull(discover())

        assertTrue(report.skipped.single().contains("no bank object or active registered banker"))
    }

    @Test fun anAnchorWithoutAnObjectOrBankerIsReported() {
        assertNull(discover())
        assertTrue(report.skipped.single().contains("no bank object or active registered banker"))
    }

    @Test fun registeredBankersWithoutADirectBankActionAreDeliberatelyReported() {
        npcs = listOf(banker(actions = listOf("Talk-to")))

        assertNull(discover())

        assertTrue(report.skipped.single().contains("no direct Bank action"))
    }

    @Test fun npcCandidatesMustHaveBothAWalkableFloorAndNativeBankingReach() {
        val npc = banker()
        npcs = listOf(npc)
        val west = anchor.translate(-1, 0)
        val east = anchor.translate(1, 0)
        `when`(world.collisionManager.reached(any(), same(npc), same(Banking.BANKER_POLICY)))
            .thenAnswer { it.getArgument<Position>(0) == west || it.getArgument<Position>(0) == east }

        val seed = requireNotNull(discover(canStand = { it != west }))

        assertEquals(listOf(east), seed.candidates)
        verify(world.collisionManager, never()).reached(eq(west), same(npc), any())
    }

    @Test fun aCounterWithoutANativelyReachableCustomerStandIsReportedRatherThanBypassed() {
        val npc = banker()
        npcs = listOf(npc)
        `when`(world.collisionManager.reached(any(), same(npc), same(Banking.BANKER_POLICY))).thenReturn(false)

        assertNull(discover())

        assertTrue(report.skipped.single().contains("no reachable customer stand"))
        verify(world.collisionManager, times(4)).reached(any(), same(npc), same(Banking.BANKER_POLICY))
    }
}
