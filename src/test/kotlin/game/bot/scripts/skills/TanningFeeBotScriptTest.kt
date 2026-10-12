package game.bot.scripts.skills

import api.bot.script.InventoryProductionFixtures
import game.skill.crafting.hideTanning.Hide
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockito.Mockito.*
import kotlin.time.Duration.Companion.minutes

class TanningFeeBotScriptTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private fun bot(): Bot = InventoryProductionFixtures.bot().also {
        val inventory = spy(it.inventory)
        doReturn(true).`when`(inventory).stackable(995)
        `when`(it.inventory).thenReturn(inventory)
    }

    private fun script(bot: Bot, hide: Hide = Hide.SOFT_LEATHER): TanHideBotScript {
        val script = InventoryProductionFixtures.active(TanHideBotScript(bot, 10.minutes))
        TanHideBotScript::class.java.getDeclaredField("tanning").apply { isAccessible = true; set(script, hide) }
        return script
    }

    @ParameterizedTest @EnumSource(Hide::class)
    fun tanAllRequiresTheEntireCarriedFeeBeforeInteracting(hide: Hide) = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(hide.hide, 3), Item(995, hide.cost * 3 - 1))
        val script = script(bot, hide)
        assertTrue(script.executeInZone())
        assertTrue(script.onBankRequested(false))
        assertFalse(script.isTerminated())
        verifyNoInteractions(bot.actionHandler)
    }

    @ParameterizedTest @EnumSource(Hide::class)
    fun exactCarriedFeeAvoidsAnUnnecessaryBankTrip(hide: Hide) = runBlocking<Unit> {
        val bot = bot()
        InventoryProductionFixtures.inventory(bot, Item(hide.hide, 3), Item(995, hide.cost * 3))
        assertFalse(script(bot, hide).onBankRequested(false))
    }

}
