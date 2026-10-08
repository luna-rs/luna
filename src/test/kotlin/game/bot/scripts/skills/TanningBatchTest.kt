package game.bot.scripts.skills

import api.predef.crafting
import engine.bot.coordinator.skill.ThievingScriptFactoryTest
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.game.model.item.Item
import io.luna.game.model.mob.attr.AttributeMap
import io.luna.game.model.mob.bot.Bot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class TanningBatchTest {
    companion object {
        @JvmStatic @BeforeAll
        fun fixtures() = ThievingScriptFactoryTest.initializeScriptFixtures()
    }

    private fun bot(): Bot = mock(Bot::class.java, RETURNS_DEEP_STUBS).also {
        `when`(it.attributes()).thenReturn(AttributeMap())
    }

    @Test fun oneBankedCowhideDoesNotRequireFullInventory() {
        val bot = bot()
        `when`(bot.crafting.staticLevel).thenReturn(1)
        `when`(bot.bank.iterator()).thenAnswer { mutableListOf<Item?>(Item(1739)).iterator() }
        bot.itemTracker.add(1739, 1)
        bot.itemTracker.add(995, 799)
        val items = TanHideBotScript(bot, 10.minutes).withdraw()
        assertEquals(1, items.first { it.id == 1739 }.amount)
        assertEquals(2, items.first { it.id == 995 }.amount)
    }

    @Test fun tanningBatchHonoursCoinsAndUpdatesOnNextBankingCycle() {
        val bot = bot()
        // A dragon hide avoids the cowhide hard/soft random branch.
        `when`(bot.bank.iterator()).thenAnswer { mutableListOf<Item?>(Item(1753, 30)).iterator() }
        bot.itemTracker.add(1753, 30)
        bot.itemTracker.add(995, 40)
        val script = TanHideBotScript(bot, 10.minutes)
        assertEquals(listOf(Item(995, 40), Item(1753, 2)), script.withdraw())
        bot.itemTracker.remove(995, 20)
        val nextBatch = TanHideBotScript::class.java.getDeclaredMethod("bankWithdraw").apply { isAccessible = true }
        assertEquals(listOf(Item(995, 20), Item(1753, 1)), nextBatch.invoke(script))
    }

}
