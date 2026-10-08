package api.bot.action

import api.bot.script.InventoryProductionFixtures
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

class BotMakeItemDialogueTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    @Test fun buttonsMatchEveryPlayerMakeDialogueLayout() = runBlocking {
        val bot = InventoryProductionFixtures.bot()
        `when`(bot.overlays.player).thenReturn(bot)
        val handler = BotWidgetActionHandler(bot, mock(BotActionHandler::class.java))
        val firstButtons = mapOf(1 to 8893, 2 to 8874, 3 to 8889, 4 to 8909, 5 to 8949)
        for ((length, first) in firstButtons) {
            `when`(bot.overlays.getOverlay(MakeItemDialogue::class.java))
                .thenReturn(MakeItemDialogue(*IntArray(length) { 1607 }))
            for (index in 0 until length) {
                for ((amount, offset) in listOf(1 to 0, 5 to 1, 10 to 2)) {
                    clearInvocations(bot.output)
                    handler.clickMakeItem(index, amount)
                    verify(bot.output).clickButton(first + if (length == 1) -offset else index * 4 - offset)
                }
            }
            clearInvocations(bot.output)
            handler.clickMakeItem(length, 1)
            handler.clickMakeItem(-1, 1)
            verifyNoInteractions(bot.output)
        }
    }
}
