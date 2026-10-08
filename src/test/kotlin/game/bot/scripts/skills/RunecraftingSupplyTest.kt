package game.bot.scripts.skills

import engine.bot.coordinator.skill.ThievingScriptFactoryTest
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.runecrafting.enterAltar.Altar
import io.luna.game.model.mob.attr.AttributeMap
import io.luna.game.model.mob.bot.Bot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class RunecraftingSupplyTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = ThievingScriptFactoryTest.initializeScriptFixtures()
    }

    private fun bot() = mock(Bot::class.java, RETURNS_DEEP_STUBS).also {
        `when`(it.attributes()).thenReturn(AttributeMap())
        `when`(it.inventory.capacity()).thenReturn(28)
    }

    @Test fun airAltarUsesPartialLoadsAndCapsFullLoads() {
        for (stock in listOf(1, 26, 27, 40)) {
            val bot = bot()
            bot.itemTracker.add(CraftRuneBotScript.RUNE_ESSENCE, stock)
            bot.itemTracker.add(Altar.AIR.talisman, 1)
            val items = CraftRuneBotScript(bot, Altar.AIR, 10.minutes).withdraw()
            assertEquals(CraftRuneBotScript.RUNE_ESSENCE, items.first().id)
            assertEquals(minOf(stock, 27), items.first().amount)
            assertEquals(Altar.AIR.talisman, items.last().id)
        }
    }

    @Test fun higherLevelAltarUsesPartialPureEssenceAndRejectsNormalEssence() {
        val bot = bot()
        bot.itemTracker.add(CraftRuneBotScript.RUNE_ESSENCE, 100)
        bot.itemTracker.add(Altar.NATURE.talisman, 1)
        assertTrue(CraftRuneBotScript(bot, Altar.NATURE, 10.minutes).withdraw().isEmpty())
        bot.itemTracker.add(CraftRuneBotScript.PURE_ESSENCE, 5)
        val items = CraftRuneBotScript(bot, Altar.NATURE, 10.minutes).withdraw()
        assertEquals(CraftRuneBotScript.PURE_ESSENCE, items.first().id)
        assertEquals(5, items.first().amount)
    }

    @Test fun lowLevelAltarCanUsePureEssenceWhenNormalStockIsEmpty() {
        val bot = bot()
        bot.itemTracker.add(CraftRuneBotScript.PURE_ESSENCE, 7)
        bot.itemTracker.add(Altar.AIR.talisman, 1)
        val items = CraftRuneBotScript(bot, Altar.AIR, 10.minutes).withdraw()
        assertEquals(CraftRuneBotScript.PURE_ESSENCE, items.first().id)
        assertEquals(7, items.first().amount)
    }

    @Test fun emptyEssenceStockStillRejectsTheScript() {
        assertTrue(CraftRuneBotScript(bot(), Altar.AIR, 10.minutes).withdraw().isEmpty())
    }
}
