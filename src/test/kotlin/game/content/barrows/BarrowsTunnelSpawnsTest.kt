package game.content.barrows

import com.google.common.collect.ImmutableList
import io.luna.LunaContext
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Locatable
import io.luna.game.model.Position
import io.luna.game.model.def.NpcCombatDefinition
import io.luna.game.model.def.NpcDefinition
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.attr.AttributeMap
import io.luna.game.model.mob.combat.CombatRestriction
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.mob.varp.PersistentVarpManager
import io.luna.game.task.Task
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.random.Random

/**
 * Checks exact encounter weights, live spawn limits, brother eligibility and temporary NPC lifetime.
 *
 * @author lare96
 */
class BarrowsTunnelSpawnsTest {
    private val context = mock(LunaContext::class.java, RETURNS_DEEP_STUBS)
    private val world = context.world
    private val player = mock(Player::class.java, RETURNS_DEEP_STUBS)
    private val manager = PersistentVarpManager(player)
    private val timers = mutableListOf<Task>()
    private val zero = object : Random() { override fun nextBits(bitCount: Int): Int = 0 }

    @BeforeEach
    fun setup() {
        `when`(player.context).thenReturn(context)
        `when`(player.world).thenReturn(world)
        `when`(player.position).thenReturn(Position(3550, 9690))
        `when`(player.state).thenReturn(EntityState.ACTIVE)
        `when`(player.isAlive).thenReturn(true)
        `when`(player.attributes()).thenReturn(AttributeMap())
        `when`(player.varpManager).thenReturn(manager)
        manager.setValue(PersistentVarp.BARROWS, 64)
        for (id in (2025..2037)) {
            if (NpcDefinition.ALL.get(id).isEmpty) {
                val definition = mock(NpcDefinition::class.java)
                `when`(definition.id()).thenReturn(id)
                `when`(definition.size).thenReturn(1)
                `when`(definition.actions).thenReturn(ImmutableList.of("Attack"))
                NpcDefinition.ALL.storeDefinition(definition)
            }
            if (NpcCombatDefinition.ALL.get(id).isEmpty) {
                val definition = mock(NpcCombatDefinition::class.java)
                `when`(definition.id()).thenReturn(id)
                `when`(definition.hitpoints).thenReturn(100)
                NpcCombatDefinition.ALL.storeDefinition(definition)
            }
        }
        `when`(world.collisionManager.traversable(any(Position::class.java), eq(EntityType.NPC),
                                                any(Direction::class.java))).thenReturn(true)
        val npcs = world.npcs
        var index = 1
        doAnswer {
            val npc = it.getArgument<Npc>(0)
            npc.index = index++
            npc.state = EntityState.ACTIVE
            true
        }.`when`(npcs).add(any(Npc::class.java))
        doAnswer { it.getArgument<Npc>(0).state = EntityState.INACTIVE; true }
            .`when`(npcs).remove(any(Npc::class.java))
        doAnswer { timers += it.getArgument<Task>(0); null }.`when`(world).schedule(any(Task::class.java))
    }

    @Test
    fun weightsExhaustAll128PossibleRolls() {
        assertEquals(mapOf(null to 12, BarrowsMonster.ARMED_SKELETON to 20,
                           BarrowsMonster.UNARMED_SKELETON to 32, BarrowsMonster.BLOODWORM to 32,
                           BarrowsMonster.CRYPT_RAT to 32),
                     (0..127).groupingBy { BarrowsTunnelSpawns.monsterForRoll(it) }.eachCount())
    }

    @Test
    fun onlyUndefeatedBrothersSpawnAndExistingEncountersAreReused() {
        for (brother in BarrowsBrother.entries) {
            manager.setValue(PersistentVarp.BARROWS_KILLS, brother.killed.write(63, 0))
            val npc = BarrowsTunnelSpawns.spawn(player, zero) as BarrowsBrotherNpc
            assertEquals(brother, npc.brother)
            assertSame(npc, BarrowsTunnelSpawns.spawn(player, zero))
            BarrowsEncounters.clear(player)
        }
    }

    @Test
    fun noRemainingBrothersFallsBackToAnUnownedRatWithNoRespawnAnd500TickLifetime() {
        manager.setValue(PersistentVarp.BARROWS_KILLS, 63)
        val npc = BarrowsTunnelSpawns.spawn(player, zero)!!
        assertEquals(2032, npc.id)
        assertEquals(-1, npc.respawnTicks)
        assertFalse(npc is CombatRestriction)
        assertSame(player, npc.combat.target)
        val timer = timers.single()
        assertEquals(500, timer.delay)
        timer.javaClass.getDeclaredMethod("execute").apply { isAccessible = true }.invoke(timer)
        assertEquals(EntityState.INACTIVE, npc.state)
        assertEquals(63, manager.getValue(PersistentVarp.BARROWS_KILLS))
    }

    @Test
    fun populationCapAndBlockedTilesPreventSpawnsWithoutCreditingKills() {
        manager.setValue(PersistentVarp.BARROWS_KILLS, 63)
        val nearby = (0..11).map { mock(Npc::class.java) }.toSet()
        `when`(world.locator.findNpcs(any(Locatable::class.java), eq(5), any())).thenReturn(nearby)
        assertNull(BarrowsTunnelSpawns.spawn(player, zero))
        `when`(world.locator.findNpcs(any(Locatable::class.java), eq(5), any())).thenReturn(nearby.take(11).toSet())
        assertNotNull(BarrowsTunnelSpawns.spawn(player, zero))
        `when`(world.collisionManager.isBlocked(any(Position::class.java), eq(false))).thenReturn(true)
        assertNull(BarrowsTunnelSpawns.spawn(player, zero))
        assertEquals(63, manager.getValue(PersistentVarp.BARROWS_KILLS))
    }
}
