package io.luna.game.model.path.route

import api.bot.zone.SubZone
import api.combat.npc.NpcCombatHandler
import api.combat.npc.dsl.NpcCombatReceiver
import engine.bot.combat.NpcSafespotMap
import game.bot.scripts.combat.NpcSafespotCombat
import io.luna.game.action.impl.NpcRetreatAction.RetreatPolicy
import io.luna.game.model.Direction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.collision.CollisionFlag
import io.luna.game.model.collision.CollisionUpdate
import io.luna.game.model.collision.CollisionUpdateType
import io.luna.game.model.def.NpcCombatMovement
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.combat.PursuitAction
import io.luna.game.model.mob.combat.attack.CombatAttack
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Validates safespot predictions against real map collision and cache invalidation.
 *
 * @author lare96
 */
class NpcSafespotMapTest {

    private val map = TestMap()
    private val world = mock(World::class.java, RETURNS_DEEP_STUBS)
    private val bot = mock(Bot::class.java, RETURNS_DEEP_STUBS)
    private val npc = mock(Npc::class.java, RETURNS_DEEP_STUBS)
    private val zone = SubZone.LUMBRIDGE_CHICKEN_COOP
    private val policy = InteractionPolicy(InteractionType.LINE_OF_SIGHT, 7)
    private var npcPosition = Position(3233, 3293)

    @BeforeEach
    fun setup() {
        `when`(world.collisionManager).thenReturn(map.manager())
        `when`(world.context.game.thread).thenReturn(Thread.currentThread())
        `when`(bot.world).thenReturn(world)
        `when`(bot.state).thenReturn(EntityState.ACTIVE)
        `when`(bot.position).thenReturn(Position(3233, 3297))
        `when`(bot.z).thenReturn(0)
        `when`(bot.isAlive).thenReturn(true)
        `when`(npc.world).thenReturn(world)
        `when`(npc.state).thenReturn(EntityState.ACTIVE)
        `when`(npc.id).thenReturn(999_999)
        `when`(npc.position).thenAnswer { npcPosition }
        `when`(npc.basePosition).thenReturn(npcPosition)
        `when`(npc.z).thenReturn(0)
        `when`(npc.isAlive).thenReturn(true)
        `when`(npc.size()).thenReturn(1)
        doReturn(RouteStrategy.NORMAL).`when`(npc).routeStrategy
        `when`(npc.combat.retreatPolicy).thenReturn(RetreatPolicy.NONE)
    }

    private fun table() = map.fill(3231, 3295, 5, 1, CollisionFlag.LOC)

    /**
     * Seeds the controller immediately after a native engagement, preserving real map and cache validation.
     */
    private fun observingController(): NpcSafespotCombat {
        table()
        val candidate = requireNotNull(NpcSafespotMap.propose(bot, npc, zone, policy))
        npcPosition = candidate.trap
        `when`(bot.walking.isEmpty).thenReturn(true)
        `when`(npc.walking.isEmpty).thenReturn(true)
        `when`(bot.combat.target).thenReturn(npc)
        `when`(npc.combat.target).thenReturn(bot)
        `when`(npc.combatDef().attackSpeed).thenReturn(4)
        val controller = NpcSafespotCombat(bot) { true }
        for ((name, value) in mapOf("candidate" to candidate, "target" to npc, "zone" to zone, "policy" to policy)) {
            NpcSafespotCombat::class.java.getDeclaredField(name).apply {
                isAccessible = true
                set(controller, value)
            }
        }
        return controller
    }

    private fun playerShot(tick: Long) {
        val attack = mock(CombatAttack::class.java)
        `when`(attack.attacker).thenReturn(bot)
        `when`(attack.victim).thenReturn(npc)
        `when`(attack.launchTick).thenReturn(tick)
        val combat = bot.combat
        doReturn(attack).`when`(combat).lastAttackSent
    }

    @Test
    fun `intermittent flinch cooldowns preserve stable geometry observations`() {
        val controller = observingController()
        val confirmations = NpcSafespotMap.confirmations
        for (tick in 0L..12L) {
            `when`(world.currentTick).thenReturn(tick)
            `when`(npc.combat.isAttackReady).thenReturn(tick == 2L || tick == 8L)
            if (tick in setOf(1L, 5L, 9L)) playerShot(tick)
            assertTrue(controller.maintain(npc, zone) { true })
        }
        assertEquals(true, controller.snapshot()["verified"])
        assertEquals(2, controller.snapshot()["readyTicks"])
        assertEquals(confirmations + 1, NpcSafespotMap.confirmations)
    }

    @Test
    fun `a monster whose attack never becomes ready cannot teach a safespot`() {
        val controller = observingController()
        val confirmations = NpcSafespotMap.confirmations
        for (tick in 0L..12L) {
            `when`(world.currentTick).thenReturn(tick)
            if (tick % 4L == 1L) playerShot(tick)
            assertTrue(controller.maintain(npc, zone) { true })
        }
        assertEquals(false, controller.snapshot()["verified"])
        assertEquals(0, controller.snapshot()["readyTicks"])
        assertEquals(confirmations, NpcSafespotMap.confirmations)
    }

    @Test
    fun `a launched npc attack rejects the stand without requiring damage`() {
        val controller = observingController()
        assertTrue(controller.maintain(npc, zone) { true })
        val miss = mock(CombatAttack::class.java)
        `when`(miss.victim).thenReturn(bot)
        `when`(miss.attacker).thenReturn(npc)
        val combat = npc.combat
        doReturn(miss).`when`(combat).lastAttackSent
        assertFalse(controller.maintain(npc, zone) { true })
        assertFalse(controller.isHolding)
        assertEquals(1, controller.snapshot()["rejections"])
    }

    @Test
    fun `open ground never becomes a safespot`() {
        assertNull(NpcSafespotMap.propose(bot, npc, zone, policy))
    }

    @Test
    fun `furniture stops melee chase while allowing the shot`() {
        table()
        val candidate = requireNotNull(NpcSafespotMap.propose(bot, npc, zone, policy))
        assertEquals(bot.position, candidate.stand)
        assertEquals(Position(3233, 3294), candidate.trap)
        assertFalse(candidate.cached)
        assertTrue(NpcSafespotMap.valid(bot, npc, candidate, policy))
        npcPosition = candidate.trap
        assertTrue(NpcSafespotMap.valid(bot, npc, candidate, policy))
    }

    @Test
    fun `temporary players and npcs cannot create safespots`() {
        map.fill(3231, 3295, 5, 1, CollisionFlag.BLOCK_NPCS or CollisionFlag.BLOCK_PLAYERS)
        assertNull(NpcSafespotMap.propose(bot, npc, zone, policy))
    }

    @Test
    fun `large enemies stop before their footprint reaches the furniture`() {
        table()
        `when`(npc.size()).thenReturn(2)
        val candidate = requireNotNull(NpcSafespotMap.propose(bot, npc, zone, policy))
        assertEquals(Position(3233, 3293), candidate.trap)
        assertTrue(NpcSafespotMap.valid(bot, npc, candidate, policy))
    }

    @Test
    fun `standing beyond the monsters leash is not a safespot`() {
        table()
        `when`(npc.combat.retreatPolicy).thenReturn(RetreatPolicy.CONFIGURED)
        `when`(npc.combatDef().movement).thenReturn(NpcCombatMovement(0, 0, -1))
        assertNull(NpcSafespotMap.propose(bot, npc, zone, policy))
    }

    @Test
    fun `projectile blocking furniture is rejected`() {
        map.fill(3225, 3295, 12, 1, CollisionFlag.LOC or CollisionFlag.LOC_PROJ_BLOCKER)
        assertNull(NpcSafespotMap.propose(bot, npc, zone, policy))
    }

    @Test
    fun `only controller confirmation teaches the shared cache`() {
        table()
        val first = requireNotNull(NpcSafespotMap.propose(bot, npc, zone, policy))
        assertFalse(requireNotNull(NpcSafespotMap.propose(bot, npc, zone, policy)).cached)
        NpcSafespotMap.verify(first)
        val reused = requireNotNull(NpcSafespotMap.propose(bot, npc, zone, policy))
        assertTrue(reused.cached)
        assertEquals(first.stand, reused.stand)
    }

    @Test
    fun `cache hits are revalidated after furniture changes`() {
        table()
        val candidate = requireNotNull(NpcSafespotMap.propose(bot, npc, zone, policy))
        NpcSafespotMap.verify(candidate)
        val removal = CollisionUpdate.Builder()
        removal.type(CollisionUpdateType.REMOVING)
        for (x in 3231..3235) {
            removal.flag(Position(x, 3295), CollisionFlag.LOC)
        }
        map.manager().apply(removal.build(), true)
        assertFalse(NpcSafespotMap.valid(bot, npc, candidate, policy))
        assertNull(NpcSafespotMap.propose(bot, npc, zone, policy))
    }

    @Test
    fun `a new spawn cannot inherit another monsters learned spot`() {
        table()
        val candidate = requireNotNull(NpcSafespotMap.propose(bot, npc, zone, policy))
        NpcSafespotMap.verify(candidate)
        `when`(npc.basePosition).thenReturn(Position(3234, 3293))
        val differentSpawn = requireNotNull(NpcSafespotMap.propose(bot, npc, zone, policy))
        assertFalse(differentSpawn.cached)
        assertFalse(NpcSafespotMap.valid(bot, npc, candidate, policy))
    }

    @Test
    fun `separate worlds do not share learned spots`() {
        table()
        val candidate = requireNotNull(NpcSafespotMap.propose(bot, npc, zone, policy))
        NpcSafespotMap.verify(candidate)
        val otherWorld = mock(World::class.java, RETURNS_DEEP_STUBS)
        `when`(otherWorld.collisionManager).thenReturn(map.manager())
        `when`(otherWorld.context.game.thread).thenReturn(Thread.currentThread())
        `when`(bot.world).thenReturn(otherWorld)
        `when`(npc.world).thenReturn(otherWorld)
        assertFalse(requireNotNull(NpcSafespotMap.propose(bot, npc, zone, policy)).cached)
        assertFalse(NpcSafespotMap.valid(bot, npc, candidate, policy))
    }

    @Test
    fun `failed stands are not immediately proposed again`() {
        table()
        val candidate = requireNotNull(NpcSafespotMap.propose(bot, npc, zone, policy))
        NpcSafespotMap.verify(candidate)
        NpcSafespotMap.reject(candidate)
        assertNotEquals(candidate.stand, NpcSafespotMap.propose(bot, npc, zone, policy)?.stand)
    }

    @Test
    fun `custom attacks are excluded without invoking their hooks`() {
        table()
        var invoked = false
        val receiver = NpcCombatReceiver().apply {
            attack { invoked = true; default() }
        }
        NpcCombatHandler.idMap[npc.id] = receiver
        try {
            assertNull(NpcSafespotMap.propose(bot, npc, zone, policy))
            assertFalse(invoked)
        } finally {
            NpcCombatHandler.idMap.remove(npc.id)
        }
    }

    @Test
    fun `frozen enemies and melee policies cannot teach safespots`() {
        table()
        `when`(npc.status.isImmobilized()).thenReturn(true)
        assertNull(NpcSafespotMap.propose(bot, npc, zone, policy))
        `when`(npc.status.isImmobilized()).thenReturn(false)
        assertNull(NpcSafespotMap.propose(bot, npc, zone, InteractionPolicy.STANDARD_SIZE))
    }

    @Test
    fun `planner refuses reads off the game thread`() {
        `when`(world.context.game.thread).thenReturn(Thread())
        assertThrows(IllegalStateException::class.java) { NpcSafespotMap.propose(bot, npc, zone, policy) }
    }

    @Test
    fun `shared pursuit preserves diagonal then x then y order`() {
        val from = Position(3230, 3290)
        val target = Position(3234, 3294)
        assertEquals(Direction.NORTH_EAST, PursuitAction.nextStep(from, target, 1, false) { true })
        assertEquals(Direction.EAST, PursuitAction.nextStep(from, target, 1, false) { it != Direction.NORTH_EAST })
        assertEquals(Direction.NORTH, PursuitAction.nextStep(from, target, 1, false) { it == Direction.NORTH })
        assertEquals(Direction.NONE, PursuitAction.nextStep(from, target, 1, false) { false })
    }

    @Test
    fun `shared pursuit separates unreachable diagonals and preserves valid footprint contact`() {
        val from = Position(3230, 3290)
        val target = Position(3231, 3291)
        assertEquals(Direction.NORTH, PursuitAction.nextStep(from, target, 1, false) { true })
        assertEquals(Direction.NONE, PursuitAction.nextStep(from, target, 1, true) {
            fail("Valid footprint contact must not queue another pursuit step.")
        })
        assertEquals(Direction.NONE, PursuitAction.nextStep(from, from, 1, false) {
            fail("Overlapping pursuit belongs to the live random-separation handler.")
        })
    }
}
