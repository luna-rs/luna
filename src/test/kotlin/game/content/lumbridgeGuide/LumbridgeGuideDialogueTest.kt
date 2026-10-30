package game.content.lumbridgeGuide

import game.player.QuestJournalInterface
import io.luna.game.model.def.NpcDefinition
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.game.model.mob.dialogue.DialogueQueue
import io.luna.game.model.mob.dialogue.NpcDialogue
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.mob.dialogue.PlayerDialogue
import io.luna.game.model.mob.overlay.AbstractOverlaySet
import io.luna.game.model.mob.overlay.OverlayType
import io.luna.net.codec.ByteOrder
import io.luna.net.codec.ValueType
import io.luna.net.msg.GameMessageWriter
import io.luna.net.msg.out.WidgetTextMessageWriter
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Traverses the Guide's actual dialogue queues and overlay callbacks as a player would.
 *
 * @author lare96
 */
class LumbridgeGuideDialogueTest {

    companion object {

        private const val GUIDE_ID = 2244

        @JvmStatic
        @BeforeAll
        fun initializeDefinitions() {
            val widgets = listOf(968, 973, 979, 986, 4882, 4887, 4893, 4900,
                14443, 2469, 8207, 8219, 8134)
            for (id in widgets) {
                if (WidgetDefinition.ALL[id].isEmpty) {
                    val definition = mock(WidgetDefinition::class.java)
                    `when`(definition.id()).thenReturn(id)
                    WidgetDefinition.ALL.storeDefinition(definition)
                }
            }
            if (NpcDefinition.ALL[GUIDE_ID].isEmpty) {
                val definition = mock(NpcDefinition::class.java)
                `when`(definition.id()).thenReturn(GUIDE_ID)
                `when`(definition.name).thenReturn("Lumbridge Guide")
                NpcDefinition.ALL.storeDefinition(definition)
            }
        }
    }

    /**
     * Keeps real dialogue and overlay state while capturing text packets instead of sending them to a client.
     *
     * @author lare96
     */
    private class Visitor(registeredScripts: Set<String> = emptySet()) {

        val player: Player = mock(Player::class.java, RETURNS_DEEP_STUBS)
        val overlays = AbstractOverlaySet(player)
        val text = mutableMapOf<Int, String>()
        var npcPages = 0
            private set

        init {
            `when`(player.overlays).thenReturn(overlays)
            `when`(player.username).thenReturn("Adventurer")
            `when`(player.world.players.iterator()).thenAnswer { emptyList<Player>().iterator() }
            val registry = registeredScripts.associateWith {
                mock(io.luna.game.model.mob.bot.script.BotScriptManager.ScriptSupplier::class.java)
            }.toMutableMap()
            `when`(player.world.botManager.scriptManager.scriptMap).thenReturn(registry)
            doCallRealMethod().`when`(player).newDialogue()
            doCallRealMethod().`when`(player).getDialogues()
            doCallRealMethod().`when`(player).setDialogues(nullable(DialogueQueue::class.java))
            doCallRealMethod().`when`(player).resetDialogues()
            val cache = Player::class.java.getDeclaredField("textCache")
            cache.isAccessible = true
            cache.set(player, HashMap<Int, String>())
            doCallRealMethod().`when`(player).sendText(any(), anyInt())
            doCallRealMethod().`when`(player).clearText(anyInt())
            doAnswer { invocation ->
                val writer = invocation.getArgument<GameMessageWriter>(0)
                if (writer is WidgetTextMessageWriter) {
                    val message = writer.write(player, Unpooled.buffer())
                    try {
                        text[message.getShort(false, ByteOrder.LITTLE, ValueType.ADD)] = message.string
                    } finally {
                        message.releaseAll()
                    }
                }
                null
            }.`when`(player).queue(any(GameMessageWriter::class.java))
            LumbridgeGuideDialogue(player, GUIDE_ID).open()
            finishSpeech()
        }

        fun options(): List<String> {
            val menu = overlays.getOverlay(OptionDialogue::class.java)
            assertNotNull(menu, "Expected an open question menu")
            val count: Int = when (menu.id) {
                14443 -> 2
                2469 -> 3
                8207 -> 4
                8219 -> 5
                else -> fail("Unsupported option dialogue ${menu.id}")
            }
            return (0 until count).map { index ->
                requireNotNull(text[menu.id + 2 + index]).also {
                    assertTrue(it.isNotBlank())
                    assertTrue(it.length <= 60, "Unwrapped menu option: $it")
                }
            }
        }

        fun choose(index: Int) {
            val options = overlays.getOverlay(OptionDialogue::class.java)
            assertTrue(index in this.options().indices)
            when (index) {
                0 -> options.first(player)
                1 -> options.second(player)
                2 -> options.third(player)
                3 -> options.fourth(player)
                4 -> options.fifth(player)
            }
            // Mirrors the click-option handler's guard against closing a replacement menu.
            if (player.dialogues == null && options.isOpen && !overlays.containsType(OverlayType.INPUT)) {
                overlays.closeWindows()
            }
        }

        fun finishSpeech(): Boolean {
            var playerSpoke = false
            var pages = 0
            while (true) {
                val dialogue = overlays.getOverlay(DialogueInterface::class.java) ?: return playerSpoke
                if (dialogue is OptionDialogue) {
                    options()
                    return playerSpoke
                }
                assertTrue(++pages <= 20, "Dialogue did not reach another menu or close")
                playerSpoke = playerSpoke || dialogue is PlayerDialogue
                if (dialogue is NpcDialogue) {
                    npcPages++
                    val count: Int = when (dialogue.id) {
                        4882 -> 1
                        4887 -> 2
                        4893 -> 3
                        4900 -> 4
                        else -> fail("Unsupported NPC dialogue ${dialogue.id}")
                    }
                    for (index in 0 until count) {
                        val line = requireNotNull(text[dialogue.id + 3 + index])
                        assertTrue(line.isNotBlank())
                        assertTrue(line.length <= 60, "Unwrapped NPC line: $line")
                    }
                }
                // Mirrors the continue handler, exercising onClose callbacks and real queue replacement.
                dialogue.isContinueClicked = true
                val queue = player.dialogues
                if (queue != null) queue.advance() else overlays.closeWindows()
            }
        }

        fun follow(path: List<Int>) {
            for (choice in path) {
                choose(choice)
                finishSpeech()
            }
        }
    }

    @Test
    fun allTopicsAreReachableAndAnswersReturnToTheirOwnQuestionMenu() {
        val pending = ArrayDeque<List<Int>>()
        pending.add(emptyList())
        val visited = mutableSetOf<List<String>>()
        var answers = 0
        var reports = 0
        var goodbye = 0
        while (pending.isNotEmpty()) {
            val path = pending.removeFirst()
            val current = Visitor().also { it.follow(path) }
            val menu = current.options()
            if (!visited.add(menu)) continue
            assertTrue(visited.size <= 12, "Unexpectedly large or recursive menu graph")
            for (index in menu.indices) {
                val visitor = Visitor().also { it.follow(path) }
                visitor.choose(index)
                val answered = visitor.finishSpeech()
                when {
                    visitor.overlays.has(QuestJournalInterface::class.java) -> reports++
                    visitor.overlays.has(OptionDialogue::class.java) -> {
                        val next = visitor.options()
                        if (answered) {
                            answers++
                            assertEquals(menu, next, "Answer returned to the wrong menu: ${menu[index]}")
                            assertTrue(visitor.npcPages > 1, "Answer did not display the Guide's advice")
                        } else if (next !in visited) {
                            pending.add(path + index)
                        }
                    }
                    else -> {
                        goodbye++
                        assertEquals("That's all, thank you.", menu[index])
                        assertFalse(visitor.overlays.hasWindow())
                    }
                }
            }
        }
        assertEquals(10, visited.size)
        assertEquals(23, answers, "Expected every detailed advice branch to be reachable")
        assertEquals(3, reports)
        assertEquals(1, goodbye)
    }

    @Test
    fun statisticOptionsOpenRealQuestJournals() {
        val headings = listOf("@dbl@The Guide's bot census", "@dbl@Current and queued tasks",
            "@dbl@Current or next tasks")
        for ((option, heading) in headings.withIndex()) {
            val visitor = Visitor()
            visitor.choose(3)
            assertEquals("Show me the population overview.", visitor.options().first())
            visitor.choose(option)

            val journal = visitor.overlays.getOverlay(QuestJournalInterface::class.java)
            assertNotNull(journal)
            assertTrue(journal.isOpen)
            assertTrue(heading in visitor.text.values)
            assertNull(visitor.player.dialogues)
            assertFalse(visitor.overlays.has(OptionDialogue::class.java))
        }
    }

    @Test
    fun largerScriptRegistriesRemainReachableThroughPageMenus() {
        val visitor = Visitor((1..90).map { "example.Future${it}BotScript" }.toSet())
        visitor.choose(3)
        visitor.choose(1)
        assertEquals("Read ledger page 1 of 3.", visitor.options().first())
        visitor.choose(1)
        assertEquals("Read ledger page 2 of 3.", visitor.options().first())
        visitor.choose(2)
        assertEquals("Read ledger page 1 of 3.", visitor.options().first())
        visitor.choose(2)
        assertEquals("Read ledger page 3 of 3.", visitor.options().first())
        visitor.choose(0)
        assertTrue(visitor.overlays.has(QuestJournalInterface::class.java))
        assertTrue("Page 3 of 3" in visitor.text.values)
    }
}
