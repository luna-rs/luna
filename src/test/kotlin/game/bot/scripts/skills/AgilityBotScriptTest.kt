package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import api.bot.webwalk.plan.BankAccess
import api.bot.zone.SubZone
import api.predef.SKILL_AGILITY
import api.predef.world
import game.skill.agility.Agility.courseProgress
import io.luna.game.model.Entity
import io.luna.game.model.EntityState
import io.luna.game.model.Locatable
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import io.luna.game.task.Task
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.EnumSet
import java.util.function.Predicate
import kotlin.time.Duration.Companion.minutes

/**
 * Exercises upper-floor agility obstacles through the shared zone lifecycle and filtered object discovery.
 *
 * @author lare96
 */
class AgilityBotScriptTest {
    companion object {
        private val runTask = Task::class.java.getDeclaredMethod("runTask").apply { isAccessible = true }
        private val entityPosition = Entity::class.java.getDeclaredField("position").apply { isAccessible = true }

        @JvmStatic @BeforeAll fun initialize() = InventoryProductionFixtures.initialize()
    }

    private lateinit var bot: Bot
    private var position = Position(2473, 3424, 1)
        set(value) {
            field = value
            if (::bot.isInitialized) entityPosition.set(bot, value)
        }
    private var objects = emptySet<GameObject>()
    private val walked = mutableListOf<Position>()

    @BeforeEach fun setup() {
        bot = InventoryProductionFixtures.bot()
        entityPosition.set(bot, position)
        `when`(bot.position).thenAnswer { position }
        `when`(bot.abs()).thenAnswer { position }
        `when`(bot.walking.isEmpty).thenReturn(true)
        `when`(bot.healthPercent).thenReturn(100)
        `when`(bot.skill(SKILL_AGILITY).level).thenReturn(99)
        doReturn(EnumSet.noneOf(SubZone::class.java)).`when`(bot).subZones
        doAnswer { SubZone.LOCAL[position.regionId] }.`when`(bot).localSubZones
        `when`(world.locator.findObjects(any(Locatable::class.java), anyInt(), any())).thenAnswer { call ->
            val base = call.getArgument<Locatable>(0)
            val distance = call.getArgument<Int>(1)
            val filter = call.getArgument<Predicate<GameObject>>(2)
            objects.filter { base.isWithinDistance(it, distance) && filter.test(it) }.toSet()
        }
        doAnswer { call ->
            val task = call.getArgument<Task>(0)
            repeat(10) { runTask.invoke(task) }
            null
        }.`when`(world).schedule(any(Task::class.java))
    }

    @AfterEach fun restoreWorld() {
        val locator = world.locator
        doReturn(emptySet<GameObject>()).`when`(locator)
            .findObjects(any(Locatable::class.java), anyInt(), any())
        doNothing().`when`(world).schedule(any(Task::class.java))
    }

    @Test fun gnomeFirstNetContinuesToTheUpperBranchWithoutReturnTravel() = runTest {
        traverse(AgilityBotCourse.GNOME_STRONGHOLD, 2, Position(2473, 3424, 1),
            2313, Position(2473, 3422, 1), Position(2473, 3423, 1), Position(2473, 3420, 2))
    }

    @Test fun gnomeSecondFloorContinuesAcrossTheRopeWithoutReturnTravel() = runTest {
        traverse(AgilityBotCourse.GNOME_STRONGHOLD, 3, Position(2473, 3420, 2),
            2312, Position(2478, 3420, 2), Position(2477, 3420, 2), Position(2483, 3420, 2))
    }

    @Test fun barbarianNetContinuesToTheUpperLedgeWithoutReturnTravel() = runTest {
        traverse(AgilityBotCourse.BARBARIAN_OUTPOST, 3, Position(2537, 3546, 1),
            2302, Position(2536, 3547, 1), Position(2536, 3547, 1), Position(2532, 3546, 1))
    }

    @Test fun lockedNativeTraversalDoesNotSearchOrSendAnotherInteraction() = runTest {
        val script = restored(AgilityBotCourse.GNOME_STRONGHOLD, 2, position)
        assertFalse(script.init(true))
        val interactions = bot.actionHandler.interactions
        clearInvocations(world.locator, interactions)
        `when`(bot.isLocked).thenReturn(true)

        assertFalse(script.run())

        assertEquals(2, script.step)
        assertNull(script.landing)
        verifyNoInteractions(interactions)
        verify(world.locator, never()).findObjects(any(Locatable::class.java), anyInt(), any())
        assertTrue(walked.isEmpty())
    }

    private fun restored(course: AgilityBotCourse, step: Int, start: Position): AgilityBotScript {
        position = start
        SubZone.updateSubZone(bot)
        return AgilityBotScript(bot, AgilityBotScript.Companion.AgilityData().also {
            it.course = course.name
            it.duration = 10.minutes
            it.step = step
        })
    }

    private suspend fun traverse(course: AgilityBotCourse, step: Int, start: Position,
                                 objectId: Int, objectPosition: Position, entry: Position, landing: Position) {
        val obstacle = mock(GameObject::class.java)
        `when`(obstacle.id).thenReturn(objectId)
        `when`(obstacle.position).thenReturn(objectPosition)
        `when`(obstacle.abs()).thenReturn(objectPosition)
        `when`(obstacle.state).thenReturn(EntityState.ACTIVE)
        objects = setOf(obstacle)
        val handler = bot.actionHandler
        val interactions = handler.interactions
        doReturn(false).`when`(handler).webWalkTo(any(SubZone::class.java) ?: course.zone,
            anyInt(), anyInt(), any(BankAccess::class.java) ?: BankAccess.NONE, anyBoolean())
        `when`(handler.webWalkTo(any(Position::class.java) ?: entry, eq(0), eq(1),
            eq(BankAccess.NONE) ?: BankAccess.NONE, eq(true))).thenAnswer { call ->
            position = call.getArgument(0)
            walked += position
            SubZone.updateSubZone(bot)
            true
        }
        `when`(interactions.interact(1, obstacle)).thenAnswer {
            position = landing
            SubZone.updateSubZone(bot)
            true
        }
        val script = restored(course, step, start)
        bot.courseProgress[course.course] = step
        assertFalse(script.init(true))

        assertFalse(script.run())

        assertEquals(listOf(entry), walked)
        assertEquals(landing, position)
        assertEquals(step + 1, script.step)
        assertEquals(0, script.failures)
        assertNull(script.landing)
        assertEquals(listOf(course.zone), script.zones)
        assertEquals(step, bot.courseProgress[course.course])
        assertTrue(course.zone in bot.subZones)
        verify(interactions).interact(1, obstacle)
        verify(handler, never()).webWalkTo(any(SubZone::class.java) ?: course.zone,
            anyInt(), anyInt(), any(BankAccess::class.java) ?: BankAccess.NONE, anyBoolean())
        verify(bot.skill(SKILL_AGILITY), never()).addExperience(anyDouble())
    }
}
