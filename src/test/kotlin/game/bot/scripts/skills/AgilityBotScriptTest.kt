package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.zone.SubZone
import api.bot.zone.Zone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.AgilityScriptFactory
import engine.bot.coordinator.skill.SkillingCoordinator
import game.bot.scripts.skills.AgilityBotScript.Companion.AgilityData
import game.skill.agility.Agility.completedObstacles
import game.skill.agility.AgilityCourse
import game.skill.agility.AgilityObstacle
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.event.EventMatcherListener
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.script.BotScriptManager
import io.luna.game.model.`object`.GameObject
import io.luna.game.task.Task
import io.luna.game.task.TaskState
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.*
import java.util.function.Predicate
import java.util.ArrayDeque
import java.util.Deque
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.minutes

/**
 * Exercises native Gnome traversal, checkpoint restoration, retry limits and normal script dispatch.
 *
 * Native actions and the action queue are real; fixture ticks drive them without starting a live server.
 *
 * @author lare96
 */
class AgilityBotScriptTest {
    companion object {
        private lateinit var nativeHandlers: Map<Int, EventMatcherListener<ObjectFirstClickEvent>>
        @JvmStatic @BeforeAll fun fixtures() {
            InventoryProductionFixtures.initialize()
            val first = scriptMatchers.size
            Class.forName("game.skill.agility.course.Gnome_stronghold")
                .getConstructor(Array<String>::class.java).newInstance(emptyArray<String>() as Any)
            val ids = listOf(2295, 2285, 2313, 2312, 4059, 2314, 2315, 2286, 154, 4058)
            @Suppress("UNCHECKED_CAST")
            val added = scriptMatchers.drop(first) as List<EventMatcherListener<ObjectFirstClickEvent>>
            assertEquals(ids.size, added.size)
            nativeHandlers = ids.zip(added).toMap()
        }
    }

    private val course = AgilityBotCourse.GNOME_STRONGHOLD
    private var position = Position(2474, 3437)
    private var locked = false
    private lateinit var bot: Bot

    @BeforeEach fun botFixture() {
        bot = InventoryProductionFixtures.bot()
        `when`(bot.position).thenAnswer { position }
        `when`(bot.x).thenAnswer { position.x }
        `when`(bot.y).thenAnswer { position.y }
        `when`(bot.z).thenAnswer { position.z }
        `when`(bot.isLocked).thenAnswer { locked }
        `when`(bot.walking.isEmpty).thenReturn(true)
        `when`(bot.agility.level).thenReturn(1)
        doAnswer { locked = true; null }.`when`(bot).lock()
        doAnswer { locked = false; null }.`when`(bot).unlock()
        doAnswer { position = it.getArgument(0); null }.`when`(bot).move(any(Position::class.java))
        doAnswer { bot.actions.submit(it.getArgument<Action<*>>(0)); null }.`when`(bot).submitAction(any(Action::class.java))
    }

    private fun data(step: Int = 0, landing: Position? = null, failures: Int = 0) = AgilityData().also {
        it.duration = 8.minutes
        it.zones = mutableListOf(course.zone)
        it.step = step
        it.landing = landing
        it.failures = failures
    }
    private fun restored(step: Int = 0, landing: Position? = null, failures: Int = 0) =
        AgilityBotScript(bot, data(step, landing, failures))
    private fun obj(id: Int, tile: Position): GameObject = mock(GameObject::class.java).also {
        `when`(it.id).thenReturn(id)
        `when`(it.position).thenReturn(tile)
        `when`(it.state).thenReturn(EntityState.ACTIVE)
    }
    private suspend fun withObjects(objects: List<GameObject>, action: suspend () -> Unit) {
        val locator = world.locator
        doAnswer {
            val predicate = it.getArgument<Predicate<GameObject>>(2)
            objects.filter { candidate -> predicate.test(candidate) }.toSet()
        }.`when`(locator).findObjects(any(Position::class.java), anyInt(), any<Predicate<GameObject>>())
        try { action() }
        finally { doReturn(emptySet<GameObject>()).`when`(locator)
            .findObjects(any(Position::class.java), anyInt(), any<Predicate<GameObject>>()) }
    }

    private suspend fun withTicks(action: suspend () -> Unit) {
        val run = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        doAnswer {
            val task = it.getArgument<Task>(0)
            repeat(100) {
                if (task.state != TaskState.CANCELLED) {
                    bot.actions.process()
                    run.invoke(task)
                }
            }
            assertEquals(TaskState.CANCELLED, task.state)
            null
        }.`when`(world).schedule(any(Task::class.java))
        try { action() }
        finally { doNothing().`when`(world).schedule(any(Task::class.java)) }
    }

    @Test fun fullLapUsesNativeActionsAndOnlyNativeExperience() = runBlocking<Unit> {
        val script = AgilityBotScript(bot, course, 8.minutes)
        assertTrue(script.onInit(false))
        val entries = listOf(Position(2474, 3436), Position(2473, 3426), Position(2473, 3423, 1),
            Position(2477, 3420, 2), Position(2486, 3419, 2), Position(2486, 3426), Position(2484, 3430))
        val objectTiles = listOf(Position(2474, 3435), Position(2473, 3425), Position(2473, 3422, 1),
            Position(2478, 3420, 2), Position(2486, 3418, 2), Position(2486, 3427), Position(2484, 3431))
        for (index in course.steps.indices) {
            position = entries[index]
            val target = obj(course.steps[index].objectIds.first(), objectTiles[index])
            val interactions = bot.actionHandler.interactions
            `when`(interactions.interact(1, target)).thenAnswer {
                nativeHandlers.getValue(target.id).apply(ObjectFirstClickEvent(bot, target))
                assertEquals(index, script.step, "Submission and its immediate XP must not advance the bot")
                assertFalse(script.hasLanded())
                true
            }
            withObjects(listOf(target)) { withTicks { assertTrue(script.executeInZone()) } }
            assertEquals((index + 1) % 7, script.step)
            assertNull(script.landing)
            assertEquals(0, bot.actions.size(ActionType.STRONG))
            assertFalse(locked)
            verify(interactions).interact(1, target)
        }
        assertEquals(Position(2484, 3437), position)
        assertTrue(bot.completedObstacles.get(course.course).isEmpty())
        verify(bot.agility, times(4)).addExperience(10.0)
        verify(bot.agility, times(2)).addExperience(6.5)
        verify(bot.agility).addExperience(7.5)
        verify(bot.agility).addExperience(50.0)
        verifyNoInteractions(bot.actionHandler.banking)
    }

    @Test fun arrivingDuringNativeClimbDoesNotAdvanceUntilUnlock() = runBlocking<Unit> {
        position = Position(2473, 3423, 1)
        val script = restored(2, Position(2473, 3420, 2))
        val target = obj(2313, Position(2473, 3422, 1))
        nativeHandlers.getValue(2313).apply(ObjectFirstClickEvent(bot, target))
        bot.actions.process()
        bot.actions.process()
        assertEquals(script.landing, position)
        assertTrue(locked)
        assertFalse(script.hasLanded())
        assertTrue(script.reconcileLanding())
        assertEquals(2, script.step)
        bot.actions.process()
        bot.actions.process() // The queue removes completed strong actions on the following tick.
        assertTrue(script.hasLanded())
        assertTrue(script.reconcileLanding())
        assertEquals(3, script.step)
    }

    @ParameterizedTest @ValueSource(ints = [2315, 4058])
    fun alternateNativeBranchAndPipeLandAtTheirOwnTiles(id: Int) = runBlocking<Unit> {
        val index = if (id == 2315) 4 else 6
        val entry = if (id == 2315) Position(2487, 3420, 2) else Position(2487, 3430)
        position = entry
        val target = obj(id, entry.translate(0, 1, 0))
        val script = restored(index)
        assertTrue(script.onInit(true))
        `when`(bot.actionHandler.interactions.interact(1, target)).thenAnswer {
            nativeHandlers.getValue(id).apply(ObjectFirstClickEvent(bot, target)); true
        }
        withObjects(listOf(target)) { withTicks { assertTrue(script.executeInZone()) } }
        assertEquals(if (id == 2315) Position(2487, 3420) else Position(2487, 3437), position)
        assertEquals((index + 1) % 7, script.step)
    }

    @Test fun xpAndNativeMarkerWithoutMovementAreNotSuccessfulTraversal() = runBlocking<Unit> {
        position = Position(2473, 3423, 1)
        val target = obj(2313, Position(2473, 3422, 1))
        val script = restored(2)
        `when`(bot.actionHandler.interactions.interact(1, target)).thenAnswer {
            bot.completedObstacles.put(course.course, AgilityObstacle.GNOME_TREE_BRANCH)
            bot.agility.addExperience(6.5)
            false
        }
        withObjects(listOf(target)) { assertTrue(script.executeInZone()) }
        assertEquals(2, script.step)
        assertEquals(1, script.failures)
        assertNull(script.landing)
    }

    @Test fun timedOutLandingConsumesOneAttemptAndDoesNotSkipObstacle() = runBlocking<Unit> {
        position = Position(2473, 3423, 1)
        val target = obj(2313, Position(2473, 3422, 1))
        val script = restored(2)
        `when`(bot.actionHandler.interactions.interact(1, target)).thenReturn(true)
        withObjects(listOf(target)) { withTicks { assertTrue(script.executeInZone()) } }
        assertEquals(2, script.step)
        assertEquals(1, script.failures)
    }

    @Test fun missingStaleWrongPlaneAndReverseObjectsDoNotProduceProgress() = runBlocking<Unit> {
        position = Position(2477, 3420, 2)
        val script = restored(3)
        val stale = obj(2312, position)
        `when`(stale.state).thenReturn(EntityState.INACTIVE)
        val wrongPlane = obj(2312, Position(2477, 3420))
        val reverse = obj(4059, position)
        withObjects(listOf(stale, wrongPlane, reverse)) {
            assertTrue(script.executeInZone())
            assertTrue(script.executeInZone())
            assertFalse(script.executeInZone())
        }
        assertEquals(3, script.failures)
        assertEquals(3, script.step)
        verifyNoInteractions(bot.actionHandler.interactions)
        assertFalse(AgilityBotScript(bot, script.snapshot()).onInit(true))
    }

    @Test fun savedPendingLandingAdvancesExactlyOnceAfterResume() = runBlocking<Unit> {
        position = Position(2473, 3420, 2)
        val pending = restored(2, position, 2)
        val json = JsonObject()
        pending.snapshot().save(json)
        val loaded = AgilityData().also { it.load(json) }
        val script = AgilityBotScript(bot, loaded)
        assertTrue(script.onInit(true))
        assertEquals(course.steps.take(2).map { it.obstacle }.toSet(), bot.completedObstacles.get(course.course).toSet())
        assertTrue(script.executeInZone())
        assertEquals(3, script.step)
        assertEquals(course.steps.take(3).map { it.obstacle }.toSet(), bot.completedObstacles.get(course.course).toSet())
        assertEquals(0, script.failures)
        assertNull(script.landing)
        verifyNoInteractions(bot.actionHandler.interactions)
        verify(bot.agility, never()).addExperience(anyDouble())
    }

    @Test fun interruptedPendingLandingUsesSavedBudgetRatherThanClaimingSuccess() = runBlocking<Unit> {
        position = Position(2473, 3423, 1)
        val script = restored(2, Position(2473, 3420, 2), 2)
        assertTrue(script.onInit(true))
        assertFalse(script.executeInZone())
        assertEquals(3, script.failures)
        assertEquals(2, script.step)
    }

    @Test fun upperPlaneProgressIsPreservedAndGroundTeleportRestartsLap() = runBlocking<Unit> {
        position = Position(2473, 3420, 2)
        val upper = restored(3)
        assertTrue(upper.onInit(true))
        assertEquals(3, upper.step)
        bot.completedObstacles.put(AgilityCourse.WILDERNESS, AgilityObstacle.WILDERNESS_ROPESWING)
        position = Position(3093, 3244)
        val teleported = restored(3, Position(2483, 3420, 2), 1)
        assertTrue(teleported.onInit(true))
        assertEquals(0, teleported.step)
        assertNull(teleported.landing)
        assertTrue(bot.completedObstacles.get(course.course).isEmpty())
        assertFalse(bot.completedObstacles.get(AgilityCourse.WILDERNESS).isEmpty())
    }

    @Test fun lockDefersTheNextClickWithoutConsumingAnAttempt() = runBlocking<Unit> {
        locked = true
        val script = restored()
        assertTrue(script.executeInZone())
        assertEquals(0, script.failures)
        verifyNoInteractions(bot.actionHandler.interactions)
    }

    @ParameterizedTest @ValueSource(ints = [-1, 7, 100])
    fun invalidSavedStepIsRejected(index: Int) = runBlocking<Unit> {
        assertFalse(restored(index).onInit(true))
    }

    @Test fun unreachableExactEntryDoesNotSendAnObstacleClick() = runBlocking<Unit> {
        val script = restored()
        val target = obj(2295, Position(2474, 3435))
        val entry = Position(2474, 3436)
        val shortPath: Deque<Position> = ArrayDeque(listOf(Position(2474, 3437)))
        `when`(bot.navigator.findPath(eq(position), eq(entry), any(), eq(true)))
            .thenReturn(CompletableFuture.completedFuture(shortPath))
        withObjects(listOf(target)) { assertTrue(script.executeInZone()) }
        assertEquals(1, script.failures)
        verifyNoInteractions(bot.actionHandler.interactions)
        verify(bot.walking, never()).replacePath(any())
    }

    @Test fun exactLogEntryIsWalkedBeforeSendingTheNativeClick() = runBlocking<Unit> {
        val script = restored()
        val target = obj(2295, Position(2474, 3435))
        val entry = Position(2474, 3436)
        val path: Deque<Position> = ArrayDeque(listOf(entry))
        `when`(bot.navigator.findPath(eq(position), eq(entry), any(), eq(true)))
            .thenReturn(CompletableFuture.completedFuture(path))
        val walking = bot.walking
        doAnswer { position = entry; null }.`when`(walking).replacePath(path)
        `when`(bot.actionHandler.interactions.interact(1, target)).thenAnswer {
            assertEquals(entry, position)
            nativeHandlers.getValue(2295).apply(ObjectFirstClickEvent(bot, target)); true
        }
        withObjects(listOf(target)) { withTicks { assertTrue(script.executeInZone()) } }
        assertEquals(1, script.step)
        assertEquals(Position(2474, 3429), position)
    }

    @Test fun factoryAndCentralRegistrationRestoreNormalAgilityTraining() = runBlocking<Unit> {
        val zones = mutableListOf<SubZone>()
        val selected = AgilityScriptFactory.getTrainingScript(bot, 1, zones)
        assertInstanceOf(AgilityBotScript::class.java, selected)
        assertEquals(listOf(course.zone), zones)
        assertFalse(AgilityScriptFactory.getTrainingScript(bot, 0, mutableListOf()) is AgilityBotScript)
        assertTrue(AgilityScriptFactory.getProfitScript(bot, 99, mutableListOf()).run())
        val factories = SkillingCoordinator::class.java.getDeclaredField("factories").apply { isAccessible = true }
        for (training in listOf(true, false)) {
            assertSame(AgilityScriptFactory, (factories.get(SkillingCoordinator(training)) as Map<*, *>)[SKILL_AGILITY])
        }
        val manager = BotScriptManager()
        val managers = world.botManager
        val previous = managers.scriptManager
        `when`(managers.scriptManager).thenReturn(manager)
        try {
            InventoryProductionFixtures.register("Scripts")
            assertInstanceOf(AgilityBotScript::class.java,
                manager.loadScript(AgilityBotScript::class.qualifiedName, bot, data(2, Position(2473, 3420, 2), 1)))
        } finally { `when`(managers.scriptManager).thenReturn(previous) }
    }

    @Test fun suppliedZoneFootprintIncludesUpperPlatformsAndEveryBankAnchor() {
        val zone = Zone.GNOME_STRONGHOLD
        assertEquals(1500, zone.price)
        assertEquals(Position(2459, 3397), zone.anchor)
        assertEquals(setOf(9781, 9525, 9782, 9526, 9783, 9527), zone.regions)
        assertEquals(listOf(Position(2447, 3424), Position(2447, 3427), Position(2444, 3424), Position(2444, 3425)), zone.bankAnchors)
        assertEquals(Position(2474, 3437), course.zone.inside)
        assertSame(zone, course.zone.parent(bot))
        for (tile in listOf(Position(2473, 3420, 2), Position(2483, 3420, 2), Position(2487, 3437))) {
            assertTrue(tile in course.zone.area)
        }
    }
}
