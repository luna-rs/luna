package game.bot.scripts.skills

import api.bot.action.BotActionHandler
import api.bot.action.BotInventoryActionHandler
import api.predef.world
import engine.bot.coordinator.skill.ThievingScriptFactoryTest
import io.luna.game.model.item.Item
import io.luna.game.model.mob.attr.AttributeMap
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.concurrent.CompletableFuture

class ItemUseNavigationTest {
    companion object {
        @JvmStatic @BeforeAll
        fun fixtures() = ThievingScriptFactoryTest.initializeScriptFixtures()
    }

    private fun bot(): Bot = mock(Bot::class.java, RETURNS_DEEP_STUBS).also {
        `when`(it.attributes()).thenReturn(AttributeMap())
    }

    @Test fun failedNavigationNeverSendsItemOnObjectPacket() = runBlocking {
        val bot = bot()
        val target = mock(GameObject::class.java)
        `when`(bot.inventory.computeIndexForId(436)).thenReturn(0)
        `when`(bot.inventory[0]).thenReturn(Item(436))
        val handler = BotInventoryActionHandler(bot, mock(BotActionHandler::class.java))
        for (result in listOf(NavigationResult.DIDNT_REACH, NavigationResult.NO_VALID_PATH, null)) {
            `when`(bot.navigator.navigate(target, true)).thenReturn(CompletableFuture.completedFuture(result))
            assertFalse(handler.useItem(436).onObject(target))
        }
        verify(bot.output, never()).useItemOnObject(anyInt(), anyInt(), any(GameObject::class.java))
        Unit
    }

    @Test fun reachedNavigationRequiresCurrentCollisionReachBeforeSendingPacket() = runBlocking {
        val bot = bot()
        val target = mock(GameObject::class.java)
        `when`(bot.inventory.computeIndexForId(436)).thenReturn(0)
        `when`(bot.inventory[0]).thenReturn(Item(436))
        `when`(bot.navigator.navigate(target, true)).thenReturn(CompletableFuture.completedFuture(NavigationResult.REACHED))
        val handler = BotInventoryActionHandler(bot, mock(BotActionHandler::class.java))
        `when`(world.collisionManager.reached(bot, target, InteractionPolicy.STANDARD_SIZE)).thenReturn(false)
        assertFalse(handler.useItem(436).onObject(target))
        verify(bot.output, never()).useItemOnObject(anyInt(), anyInt(), any(GameObject::class.java))
        `when`(world.collisionManager.reached(bot, target, InteractionPolicy.STANDARD_SIZE)).thenReturn(true)
        assertTrue(handler.useItem(436).onObject(target))
        verify(bot.output).useItemOnObject(0, 436, target)
        Unit
    }
}
