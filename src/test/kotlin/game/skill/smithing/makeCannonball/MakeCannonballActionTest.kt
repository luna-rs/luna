package game.skill.smithing.makeCannonball

import api.bot.script.InventoryProductionFixtures
import api.predef.*
import game.skill.smithing.makeCannonball.MakeCannonballAction.Companion.AMMO_MOULD
import game.skill.smithing.makeCannonball.MakeCannonballAction.Companion.CANNONBALL
import game.skill.smithing.makeCannonball.MakeCannonballAction.Companion.STEEL_BAR
import io.luna.game.action.ActionState
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*

class MakeCannonballActionTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun bot(bars: Int = 1, cannonballs: Int = 0): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.state).thenReturn(EntityState.ACTIVE)
        `when`(it.smithing.level).thenReturn(35)
        val inventory = spy(it.inventory)
        doReturn(true).`when`(inventory).stackable(CANNONBALL)
        `when`(it.inventory).thenReturn(inventory)
        InventoryProductionFixtures.inventory(it, Item(STEEL_BAR, bars), Item(AMMO_MOULD))
        if (cannonballs > 0) InventoryProductionFixtures.inventory(it, Item(CANNONBALL, cannonballs))
    }

    private fun start(bot: Bot): MakeCannonballAction = MakeCannonballAction(bot).also { bot.actions.submit(it) }

    private fun reach(bot: Bot, action: MakeCannonballAction, executions: Int) {
        repeat(20) {
            if (action.executions < executions && action.state == ActionState.PROCESSING) bot.actions.process()
        }
        assertEquals(executions, action.executions)
        assertEquals(ActionState.PROCESSING, action.state)
    }

    private fun finish(bot: Bot, action: MakeCannonballAction) {
        repeat(20) { if (action.state == ActionState.PROCESSING) bot.actions.process() }
        assertNotEquals(ActionState.PROCESSING, action.state)
        bot.actions.process()
    }

    @Test fun heatingPouringAndCoolingKeepTheirDelaysAndOnlyCompletionAwardsXp() {
        val bot = bot()
        val action = start(bot)
        bot.actions.process()
        assertEquals(1, action.executions)
        assertEquals(5, action.delay)
        repeat(4) { bot.actions.process() }
        assertEquals(1, action.executions)
        assertTrue(bot.inventory.contains(STEEL_BAR))
        bot.actions.process()
        assertEquals(2, action.executions)
        assertEquals(1, action.delay)
        bot.actions.process()
        assertEquals(3, action.executions)
        assertEquals(3, action.delay)
        repeat(2) { bot.actions.process() }
        assertFalse(bot.inventory.contains(CANNONBALL))
        verify(bot.smithing, never()).addExperience(anyDouble())
        bot.actions.process()
        assertEquals(ActionState.COMPLETED, action.state)
        assertEquals(4, bot.inventory.computeAmountForId(CANNONBALL))
        assertFalse(bot.inventory.contains(STEEL_BAR))
        assertTrue(bot.inventory.contains(AMMO_MOULD))
        verify(bot.smithing).addExperience(25.6)
        verify(bot).unlock()
        repeat(4) { bot.actions.process() }
        verify(bot.smithing, times(1)).addExperience(25.6)
    }

    @Test fun fullInventoriesCreateOrExtendOneCannonballStackAndPreserveOtherBarsAndMould() {
        for (existing in listOf(0, 12)) {
            val bars = if (existing == 0) 27 else 26
            val bot = bot(bars, existing)
            assertTrue(bot.inventory.isFull)
            val action = start(bot)
            finish(bot, action)
            assertEquals(ActionState.COMPLETED, action.state)
            assertEquals(existing + 4, bot.inventory.computeAmountForId(CANNONBALL))
            assertEquals(bars - 1, bot.inventory.computeAmountForId(STEEL_BAR))
            assertEquals(1, bot.inventory.computeAmountForId(AMMO_MOULD))
            assertEquals(if (existing == 0) 0 else 1, bot.inventory.computeRemainingSize())
            verify(bot.smithing).addExperience(25.6)
            verify(bot).unlock()
        }
    }

    @Test fun invalidStartingRequirementsTerminateBeforeHeatingOrConsumption() {
        for (missing in listOf("level", "mould", "bar", "dead", "inactive")) {
            val bot = bot()
            invalidate(bot, missing)
            val action = start(bot)
            finish(bot, action)
            assertEquals(ActionState.INTERRUPTED, action.state)
            assertFalse(bot.inventory.contains(CANNONBALL))
            if (missing != "bar") assertTrue(bot.inventory.contains(STEEL_BAR))
            verify(bot.smithing, never()).addExperience(anyDouble())
            verify(bot, never()).sendMessage("You heat the steel bar into a liquid state.")
            verify(bot).unlock()
        }
    }

    private fun invalidate(bot: Bot, missing: String) {
        when (missing) {
            "level" -> `when`(bot.smithing.level).thenReturn(34)
            "mould" -> bot.inventory.remove(AMMO_MOULD)
            "bar" -> bot.inventory.remove(STEEL_BAR)
            "dead" -> `when`(bot.health).thenReturn(0)
            "inactive" -> `when`(bot.state).thenReturn(EntityState.INACTIVE)
        }
    }

    @Test fun everyStageCancelsImmediatelyWhenRequirementsAreLostAndReleasesItsLock() {
        for (stage in 1..3) for (missing in listOf("level", "mould", "bar", "dead", "inactive")) {
            val bot = bot()
            val action = start(bot)
            reach(bot, action, stage)
            invalidate(bot, missing)
            bot.actions.process()
            assertEquals(ActionState.INTERRUPTED, action.state)
            assertFalse(bot.inventory.contains(CANNONBALL))
            if (missing != "bar") assertTrue(bot.inventory.contains(STEEL_BAR))
            if (missing != "mould") assertTrue(bot.inventory.contains(AMMO_MOULD))
            verify(bot.smithing, never()).addExperience(anyDouble())
            verify(bot).unlock()
            repeat(10) { bot.actions.process() }
            assertFalse(bot.inventory.contains(CANNONBALL))
        }
    }

    @Test fun stackOverflowIsCheckedAtCompletionWithoutLosingSteelOrAwardingXp() {
        for (late in listOf(false, true)) for (amount in listOf(Int.MAX_VALUE - 4, Int.MAX_VALUE - 3, Int.MAX_VALUE)) {
            val bot = bot(cannonballs = if (late) 0 else amount)
            val action = start(bot)
            if (late) {
                reach(bot, action, 3)
                InventoryProductionFixtures.inventory(bot, Item(CANNONBALL, amount))
            }
            finish(bot, action)
            val successful = amount == Int.MAX_VALUE - 4
            assertEquals(if (successful) Int.MAX_VALUE else amount, bot.inventory.computeAmountForId(CANNONBALL))
            assertEquals(!successful, bot.inventory.contains(STEEL_BAR))
            assertTrue(bot.inventory.contains(AMMO_MOULD))
            verify(bot.smithing, times(if (successful) 1 else 0)).addExperience(25.6)
            verify(bot).unlock()
        }
    }

    @Test fun failedAdditionsRestoreUnconvertedSteelAndExceptionsReleaseTheLockWithoutDuplicateProducts() {
        for (failure in listOf("false", "before", "after")) {
            val bot = bot()
            val inventory = bot.inventory
            val product = Item(CANNONBALL, 4)
            when (failure) {
                "false" -> doReturn(false).`when`(inventory).add(product)
                "before" -> doThrow(IllegalStateException("fixture failure")).`when`(inventory).add(product)
                "after" -> doAnswer {
                    it.callRealMethod()
                    throw IllegalStateException("fixture failure after storage")
                }.`when`(inventory).add(product)
            }
            val action = start(bot)
            finish(bot, action)
            assertEquals(if (failure == "false") ActionState.COMPLETED else ActionState.INTERRUPTED, action.state)
            assertEquals(if (failure == "after") 4 else 0, inventory.computeAmountForId(CANNONBALL))
            assertEquals(failure != "after", inventory.contains(STEEL_BAR))
            if (failure != "after") assertEquals(STEEL_BAR, inventory.computeIdForIndex(0))
            assertTrue(inventory.contains(AMMO_MOULD))
            verify(bot.smithing, never()).addExperience(anyDouble())
            verify(bot).unlock()
        }
    }

    @Test fun explicitInterruptionDuringCoolingKeepsSuppliesAndCannotResumeProduction() {
        val bot = bot()
        val action = start(bot)
        reach(bot, action, 3)
        action.interrupt()
        repeat(20) { bot.actions.process() }
        assertEquals(ActionState.INTERRUPTED, action.state)
        assertTrue(bot.inventory.containsAll(STEEL_BAR, AMMO_MOULD))
        assertFalse(bot.inventory.contains(CANNONBALL))
        verify(bot.smithing, never()).addExperience(anyDouble())
        verify(bot).unlock()
    }
}
