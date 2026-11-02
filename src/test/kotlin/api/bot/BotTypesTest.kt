package api.bot

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.bot.Bot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Separates autonomous identity from the ability to host bot scripts.
 *
 * @author lare96
 */
class BotTypesTest {
    @Test fun identityRequiresBothTypeAndFlag() {
        val ordinary = mock(Player::class.java)
        val bot = mock(Bot::class.java)
        val connected = mock(Bot::class.java)
        `when`(bot.isBot).thenReturn(true)
        assertFalse(null.isRealBot())
        assertFalse(ordinary.isRealBot())
        `when`(ordinary.isBot).thenReturn(true)
        assertFalse(ordinary.isRealBot())
        assertTrue(bot.isRealBot())
        assertFalse(connected.isRealBot())
        `when`(connected.isAutomationActive).thenReturn(true)
        assertFalse(connected.isRealBot())
        val player: Player = bot
        if (player.isRealBot()) assertSame(bot.botClient, player.botClient)
        else fail("The helper must preserve a Bot smart cast.")
    }
}
