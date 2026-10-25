package engine.widget.make

import api.predef.scriptListeners
import api.bot.script.InventoryProductionFixtures
import io.luna.game.event.EventListener
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import io.luna.game.model.mob.overlay.NumberInput
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*

class MakeItemSelectionIsolationTest {
    companion object {
        @JvmStatic @BeforeAll fun fixtures() = InventoryProductionFixtures.initialize()
    }

    private lateinit var listener: EventListener<ButtonClickEvent>

    @BeforeEach fun loadActualPlugin() {
        val first = scriptListeners.size
        Class.forName("engine.widget.make.MakeItem").getConstructor(Array<String>::class.java)
            .newInstance(emptyArray<String>() as Any)
        @Suppress("UNCHECKED_CAST")
        val registered = scriptListeners.drop(first).single() as EventListener<ButtonClickEvent>
        listener = registered
        scriptListeners.subList(first, scriptListeners.size).clear()
    }

    private class Dialogue(length: Int) : MakeItemDialogue(*IntArray(length) { 1000 + it }) {
        val made = mutableListOf<Triple<Int, Int, Int>>()
        override fun make(player: Player, id: Int, index: Int, amount: Int) {
            made += Triple(id, index, amount)
        }
    }

    private fun player(dialogue: Dialogue): Player = mock(Player::class.java, RETURNS_DEEP_STUBS).also {
        `when`(it.overlays.player).thenReturn(it)
        `when`(it.overlays.has(MakeItemDialogue::class.java)).thenReturn(true)
        `when`(it.overlays.getOverlay(MakeItemDialogue::class.java)).thenReturn(dialogue)
    }

    private fun click(player: Player, button: Int) = listener.apply(ButtonClickEvent(player, button))

    private fun prompt(player: Player): NumberInput {
        val captor = ArgumentCaptor.forClass(NumberInput::class.java)
        verify(player.overlays).open(captor.capture())
        return captor.value
    }

    @ParameterizedTest
    @CsvSource("1,true", "1,false", "3,true", "3,false")
    fun twoPlayersKeepTheirOwnSelectionUntilQuantitySubmission(firstLength: Int, firstSubmitsFirst: Boolean) {
        val secondLength = if (firstLength == 1) 3 else 1
        val firstDialogue = Dialogue(firstLength)
        val secondDialogue = Dialogue(secondLength)
        val first = player(firstDialogue)
        val second = player(secondDialogue)
        click(first, 8890)
        val firstPrompt = prompt(first)
        click(second, 8890)
        val secondPrompt = prompt(second)
        if (firstSubmitsFirst) {
            firstPrompt.input(first, 23)
            secondPrompt.input(second, 7)
        } else {
            secondPrompt.input(second, 7)
            firstPrompt.input(first, 23)
        }
        val firstIndex = if (firstLength == 1) 0 else 1
        val secondIndex = if (secondLength == 1) 0 else 1
        assertEquals(listOf(Triple(1000 + firstIndex, firstIndex, 23)), firstDialogue.made)
        assertEquals(listOf(Triple(1000 + secondIndex, secondIndex, 7)), secondDialogue.made)
    }

    @ParameterizedTest @ValueSource(ints = [1, 2, 3, 4, 5])
    fun allLayoutsKeepNormalAmountsAndSelectedItem(length: Int) {
        val firstButton = mapOf(1 to 8893, 2 to 8874, 3 to 8889, 4 to 8909, 5 to 8949).getValue(length)
        for (index in 0 until length) {
            for ((amount, offset) in listOf(1 to 0, 5 to 1, 10 to 2, 23 to 3)) {
                val dialogue = Dialogue(length)
                val player = player(dialogue)
                click(player, firstButton + index * 4 - offset)
                if (offset == 3) prompt(player).input(player, amount)
                assertEquals(listOf(Triple(1000 + index, index, amount)), dialogue.made)
            }
        }
    }

    @Test fun staleOutOfRangeButtonDoesNotCallMakeIndex() {
        val dialogue = Dialogue(1)
        val player = player(dialogue)
        // Fifth item from the five-item layout, while a one-item dialogue is current.
        assertDoesNotThrow { click(player, 8965) }
        assertTrue(dialogue.made.isEmpty())
        verify(player.overlays, never()).closeWindows()
    }

    @Test fun unknownButtonDoesNotProduceAnything() {
        val dialogue = Dialogue(1)
        click(player(dialogue), -123)
        assertTrue(dialogue.made.isEmpty())
    }

    @Test fun buttonWithoutCurrentMakeDialogueDoesNotProduceAnything() {
        val dialogue = Dialogue(1)
        val player = player(dialogue)
        `when`(player.overlays.has(MakeItemDialogue::class.java)).thenReturn(false)
        click(player, 8893)
        assertTrue(dialogue.made.isEmpty())
    }

    @Test fun anotherAmountButtonDoesNotChangeAPendingQuantitySelection() {
        val firstDialogue = Dialogue(1)
        val first = player(firstDialogue)
        click(first, 8890)
        val pending = prompt(first)
        click(player(Dialogue(3)), 8893)
        pending.input(first, 23)
        assertEquals(listOf(Triple(1000, 0, 23)), firstDialogue.made)
    }
}
