package game.content.gnomeBall

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import com.google.common.collect.ImmutableList
import game.content.gnomeBall.GnomeBall.heldBalls
import game.content.gnomeBall.GnomeBall.matchBall
import game.content.gnomeBall.GnomeBall.rewardBall
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerContextMenu
import io.luna.game.model.mob.controller.ControllerManager
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.overlay.AbstractOverlaySet
import io.luna.game.model.`object`.GameObject
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.mockito.Mockito.*
import java.nio.file.Files
import java.nio.file.Path

/**
 * Verifies pitch boundaries, native assets, possession, delayed effects and persistent ball returns.
 *
 * @author lare96
 */
class GnomeBallTest {
    companion object {
        @JvmStatic @BeforeAll fun initialize() {
            InventoryProductionFixtures.initializeCombat()
            if (io.luna.game.model.def.VarpDefinition.ALL[143].isEmpty)
                io.luna.game.model.def.VarpDefinition.ALL.storeDefinition(io.luna.game.model.def.VarpDefinition(143, 0))
            if (WidgetDefinition.ALL[4958].isEmpty) {
                val widget = mock(WidgetDefinition::class.java)
                `when`(widget.id()).thenReturn(4958)
                `when`(widget.children).thenReturn(ImmutableList.of())
                WidgetDefinition.ALL.storeDefinition(widget)
            }
        }
    }
    private val players = mutableListOf<Player>()
    @AfterEach fun cleanup() { players.forEach(GnomeBall::leave); advance(12) }

    private fun player(position: Position = Position(2385, 3488)): Player = InventoryProductionFixtures.bot().also { p ->
        var current = position
        `when`(p.position).thenAnswer { current }
        doAnswer { current = it.getArgument(0); null }.`when`(p).move(any(Position::class.java))
        val equipment = Equipment(p)
        val controllers = ControllerManager(p)
        val menu = PlayerContextMenu(p)
        val overlays = AbstractOverlaySet(p)
        `when`(p.equipment).thenReturn(equipment)
        `when`(p.controllers).thenReturn(controllers)
        `when`(p.contextMenu).thenReturn(menu)
        `when`(p.overlays).thenReturn(overlays)
        `when`(p.isAlive).thenReturn(true)
        `when`(p.ranged.level).thenReturn(50)
        `when`(p.agility.level).thenReturn(50)
        `when`(world.collisionManager.raycast(any(Position::class.java), any(Position::class.java))).thenReturn(true)
        players.add(p)
    }

    private fun npc(base: Int, position: Position): Npc = mock(Npc::class.java, RETURNS_DEEP_STUBS).also { n ->
        var id = base
        var locked = false
        `when`(n.id).thenAnswer { id }
        `when`(n.baseId).thenReturn(base)
        `when`(n.position).thenReturn(position)
        `when`(n.state).thenReturn(EntityState.ACTIVE)
        `when`(n.walking.isLocked).thenAnswer { locked }
        doAnswer { id = it.getArgument(0); null }.`when`(n).transform(anyInt())
        doAnswer { id = base; null }.`when`(n).resetTransform()
        val walking = n.walking
        doAnswer { locked = it.getArgument(0); null }.`when`(walking).isLocked = anyBoolean()
    }

    private fun goal(): GameObject = mock(GameObject::class.java).also {
        `when`(it.id).thenReturn(2393); `when`(it.position).thenReturn(GnomeBall.GOAL)
    }
    private fun advance(ticks: Int) { repeat(ticks) { GnomeBall.tick(emptyList()) } }
    private fun begin(p: Player): GnomeBall.Session {
        assertTrue(GnomeBall.enter(p))
        assertTrue(GnomeBall.start(p, npc(635, p.position)))
        assertFalse(GnomeBall.hasBall(p))
        advance(2)
        assertTrue(GnomeBall.hasBall(p))
        return GnomeBall.session(p)!!
    }

    @Test fun pitchUsesAllFourSourceRectanglesAndRejectsOtherFloors() {
        assertTrue(GnomeBall.inside(Position(2383, 3488)))
        assertTrue(GnomeBall.inside(Position(2387, 3496)))
        assertTrue(GnomeBall.inside(Position(2406, 3481)))
        assertFalse(GnomeBall.inside(GnomeBall.EXIT))
        assertFalse(GnomeBall.inside(Position(2396, 3496)))
        assertFalse(GnomeBall.inside(Position(2396, 3488, 1)))
    }

    @Test fun entryRequiresRoomForBothHandsAndPreservesPersonalBalls() {
        val p = player()
        p.equipment.set(Equipment.WEAPON, Item(1275))
        p.equipment.set(Equipment.SHIELD, Item(1171))
        repeat(27) { p.inventory.add(Item(1265)) }
        assertFalse(GnomeBall.enter(p))
        assertEquals(1275, p.equipment[Equipment.WEAPON].id)
        p.inventory.remove(Item(1265, 3))
        p.inventory.add(Item(751))
        assertTrue(GnomeBall.enter(p))
        assertFalse(p.equipment.occupied(Equipment.WEAPON))
        assertFalse(p.equipment.occupied(Equipment.SHIELD))
        assertEquals(1, p.heldBalls)
        assertFalse(p.inventory.contains(751))
        GnomeBall.leave(p)
        assertEquals(1, p.inventory.computeAmountForId(751))
        assertEquals(0, p.heldBalls)
        assertEquals(PlayerController::class.java, p.controllers.primary.javaClass)
    }

    @Test fun refereeNeverCreatesAnotherBallWhileOneIsInFlightOrHeld() {
        val p = player(); val ref = npc(635, p.position)
        GnomeBall.enter(p)
        assertTrue(GnomeBall.start(p, ref))
        assertFalse(GnomeBall.start(p, ref))
        advance(2)
        assertFalse(GnomeBall.start(p, ref))
        assertEquals(1, p.equipment.computeAmountForId(751))
        GnomeBall.leave(p)
        assertFalse(p.equipment.contains(751))
        assertFalse(p.inventory.contains(751))
    }

    @Test fun shotsRequireFrontOfGoalRangeSightAndExactlyOneBall() {
        val p = player(); val session = begin(p); val goal = goal()
        assertFalse(GnomeBall.shoot(p, goal, 0.0))
        p.move(Position(2405, 3488))
        assertFalse(GnomeBall.shoot(p, goal, 0.0))
        p.move(Position(2398, 3488))
        `when`(world.collisionManager.raycast(p.position, GnomeBall.GOAL)).thenReturn(false)
        assertFalse(GnomeBall.shoot(p, goal, 0.0))
        `when`(world.collisionManager.raycast(p.position, GnomeBall.GOAL)).thenReturn(true)
        assertTrue(GnomeBall.shoot(p, goal, 0.0))
        assertFalse(GnomeBall.shoot(p, goal, 0.0))
        assertEquals(0, session.score)
        advance(3)
        assertEquals(1, session.score)
        verify(p.ranged).addExperience(4.0)
    }

    @Test fun fiveGoalsAwardSourceExperienceAndOneBallOnlyOnExit() {
        val p = player(); val session = begin(p); val goal = goal()
        p.move(Position(2398, 3488))
        repeat(5) { round ->
            if (round > 0) { assertTrue(GnomeBall.start(p, npc(635, p.position))); advance(2) }
            assertTrue(GnomeBall.shoot(p, goal, 0.0)); advance(3)
        }
        assertEquals(0, session.score)
        assertTrue(p.rewardBall)
        assertFalse(p.inventory.contains(751))
        verify(p.ranged).addExperience(30.0)
        verify(p.agility).addExperience(30.0)
        GnomeBall.leave(p); GnomeBall.leave(p)
        assertEquals(1, p.inventory.computeAmountForId(751))
    }

    @Test fun missedShotsAndAbandonedFlightsCannotAwardExperience() {
        val p = player(); val session = begin(p); val goal = goal()
        p.move(Position(2398, 3488))
        assertTrue(GnomeBall.shoot(p, goal, 1.0)); advance(3)
        assertEquals(0, session.score)
        assertTrue(GnomeBall.start(p, npc(635, p.position))); advance(2)
        assertTrue(GnomeBall.shoot(p, goal, 0.0))
        GnomeBall.leave(p); advance(4)
        verify(p.ranged, never()).addExperience(anyDouble())
        assertFalse(p.rewardBall)
    }

    @Test fun wingerReservesOneBallAndReturnsOnlyToItsOwner() {
        val a = player(); val b = player(); begin(a); begin(b)
        val winger = npc(633, Position(2390, 3488))
        assertTrue(GnomeBall.pass(a, winger))
        assertFalse(GnomeBall.pass(b, winger))
        assertEquals(634, winger.id)
        assertFalse(GnomeBall.start(a, npc(635, a.position)))
        advance(10)
        assertFalse(GnomeBall.hasBall(a))
        advance(2)
        assertTrue(GnomeBall.hasBall(a)); assertTrue(GnomeBall.hasBall(b))
        assertEquals(633, winger.id)
        assertFalse(winger.walking.isLocked)
    }

    @Test fun leavingWithAWingerBallRestoresNpcAndCancelsReturn() {
        val p = player(); begin(p)
        val winger = npc(633, Position(2390, 3488))
        GnomeBall.pass(p, winger)
        GnomeBall.leave(p); advance(12)
        assertEquals(633, winger.id)
        assertFalse(GnomeBall.hasBall(p))
        assertFalse(p.inventory.contains(751))
    }

    @Test fun defendersStealAndOwnerCanRecoverAfterCooldown() {
        val a = player(); val b = player(); begin(a); GnomeBall.enter(b)
        val defender = npc(621, a.position)
        assertTrue(GnomeBall.intercept(defender, a, 1.0))
        assertFalse(GnomeBall.hasBall(a)); assertEquals(622, defender.id)
        assertFalse(GnomeBall.tackle(a, defender, 0.0))
        GnomeBall.session(b)!!.started = true
        advance(7)
        assertFalse(GnomeBall.tackle(b, defender, 0.0))
        assertTrue(GnomeBall.tackle(a, defender, 0.0))
        assertTrue(GnomeBall.hasBall(a)); assertEquals(623, defender.id)
        advance(11)
        assertEquals(621, defender.id)
        assertFalse(defender.walking.isLocked)
    }

    @Test fun successfulDodgeLeavesBallWithPlayerAndTemporarilyDisablesDefender() {
        val p = player(); begin(p)
        val defender = npc(629, p.position)
        assertTrue(GnomeBall.intercept(defender, p, 0.0))
        assertTrue(GnomeBall.hasBall(p)); assertEquals(632, defender.id)
        assertFalse(GnomeBall.intercept(defender, p, 1.0))
        advance(7)
        assertEquals(629, defender.id)
    }

    @Test fun playerPassReservesRecipientAndCannotCrossPitchBoundary() {
        val a = player(); val b = player(); val c = player()
        begin(a); GnomeBall.enter(b); begin(c)
        assertTrue(GnomeBall.pass(a, b))
        assertFalse(GnomeBall.pass(c, b))
        assertFalse(GnomeBall.start(b, npc(635, b.position)))
        advance(2)
        assertTrue(GnomeBall.hasBall(b)); assertFalse(GnomeBall.hasBall(a))
        a.move(GnomeBall.EXIT)
        assertFalse(GnomeBall.pass(b, a))
    }

    @Test fun personalPassChecksRecipientBeforeRemovingBall() {
        val a = player(GnomeBall.EXIT); val b = player(Position(2381, 3488))
        a.inventory.add(Item(751))
        b.equipment.set(Equipment.WEAPON, Item(1275))
        assertFalse(GnomeBall.passPersonal(a, b)); assertTrue(a.inventory.contains(751))
        b.equipment.set(Equipment.WEAPON, null)
        assertTrue(GnomeBall.passPersonal(a, b))
        assertFalse(a.inventory.contains(751)); assertEquals(751, b.equipment[Equipment.WEAPON].id)
        assertFalse(b.matchBall)
    }

    @Test fun loginDeletesOnlyTemporaryBallAndReturnsHeldBallsOnce() {
        val p = player()
        p.heldBalls = 2; p.rewardBall = true; p.matchBall = true
        p.equipment.set(Equipment.WEAPON, Item(751))
        GnomeBall.login(p); GnomeBall.login(p)
        assertEquals(GnomeBall.EXIT, p.position)
        assertEquals(3, p.inventory.computeAmountForId(751))
        assertFalse(p.equipment.contains(751)); assertEquals(0, p.heldBalls)
        assertFalse(p.matchBall); assertFalse(p.rewardBall)
    }

    @Test fun fullInventoryReturnsHeldBallsToBank() {
        val p = player(); p.inventory.add(Item(751)); begin(p)
        repeat(28) { p.inventory.add(Item(1265)) }
        GnomeBall.leave(p); GnomeBall.claim(p)
        assertEquals(1, p.bank.computeAmountForId(751))
        assertEquals(0, p.heldBalls)
    }

    @Test fun controllerBlocksHandsTradingAndBallRemovalAndCleansUpOnExit() {
        val p = player(); begin(p)
        val controller = p.controllers.primary
        assertFalse(controller.event(io.luna.game.event.impl.EquipItemEvent(p, 0, 1275, 3214)))
        assertFalse(controller.event(io.luna.game.event.impl.WidgetItemClickEvent.WidgetItemFirstClickEvent(p, Equipment.WEAPON, 1688, 751)))
        assertFalse(controller.event(io.luna.game.event.impl.DropItemEvent(p, 751, 3214, 0)))
        assertFalse(controller.combat(player()))
        p.move(GnomeBall.EXIT); controller.move()
        assertNull(GnomeBall.session(p)); assertFalse(p.matchBall)
        assertEquals(PlayerController::class.java, p.controllers.primary.javaClass)
    }

    @Test fun nativeCacheContainsScoreboardGoalAndStraightGate() {
        assumeTrue(Files.exists(Path.of("data/game/cache/main_file_cache.dat")), "Local revision 377 cache required")
        io.luna.game.cache.Cache().use { cache ->
            cache.open()
            val widgets = ImmutableList.builder<WidgetDefinition>()
            io.luna.game.cache.codec.WidgetDefinitionDecoder().decode(cache, widgets)
            val definitions = widgets.build().associateBy { it.id() }
            assertTrue(definitions.getValue(4958).children.contains(4962))
            val decoder = io.luna.game.cache.codec.MapDecoder()
            val indices = ImmutableList.builder<io.luna.game.cache.map.MapIndex>()
            decoder.decode(cache, indices)
            decoder.handle(mock(io.luna.LunaContext::class.java), cache,
                ImmutableList.copyOf(indices.build().filter { it.region == GnomeBall.GOAL.region }))
            val objects = cache.mapIndexTable.objectSet.objects
            assertTrue(objects.any { it.objectId == 2393 && it.position == GnomeBall.GOAL })
            val gate = objects.single { it.objectId == 2394 }
            assertTrue(gate.position.isWithinDistance(GnomeBall.EXIT, 3))
            assertEquals(io.luna.game.model.`object`.ObjectType.STRAIGHT_WALL, gate.type)
            assertNotEquals(GnomeBall.inside(gate.position),
                GnomeBall.inside(gate.position.translate(1, gate.rotation.toNormalDirection())))
        }
    }

    @Test fun normalDeathHookReceivesPlayerAfterTemporaryBallIsRemoved() {
        val p = player(); begin(p)
        p.inventory.add(Item(1265))
        `when`(p.isAlive).thenReturn(false)
        val previous = api.combat.death.DeathHookHandler.defaultPlayerHook
        var calls = 0
        try {
            api.combat.death.DeathHookHandler.defaultPlayerHook = {
                calls++
                assertFalse(victim.equipment.contains(751))
                assertTrue(victim.inventory.contains(1265))
            }
            GnomeBall.launch()
            api.combat.death.DeathHookHandler.onDeath(p, null, io.luna.game.model.mob.MobDeathAction.DeathStage.PRE_DEATH)
            assertNull(GnomeBall.session(p))
            api.combat.death.DeathHookHandler.onDeath(p, null, io.luna.game.model.mob.MobDeathAction.DeathStage.DEATH)
            assertEquals(2, calls)
        } finally {
            api.combat.death.DeathHookHandler.defaultPlayerHook = previous
        }
    }

    @Test fun unclaimedBallsStayPersistentWhenInventoryAndBankCannotAcceptThem() {
        val p = player()
        repeat(28) { p.inventory.add(Item(1265)) }
        p.bank.add(Item(751, Int.MAX_VALUE))
        p.heldBalls = 2
        GnomeBall.claim(p)
        assertEquals(2, p.heldBalls)
        assertEquals(Int.MAX_VALUE, p.bank.computeAmountForId(751))
        p.inventory.remove(Item(1265, 2))
        GnomeBall.claim(p); GnomeBall.claim(p)
        assertEquals(0, p.heldBalls)
        assertEquals(2, p.inventory.computeAmountForId(751))
    }

    @Test fun nativeRefereeHandlerLeavesTreasureTrailChallengeInControl() {
        Class.forName("game.content.gnomeBall.GnomeBallHandlers").getConstructor(Array<String>::class.java)
            .newInstance(emptyArray<String>() as Any)
        val p = player(); GnomeBall.enter(p)
        val referee = npc(635, p.position)
        p.inventory.add(Item(2853))
        val event = io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent(p, referee)
        val listeners = api.event.Matcher.get<io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent, Int>()
            .interactions(p, event)
        assertTrue(listeners.isNotEmpty())
        listeners.forEach { it.action.run() }
        assertFalse(p.overlays.hasWindow())
        assertTrue(game.content.treasureTrails.TreasureTrails.talk(p, referee))
        assertTrue(p.inventory.contains(2854))
        assertFalse(GnomeBall.session(p)!!.started)
    }
}
