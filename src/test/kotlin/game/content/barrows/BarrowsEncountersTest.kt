package game.content.barrows

import com.google.common.collect.ImmutableList
import io.luna.LunaContext
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.EntityType
import io.luna.game.model.Position
import io.luna.game.model.def.NpcCombatDefinition
import io.luna.game.model.def.NpcCombatMovement
import io.luna.game.model.def.NpcDefinition
import io.luna.game.model.def.VarpDefinition
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.attr.AttributeMap
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.mob.varp.PersistentVarpManager
import io.luna.game.task.Task
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Uses real brother NPCs with a controlled world to check spawn ownership, expiry and kill credit.
 *
 * @author lare96
 */
class BarrowsEncountersTest {
    private val context = mock(LunaContext::class.java, RETURNS_DEEP_STUBS)
    private val world = context.world
    private val tasks = mutableListOf<Task>()
    private lateinit var owner: Player

    private fun player(): Player {
        val player = mock(Player::class.java, RETURNS_DEEP_STUBS)
        `when`(player.context).thenReturn(context)
        `when`(player.world).thenReturn(world)
        `when`(player.attributes()).thenReturn(AttributeMap())
        `when`(player.varpManager).thenReturn(PersistentVarpManager(player))
        `when`(player.cachedVarps).thenReturn(HashMap())
        `when`(player.position).thenReturn(Position(3555, 9697, 3))
        `when`(player.state).thenReturn(EntityState.ACTIVE)
        `when`(player.isAlive).thenReturn(true)
        return player
    }

    @BeforeEach
    fun setup() {
        owner = player()
        for (brother in BarrowsBrother.entries) {
            if (NpcDefinition.ALL.get(brother.npcId).isEmpty) {
                val definition = mock(NpcDefinition::class.java)
                `when`(definition.id()).thenReturn(brother.npcId)
                `when`(definition.size).thenReturn(1)
                `when`(definition.actions).thenReturn(ImmutableList.of("Attack"))
                NpcDefinition.ALL.storeDefinition(definition)
            }
            if (NpcCombatDefinition.ALL.get(brother.npcId).isEmpty) {
                val definition = mock(NpcCombatDefinition::class.java)
                `when`(definition.id()).thenReturn(brother.npcId)
                `when`(definition.hitpoints).thenReturn(100)
                `when`(definition.movement).thenReturn(NpcCombatMovement.DEFAULT)
                NpcCombatDefinition.ALL.storeDefinition(definition)
            }
        }
        if (VarpDefinition.ALL.get(453).isEmpty) VarpDefinition.ALL.storeDefinition(VarpDefinition(453, 0))
        `when`(world.collisionManager.traversable(any(Position::class.java), eq(EntityType.NPC),
                                                any(Direction::class.java))).thenReturn(true)
        var index = 1
        val npcs = world.npcs
        doAnswer {
            val npc = it.getArgument<Npc>(0)
            npc.index = index++
            npc.state = EntityState.ACTIVE
            true
        }.`when`(npcs).add(any(Npc::class.java))
        doAnswer {
            val npc = it.getArgument<Npc>(0)
            if (npc.state == EntityState.ACTIVE) npc.state = EntityState.INACTIVE
            true
        }.`when`(npcs).remove(any(Npc::class.java))
        doAnswer { tasks += it.getArgument<Task>(0); null }.`when`(world).schedule(any(Task::class.java))
    }

    private fun tick(task: Task) {
        task.javaClass.getDeclaredMethod("execute").apply { isAccessible = true }.invoke(task)
    }

    @Test
    fun repeatedSearchesReuseTheBrotherButOtherPlayersGetTheirOwnEncounter() {
        val first = BarrowsEncounters.spawn(owner, BarrowsBrother.AHRIM)!!
        assertSame(first, BarrowsEncounters.spawn(owner, BarrowsBrother.AHRIM))
        val stranger = player()
        val second = BarrowsEncounters.spawn(stranger, BarrowsBrother.AHRIM)!!
        assertNotSame(first, second)
        assertTrue(first.canFight(owner))
        assertFalse(first.canFight(stranger))
        assertFalse(second.canFight(owner))
        assertEquals(-1, first.respawnTicks)
        assertNull(first.aggressionProfile)
        assertSame(owner, first.combat.target)
        assertTrue(first.position.isWithinDistance(owner.position, 2))
    }

    @Test
    fun sixBrotherKillsCreditExactlySixFlagsAnd656ScoreAndPreventRespawns() {
        for (brother in BarrowsBrother.entries) {
            val npc = BarrowsEncounters.spawn(owner, brother)!!
            BarrowsEncounters.defeated(npc, owner)
            assertEquals(EntityState.INACTIVE, npc.state)
            assertFalse(BarrowsProgress.recordBrotherKill(owner, brother))
            assertNull(BarrowsEncounters.spawn(owner, brother))
        }
        val value = owner.varpManager.getValue(PersistentVarp.BARROWS_KILLS)
        assertEquals(6, BarrowsVarbit.KILL_COUNT.read(value))
        assertEquals(656, BarrowsVarbit.MONSTER_SCORE.read(value))
        assertEquals(6, BarrowsProgress.killedBrothers(owner).size)
    }

    @Test
    fun anotherPlayerCannotReceiveOrGiveOwnerKillCredit() {
        val npc = BarrowsEncounters.spawn(owner, BarrowsBrother.AHRIM)!!
        val stranger = player()
        BarrowsEncounters.defeated(npc, stranger)
        assertFalse(BarrowsProgress.isKilled(owner, BarrowsBrother.AHRIM))
        assertFalse(BarrowsProgress.isKilled(stranger, BarrowsBrother.AHRIM))
    }

    @Test
    fun timeoutLogoutDeathAndPlaneChangeRemoveWithoutCredit() {
        val first = BarrowsEncounters.spawn(owner, BarrowsBrother.AHRIM)!!
        val timer = tasks.last()
        repeat(999) { tick(timer) }
        assertEquals(EntityState.ACTIVE, first.state)
        tick(timer)
        assertEquals(EntityState.INACTIVE, first.state)
        verify(owner).sendMessage("We'll finish this later...")
        for (reason in 0..2) {
            owner = player()
            val npc = BarrowsEncounters.spawn(owner, BarrowsBrother.AHRIM)!!
            when (reason) {
                0 -> `when`(owner.state).thenReturn(EntityState.INACTIVE)
                1 -> `when`(owner.isAlive).thenReturn(false)
                2 -> `when`(owner.position).thenReturn(Position(3555, 9697, 0))
            }
            tick(tasks.last())
            assertEquals(EntityState.INACTIVE, npc.state)
            assertFalse(BarrowsProgress.isKilled(owner, BarrowsBrother.AHRIM))
        }
    }

    @Test
    fun failedRegistrationAndBlockedTilesDoNotLeaveAnEncounterReference() {
        val npcs = world.npcs
        doReturn(false).`when`(npcs).add(any(Npc::class.java))
        assertNull(BarrowsEncounters.spawn(owner, BarrowsBrother.AHRIM))
        assertNull(BarrowsEncounters.spawn(owner, BarrowsBrother.AHRIM))
        verify(world.npcs, times(2)).add(any(Npc::class.java))
        `when`(world.collisionManager.isBlocked(any(Position::class.java), eq(false))).thenReturn(true)
        assertNull(BarrowsEncounters.spawn(owner, BarrowsBrother.AHRIM))
        verify(world.npcs, times(2)).add(any(Npc::class.java))
    }
}
