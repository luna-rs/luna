package game.player

import com.google.gson.JsonParser
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.mob.Player
import io.luna.net.codec.ByteOrder
import io.luna.net.codec.MessageType
import io.luna.net.codec.ValueType
import io.luna.net.msg.GameMessageWriter
import io.luna.net.msg.out.WidgetTextMessageWriter
import io.luna.net.msg.out.WidgetScrollPositionMessageWriter
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.nio.file.Files
import java.nio.file.Path

/**
 * Checks journal output against the revision 377 widget dump and the player's text cache.
 *
 * @author lare96
 */
class QuestJournalInterfaceTest {
    companion object {
        private lateinit var bodyIds: List<Int>

        @JvmStatic
        @BeforeAll
        fun loadJournalWidgets() {
            Files.newBufferedReader(Path.of("data", "dumps", "widgets.json")).use { reader ->
                val scroll = JsonParser.parseReader(reader).asJsonArray
                    .filter { it.isJsonObject }
                    .map { it.asJsonObject }
                    .single { it.get("id").asInt == 8143 }
                bodyIds = scroll.getAsJsonArray("children").map { it.asInt }
            }
            if (WidgetDefinition.ALL[8134].isEmpty) {
                val definition = mock(WidgetDefinition::class.java)
                `when`(definition.id()).thenReturn(8134)
                WidgetDefinition.ALL.storeDefinition(definition)
            }
        }
    }

    @Test
    fun `all one hundred lines target actual journal children`() {
        val journal = QuestJournalInterface("Bot census")
        repeat(100) { journal.addLine("Line ${it + 1}") }
        val output = output(journal)

        assertEquals(100, journal.size)
        assertEquals(listOf(8144) + bodyIds, output.map { it.first })
        assertEquals("Line 50", output.single { it.first == 8195 }.second)
        assertEquals("Line 51", output.single { it.first == 12174 }.second)
        assertEquals("Line 100", output.single { it.first == 12223 }.second)
        assertThrows(IllegalStateException::class.java) { journal.addLine("Too many") }
    }

    @Test
    fun `reopening retains content and refreshes text after client eviction`() {
        val journal = QuestJournalInterface("Bot census")
        journal.addLine("Bots online: 10")
        journal.newLine()
        journal.addLine("PK scripts: 25%")
        val player = player()
        val first = output(journal, player)

        // Replaying onOpen without server-side cleanup models client widget eviction
        // while the player's text cache still holds the previously sent values.
        assertEquals(first, output(journal, player))
        journal.onClose(player)
        assertEquals(first, output(journal, player))
        assertEquals(3, journal.size)
    }

    @Test
    fun `shorter journal clears all unused rows and never touches another interface`() {
        val player = player()
        val longJournal = QuestJournalInterface("Long journal")
        repeat(100) { longJournal.addLine("Old line $it") }
        output(longJournal, player)
        val shortJournal = QuestJournalInterface("Short journal")
        shortJournal.addLine("Only line")

        val output = output(shortJournal, player)
        assertEquals("Only line", output.single { it.first == bodyIds.first() }.second)
        assertTrue(output.filter { it.first in bodyIds.drop(1) }.all { it.second.isEmpty() })
        assertEquals(listOf(8144) + bodyIds, output.map { it.first })
    }

    @Test
    fun `closing clears only journal text widgets`() {
        val journal = QuestJournalInterface("Bot census")
        repeat(100) { journal.addLine("Line $it") }
        val player = player()
        output(journal, player)
        val output = capture(player) { journal.onClose(player) }

        assertEquals(listOf(8144) + bodyIds, output.map { it.first })
        assertTrue(output.all { it.second.isEmpty() })
    }

    @Test
    fun `opening resets the journal scroll position`() {
        val player = player()
        val journal = QuestJournalInterface("Bot census")
        val scrolls = ArrayList<Pair<Int, Int>>()
        doAnswer { invocation ->
            val writer = invocation.getArgument<GameMessageWriter>(0)
            if (writer is WidgetScrollPositionMessageWriter) {
                val message = writer.write(player, Unpooled.buffer())
                try {
                    scrolls += message.getShort(false) to
                        message.getShort(false, ByteOrder.LITTLE, ValueType.ADD)
                } finally {
                    message.releaseAll()
                }
            }
            null
        }.`when`(player).queue(any(GameMessageWriter::class.java))

        journal.onOpen(player)
        journal.onOpen(player)

        assertEquals(listOf(8143 to 0, 8143 to 0), scrolls)
    }

    @Test
    fun `scroll packet matches the revision 377 fixed length encoding`() {
        val message = WidgetScrollPositionMessageWriter(8143, 1337).write(player(), Unpooled.buffer())
        try {
            assertEquals(200, message.opcode)
            assertEquals(MessageType.FIXED, message.type)
            assertEquals(4, message.buffer.readableBytes())
            assertArrayEquals(byteArrayOf(0x1f, 0xcf.toByte(), 0xb9.toByte(), 0x05), message.getBytes(4))
        } finally {
            message.releaseAll()
        }
    }

    private fun player(): Player {
        val player = mock(Player::class.java)
        val field = Player::class.java.getDeclaredField("textCache")
        field.isAccessible = true
        field.set(player, HashMap<Int, String>())
        doCallRealMethod().`when`(player).sendText(any(), anyInt())
        doCallRealMethod().`when`(player).clearText(anyInt())
        return player
    }

    private fun output(journal: QuestJournalInterface, player: Player = player()): List<Pair<Int, String>> =
        capture(player) { journal.onOpen(player) }

    private fun capture(player: Player, action: () -> Unit): List<Pair<Int, String>> {
        val output = ArrayList<Pair<Int, String>>()
        doAnswer { invocation ->
            val writer = invocation.getArgument<GameMessageWriter>(0)
            if (writer is WidgetTextMessageWriter) {
                val message = writer.write(player, Unpooled.buffer())
                try {
                    output += message.getShort(false, ByteOrder.LITTLE, ValueType.ADD) to message.string
                } finally {
                    message.releaseAll()
                }
            }
            null
        }.`when`(player).queue(any(GameMessageWriter::class.java))
        action()
        return output
    }
}
