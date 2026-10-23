package game.skill.cooking.cookFood

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import game.skill.cooking.cookFood.FermentWineTask.Companion.wineFermentCounter
import game.skill.cooking.cookFood.MakeWineActionItem.Companion.WINE
import game.skill.cooking.cookFood.MakeWineActionItem.Companion.wineFermentTask
import io.luna.game.model.EntityState
import io.luna.game.model.item.DynamicItem
import io.luna.game.model.item.Item
import io.luna.game.task.Task
import io.luna.game.task.TaskManager
import io.luna.game.task.TaskState
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/** Real inventory and task-manager regressions for wine mixing and fermentation lifecycle. */
class WineFermentationTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun mixingStartsOneTaskAndReusesItUntilFermentationFinishes() {
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.cooking.level).thenReturn(WINE.lvl)
        val fixtureWorld = world
        val manager = TaskManager()
        val scheduled = mutableListOf<Task>()
        doAnswer { invocation ->
            val task = invocation.getArgument<Task>(0)
            scheduled += task
            manager.schedule(task)
            null
        }.`when`(fixtureWorld).schedule(any(Task::class.java))
        try {
            assertNull(bot.wineFermentTask)
            InventoryProductionFixtures.inventory(bot, Item(1987, 2), Item(1937, 2))
            InventoryProductionFixtures.execute(bot, MakeWineActionItem(bot, 1))
            val first = bot.wineFermentTask!!
            assertEquals(TaskState.RUNNING, first.state)
            InventoryProductionFixtures.execute(bot, MakeWineActionItem(bot, 1))
            assertSame(first, bot.wineFermentTask)
            assertEquals(1, scheduled.size)
            assertEquals(2, bot.inventory.computeAmountForId(1995))
            verify(bot.cooking, never()).addExperience(anyDouble())
            repeat(20) { manager.runTaskIteration() }
            assertEquals(2, bot.inventory.computeAmountForId(1993))
            assertEquals(TaskState.CANCELLED, first.state)
            verify(bot.cooking, times(2)).addExperience(WINE.exp)
            InventoryProductionFixtures.inventory(bot, Item(1987), Item(1937))
            InventoryProductionFixtures.execute(bot, MakeWineActionItem(bot, 1))
            assertNotSame(first, bot.wineFermentTask)
            assertEquals(TaskState.RUNNING, bot.wineFermentTask!!.state)
            assertEquals(2, scheduled.size)
        } finally {
            doNothing().`when`(fixtureWorld).schedule(any(Task::class.java))
        }
    }

    @Test fun idleTaskIsReplacedAndScheduledWhenMixingWine() {
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.cooking.level).thenReturn(WINE.lvl)
        val previous = FermentWineTask(bot)
        bot.wineFermentTask = previous
        val fixtureWorld = world
        val manager = TaskManager()
        doAnswer { invocation ->
            manager.schedule(invocation.getArgument<Task>(0))
            null
        }.`when`(fixtureWorld).schedule(any(Task::class.java))
        try {
            InventoryProductionFixtures.inventory(bot, Item(1987), Item(1937))
            InventoryProductionFixtures.execute(bot, MakeWineActionItem(bot, 1))
            assertNotSame(previous, bot.wineFermentTask)
            assertEquals(TaskState.RUNNING, bot.wineFermentTask!!.state)
        } finally {
            doNothing().`when`(fixtureWorld).schedule(any(Task::class.java))
        }
    }

    @Test fun inventoryAndBankWinesKeepIndependentCountersAndAwardExperienceOnlyOnce() {
        val bot = InventoryProductionFixtures.bot()
        val fresh = DynamicItem(1995)
        val older = DynamicItem(1995).also { it.wineFermentCounter = 19 }
        InventoryProductionFixtures.inventory(bot, fresh, Item(995))
        InventoryProductionFixtures.bank(bot, older, Item(1937, 10))
        val manager = TaskManager()
        val task = FermentWineTask(bot)
        manager.schedule(task)
        manager.runTaskIteration()
        assertEquals(1, fresh.wineFermentCounter)
        assertEquals(1, bot.bank.computeAmountForId(1993))
        assertEquals(TaskState.RUNNING, task.state)
        verify(bot.cooking, times(1)).addExperience(WINE.exp)
        repeat(18) { manager.runTaskIteration() }
        assertEquals(19, fresh.wineFermentCounter)
        assertTrue(bot.inventory.contains(1995))
        manager.runTaskIteration()
        assertEquals(1, bot.inventory.computeAmountForId(1993))
        assertEquals(TaskState.CANCELLED, task.state)
        repeat(5) { manager.runTaskIteration() }
        verify(bot.cooking, times(2)).addExperience(WINE.exp)
        assertEquals(1, bot.inventory.computeAmountForId(995))
        assertEquals(10, bot.bank.computeAmountForId(1937))
    }

    @Test fun movingWineToTheBankPreservesItsProgress() {
        val bot = InventoryProductionFixtures.bot()
        val wine = DynamicItem(1995)
        InventoryProductionFixtures.inventory(bot, wine)
        val manager = TaskManager()
        val task = FermentWineTask(bot)
        manager.schedule(task)
        repeat(10) { manager.runTaskIteration() }
        assertTrue(bot.inventory.remove(wine))
        InventoryProductionFixtures.bank(bot, wine)
        repeat(9) { manager.runTaskIteration() }
        assertEquals(19, wine.wineFermentCounter)
        assertTrue(bot.bank.contains(1995))
        manager.runTaskIteration()
        assertFalse(bot.bank.contains(1995))
        assertEquals(1, bot.bank.computeAmountForId(1993))
        assertEquals(TaskState.CANCELLED, task.state)
        verify(bot.cooking, times(1)).addExperience(WINE.exp)
    }

    @Test fun emptySlotsAndUnrelatedItemsDoNotKeepFermentationRunning() {
        for (withItems in listOf(false, true)) {
            val bot = InventoryProductionFixtures.bot()
            if (withItems) {
                InventoryProductionFixtures.inventory(bot, Item(995), Item(1995))
                InventoryProductionFixtures.bank(bot, Item(1937, 10))
            }
            val task = FermentWineTask(bot)
            val manager = TaskManager()
            manager.schedule(task)
            manager.runTaskIteration()
            assertEquals(TaskState.CANCELLED, task.state)
            verify(bot.cooking, never()).addExperience(anyDouble())
            if (withItems) assertTrue(bot.inventory.contains(1995))
        }
    }

    @Test fun logoutStopsFermentationWithoutChangingWineOrExperience() {
        val bot = InventoryProductionFixtures.bot()
        val wine = DynamicItem(1995).also { it.wineFermentCounter = 19 }
        InventoryProductionFixtures.inventory(bot, wine)
        `when`(bot.state).thenReturn(EntityState.INACTIVE)
        val manager = TaskManager()
        val task = FermentWineTask(bot)
        manager.schedule(task)
        manager.runTaskIteration()
        assertEquals(TaskState.CANCELLED, task.state)
        assertEquals(19, wine.wineFermentCounter)
        assertTrue(bot.inventory.contains(1995))
        verify(bot.cooking, never()).addExperience(anyDouble())
    }

    @Test fun invalidMixingPreservesInputsAndDoesNotStartFermentation() {
        for (missing in listOf(1987, 1937, null)) {
            val bot = InventoryProductionFixtures.bot()
            `when`(bot.cooking.level).thenReturn(if (missing == null) WINE.lvl - 1 else WINE.lvl)
            val supplied = listOf(1987, 1937).filter { it != missing }
            InventoryProductionFixtures.inventory(bot, *supplied.map { Item(it) }.toTypedArray())
            InventoryProductionFixtures.execute(bot, MakeWineActionItem(bot, 1))
            for (id in supplied) assertEquals(1, bot.inventory.computeAmountForId(id))
            assertFalse(bot.inventory.contains(1995))
            assertNull(bot.wineFermentTask)
            verify(bot.cooking, never()).addExperience(anyDouble())
        }
    }
}
