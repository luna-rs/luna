package game.skill.woodcutting.cutTree

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import game.skill.woodcutting.searchNest.Nest
import io.luna.Luna
import io.luna.LunaSettings
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.World
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.GroundItemList
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import io.luna.game.task.Task
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.MockedStatic
import org.mockito.Mockito.*
import java.util.concurrent.ThreadLocalRandom

/**
 * Exercises successful and failed chops through the inventory action, including independent nest drops.
 *
 * @author lare96
 */
class CutTreeActionTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private lateinit var luna: MockedStatic<Luna>
    private lateinit var functions: MockedStatic<*>
    private lateinit var random: ThreadLocalRandom
    private lateinit var fixtureWorld: World
    private lateinit var fixtureItems: GroundItemList
    private val drops = mutableListOf<GroundItem>()
    private val tasks = mutableListOf<Task>()

    @BeforeEach
    fun prepare() {
        val settings = mock(LunaSettings::class.java, RETURNS_DEEP_STUBS)
        `when`(settings.skills().woodcuttingSpeed()).thenReturn(4)
        luna = mockStatic(Luna::class.java, CALLS_REAL_METHODS)
        luna.`when`<LunaSettings> { Luna.settings() }.thenReturn(settings)
        random = mock(ThreadLocalRandom::class.java)
        functions = mockStatic(Class.forName("api.predef.FunctionsPredefKt"), CALLS_REAL_METHODS)
        functions.`when`<ThreadLocalRandom> { rand() }.thenReturn(random)
        `when`(random.nextDouble()).thenReturn(0.0)
        `when`(random.nextInt(256)).thenReturn(0)
        `when`(random.nextInt(8)).thenReturn(1)
        fixtureWorld = world
        fixtureItems = fixtureWorld.items
        doAnswer { invocation -> drops += invocation.getArgument<GroundItem>(0); true }
            .`when`(fixtureItems).register(any(GroundItem::class.java))
        doAnswer { invocation -> tasks += invocation.getArgument<Task>(0); null }
            .`when`(fixtureWorld).schedule(any(Task::class.java))
    }

    @AfterEach
    fun restore() {
        functions.close()
        luna.close()
        doNothing().`when`(fixtureWorld).schedule(any(Task::class.java))
        doReturn(false).`when`(fixtureItems).register(any(GroundItem::class.java))
        clearInvocations(fixtureItems, fixtureWorld)
    }

    private fun bot(): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.skill(SKILL_WOODCUTTING).level).thenReturn(99)
        doReturn(Position(3200, 3200)).`when`(it).position
        InventoryProductionFixtures.inventory(it, Item(Axe.RUNE.id))
    }

    private fun chop(bot: Bot, tree: Tree): CutTreeAction {
        val obj = mock(GameObject::class.java, RETURNS_DEEP_STUBS)
        `when`(obj.id).thenReturn(TreeStump.ALIVE_TREE_MAP[tree].first())
        doReturn(Position(3200, 3201)).`when`(obj).position
        `when`(obj.state).thenReturn(EntityState.ACTIVE)
        val action = CutTreeAction(bot, Axe.RUNE, tree, obj)
        bot.actions.submit(action)
        action.run()
        return action
    }

    @Test
    fun nestsAccompanyLogsAndExperienceForEveryTreeEvenWhenTheLastInventorySlotFills() {
        for (tree in Tree.entries) {
            drops.clear()
            tasks.clear()
            val bot = bot()
            repeat(26) { InventoryProductionFixtures.inventory(bot, Item(1925)) }
            assertEquals(1, bot.inventory.computeRemainingSize())
            chop(bot, tree)
            assertEquals(1, bot.inventory.computeAmountForId(tree.logId), tree.name)
            assertTrue(bot.inventory.isFull, tree.name)
            verify(bot.woodcutting).addExperience(tree.exp)
            assertEquals(1, drops.size, tree.name)
            assertTrue(Nest.isNest(drops.single().id))
        }
    }

    @Test
    fun aFailedChopAwardsNeitherLogsExperienceNorANest() {
        `when`(random.nextDouble()).thenReturn(1.0)
        val bot = bot()
        chop(bot, Tree.MAGIC)
        assertFalse(bot.inventory.contains(Tree.MAGIC.logId))
        verify(bot.woodcutting, never()).addExperience(anyDouble())
        assertTrue(drops.isEmpty())
        assertTrue(tasks.isEmpty())
    }

    @Test
    fun nestsRemainPrivateAndTheirFiftyTickExpiryRemovesOnlyTheOriginalDrop() {
        val bot = bot()
        chop(bot, Tree.OAK)
        val nest = drops.single()
        assertTrue(nest.isLocal)
        assertTrue(nest.view.isViewableFor(bot))
        assertEquals(bot.position, nest.position)
        assertFalse(nest.isExpiring)
        val expiry = tasks.single()
        assertEquals(50, expiry.delay)
        val replacement = GroundItem(ctx, nest.id, 1, nest.position, nest.view)
        world.items.register(replacement)
        verify(world.items, never()).expire(any(GroundItem::class.java))
        expiry.javaClass.getDeclaredMethod("execute").apply { isAccessible = true }.invoke(expiry)
        verify(world.items).expire(same(nest))
        verify(world.items, never()).expire(same(replacement))
    }
}
