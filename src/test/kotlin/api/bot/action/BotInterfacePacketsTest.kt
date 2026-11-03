package api.bot.action

import api.bot.script.InventoryProductionFixtures
import com.google.common.collect.ImmutableList
import io.luna.game.event.impl.ItemClickEvent.ItemFifthClickEvent
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.io.BotClient
import io.luna.game.model.mob.bot.io.BotOutputMessageHandler
import io.luna.game.model.mob.overlay.AbstractOverlaySet
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.net.msg.GameMessage
import io.luna.net.msg.`in`.ArrangeItemMessageReader
import io.luna.net.msg.`in`.ItemClickMessageReader
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*

/**
 * Retains native inventory and arrange-item packets and interface ownership checks independently of minigames.
 *
 * @author lare96
 */
class BotInterfacePacketsTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun initialize() {
            InventoryProductionFixtures.initialize()
            for (id in listOf(10380, 10617)) {
                val definition = mock(WidgetDefinition::class.java)
                `when`(definition.id()).thenReturn(id)
                `when`(definition.children).thenReturn(ImmutableList.copyOf(when (id) {
                    10380 -> listOf(10494)
                    else -> (10736..10742).toList()
                }))
                WidgetDefinition.ALL.storeDefinition(definition)
            }
            val grid = mock(WidgetDefinition::class.java)
            `when`(grid.id()).thenReturn(10494)
            `when`(grid.type).thenReturn(WidgetDefinition.WidgetType.ITEM_GRID)
            WidgetDefinition.ALL.storeDefinition(grid)
        }
    }

    private fun bot(): Bot = InventoryProductionFixtures.bot().also {
        `when`(it.overlays).thenReturn(AbstractOverlaySet(it))
    }

    @Test
    fun nativeDragPacketRoundTripsThroughThePlayerReader() {
        val bot = bot()
        val client = mock(BotClient::class.java)
        `when`(client.bot).thenReturn(bot)
        val output = BotOutputMessageHandler(client)
        assertFalse(output.sendArrangeItem(10494, -1, 33))
        assertFalse(output.sendArrangeItem(10494, 40, 40))
        assertFalse(output.sendArrangeItem(40000, 40, 33))
        assertFalse(output.sendArrangeItem(10494, 40, 65536))
        assertTrue(output.sendArrangeItem(10494, 40, 33))
        val captured = ArgumentCaptor.forClass(GameMessage::class.java)
        verify(client).queueSimulated(captured.capture())
        val packet = captured.value
        try {
            assertEquals(123, packet.opcode)
            assertEquals(7, packet.size)
            val reader = ArrangeItemMessageReader()
            val event = reader.decode(bot, packet)
            assertTrue(reader.validate(bot, event))
            assertEquals(10494, event.widgetId)
            assertEquals(40, event.fromIndex)
            assertEquals(33, event.toIndex)
            assertEquals(0, event.insertionMode)
            assertEquals(0, packet.payload.buffer.readableBytes())
        } finally { packet.payload.buffer.release() }
    }

    @Test
    fun widgetActionsRejectClosedAndUnrelatedInterfaces() {
        val bot = bot()
        val handler = BotWidgetActionHandler(bot, mock(BotActionHandler::class.java))
        assertFalse(handler.clickButton(10617, 10736))
        assertFalse(handler.dragItem(10380, 10494, 40, 33))
        bot.overlays.open(StandardInterface(10617))
        assertFalse(handler.clickButton(10617, 2458))
        assertFalse(handler.dragItem(10380, 10494, 40, 33))
        `when`(bot.output.clickButton(10739)).thenReturn(true)
        assertTrue(handler.clickButton(10617, 10739))
        bot.overlays.open(StandardInterface(10380))
        assertFalse(handler.dragItem(10380, 10494, -1, 33))
        assertFalse(handler.dragItem(10380, 10494, 40, 40))
        assertFalse(handler.dragItem(10380, 10380, 40, 33))
        `when`(bot.output.sendArrangeItem(10494, 40, 33)).thenReturn(true)
        assertTrue(handler.dragItem(10380, 10494, 40, 33))
        verify(bot.output).clickButton(10739)
        verify(bot.output).sendArrangeItem(10494, 40, 33)
        verifyNoMoreInteractions(bot.output)
    }

    @Test
    fun fifthInventoryActionUsesTheSameNativePacketLayout() {
        val bot = bot()
        val client = mock(BotClient::class.java)
        `when`(client.bot).thenReturn(bot)
        val output = BotOutputMessageHandler(client)
        assertTrue(output.sendInventoryItemClick(5, 17, 952))
        val captor = ArgumentCaptor.forClass(GameMessage::class.java)
        verify(client).queueSimulated(captor.capture())
        val event = ItemClickMessageReader().decode(bot, captor.value)
        assertTrue(event is ItemFifthClickEvent)
        assertEquals(952, event.id)
        assertEquals(17, event.index)
        assertEquals(3214, event.interfaceId)
    }
}
