package game.bot.scripts.skills

import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.script.InventoryBotScript
import api.bot.zone.SubZone
import api.predef.*
import engine.bot.coordinator.skill.CookingScriptFactory
import engine.bot.coordinator.skill.SmithingScriptFactory
import engine.bot.coordinator.skill.ThievingScriptFactoryTest
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.cooking.cookFood.Food
import game.skill.magic.lowHighAlch.AlchemyType
import io.luna.game.model.mob.Spellbook
import game.skill.smithing.BarType
import game.skill.smithing.smithBar.SmithingTable
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.attr.AttributeMap
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.ArrayDeque
import java.util.concurrent.Executor
import kotlin.time.Duration.Companion.minutes

class ProductionBotRegressionTest {
    companion object {
        @JvmStatic @BeforeAll
        fun fixtures() = ThievingScriptFactoryTest.initializeScriptFixtures()
    }

    private fun bot(): Bot = mock(Bot::class.java, RETURNS_DEEP_STUBS).also {
        `when`(it.attributes()).thenReturn(AttributeMap())
        `when`(it.smithing.staticLevel).thenReturn(99)
        `when`(it.cooking.staticLevel).thenReturn(99)
        `when`(it.inventory.capacity()).thenReturn(28)
    }

    @Test fun oreOnlyTrainingSelectsSmeltingInsteadOfUnavailableBars() {
        val bot = bot()
        bot.itemTracker.add(436, 100)
        bot.itemTracker.add(438, 100)
        val script = SmithingScriptFactory.getTrainingScript(bot, 99, mutableListOf())
        assertInstanceOf(SmeltOreBotScript::class.java, script)
        assertEquals(BarType.BRONZE, (script as SmeltOreBotScript).selectedBar)
        assertNull(SmithingScriptFactory.getSmithingScript(bot, 99))
    }

    @Test fun ownedBarsSelectAnAffordableSmithingRecipe() {
        val bot = bot()
        bot.itemTracker.add(2349, 100)
        val script = SmithingScriptFactory.getTrainingScript(bot, 99, mutableListOf()) as SmithBarBotScript
        assertTrue(script.selectedItems.all { it.barType == BarType.BRONZE })
    }

    @Test fun highLevelCookingUsesOwnedShrimp() {
        val bot = bot()
        bot.itemTracker.add(Food.SHRIMP.raw, 100)
        val script = CookingScriptFactory.getTrainingScript(bot, 99, mutableListOf()) as CookFoodBotScript
        assertEquals(Food.SHRIMP, script.selectedFood)
    }

    @Test fun cookingSkipsFoodAboveCurrentLevel() {
        val bot = bot()
        `when`(bot.cooking.staticLevel).thenReturn(1)
        bot.itemTracker.add(Food.SHARK.raw, 100)
        bot.itemTracker.add(Food.SHRIMP.raw, 10)
        assertEquals(listOf(Item(Food.SHRIMP.raw, 10)),
            CookFoodBotScript(bot, null, 10.minutes, mutableListOf(SubZone.AL_KHARID_BANK)).withdraw())
    }

    @Test fun smeltingBalancesSmallCoalAndOreSupplies() {
        val bot = bot()
        bot.itemTracker.add(440, 10)
        bot.itemTracker.add(453, 5)
        val script = SmeltOreBotScript(bot, BarType.STEEL, 10.minutes, mutableListOf(SubZone.AL_KHARID_BANK))
        assertEquals(BarType.STEEL.oreList.map { Item(it.id, it.amount * 2) }, script.withdraw())
        bot.itemTracker.remove(453, 2)
        assertEquals(BarType.STEEL.oreList.map { Item(it.id, it.amount) }, script.withdraw())
    }

    @Test fun smithingWithdrawsSmallStockAndKeepsHammerSlot() {
        val bot = bot()
        bot.itemTracker.add(2349, 10)
        val script = SmithBarBotScript(bot, mutableListOf(SmithingTable.DAGGER.items.first()), 10.minutes)
        assertEquals(listOf(Item(2347), Item(2349, 10)), script.withdraw())
        bot.itemTracker.remove(2349, 7)
        assertEquals(listOf(Item(2347), Item(2349, 3)), script.withdraw())
    }

    @Test fun cookingDoesNotBankWhileRawInputsRemain() = runBlocking {
        val bot = bot()
        bot.itemTracker.add(Food.SHRIMP.raw, 28)
        val script = CookFoodBotScript(bot, Food.SHRIMP, 10.minutes, mutableListOf(SubZone.AL_KHARID_BANK))
        script.withdraw()
        `when`(bot.inventory.contains(Food.SHRIMP.raw)).thenReturn(true)
        assertFalse(script.onBankRequested(false))
        `when`(bot.inventory.contains(Food.SHRIMP.raw)).thenReturn(false)
        assertTrue(script.onBankRequested(false))
    }

    @Test fun weakProductionActionsBlockBankingButInitialSetupStillBanks() = runBlocking {
        val bot = bot()
        val script = object : InventoryBotScript(bot, 10.minutes, mutableListOf(SubZone.HOME)) {
            override fun withdraw() = listOf(Item(436))
            override fun snapshot(): BotScriptData? = null
        }
        `when`(bot.actions.size(ActionType.WEAK)).thenReturn(1)
        assertFalse(script.onBankRequested(false))
        assertTrue(script.onBankRequested(true))
        `when`(bot.actions.size(ActionType.WEAK)).thenReturn(0)
        assertTrue(script.onBankRequested(false))
    }

    @Test fun alchemyStartupAcceptsBankedRunes() = runBlocking {
        val bot = bot()
        `when`(bot.spellbook).thenReturn(Spellbook.REGULAR)
        `when`(bot.magic.level).thenReturn(99)
        `when`(bot.bank.computeAmountForId(1117)).thenReturn(10)
        `when`(bot.bank.computeAmountForId(554)).thenReturn(50)
        bot.itemTracker.add(561, 10)
        bot.itemTracker.add(554, 50)
        assertTrue(AlchemyBotScript(bot, AlchemyType.HIGH, 10.minutes).onInit(false))
    }

    @Test fun alchemyStillRejectsMissingNatureRunes() = runBlocking {
        val bot = bot()
        `when`(bot.spellbook).thenReturn(Spellbook.REGULAR)
        `when`(bot.magic.level).thenReturn(99)
        `when`(bot.bank.computeAmountForId(1117)).thenReturn(10)
        assertFalse(AlchemyBotScript(bot, AlchemyType.HIGH, 10.minutes).onInit(false))
        verify(bot.preferences).addWantedItem(561, 10)
    }

    @Test fun pausedScriptResumesAndExplicitStopStillTerminates() {
        val queue = ArrayDeque<Runnable>()
        val executor = gameService.gameExecutor
        `when`(gameService.gameExecutor).thenReturn(Executor { queue.addLast(it) })
        fun drain() { while (queue.isNotEmpty()) queue.removeFirst().run() }
        val bot = bot()
        `when`(bot.state).thenReturn(EntityState.ACTIVE)
        val script = object : BotScript(bot) {
            val resumes = mutableListOf<Boolean>()
            var finishes = 0
            override suspend fun init(resumed: Boolean): Boolean { resumes += resumed; return false }
            override suspend fun run(): Boolean = awaitCancellation()
            override suspend fun finish() { finishes++ }
            override fun snapshot(): BotScriptData? = null
        }
        try {
            assertTrue(script.start()); drain()
            assertTrue(script.pause()); drain()
            assertTrue(script.isPaused()); assertFalse(script.isTerminated())
            assertTrue(script.start()); drain()
            assertEquals(listOf(false, true), script.resumes)
            assertTrue(script.stop()); drain()
            assertTrue(script.isTerminated()); assertFalse(script.start())
            assertEquals(2, script.finishes)
        } finally {
            script.stop(); drain()
            `when`(gameService.gameExecutor).thenReturn(executor)
        }
    }
}
