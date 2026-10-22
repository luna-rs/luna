package game.content.barrows

import io.luna.game.model.Position
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.def.VarpDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.AbstractOverlaySet
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.mob.varp.PersistentVarpManager
import io.luna.net.msg.GameMessageWriter
import io.luna.net.msg.out.WidgetModelMessageWriter
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.random.Random

/**
 * Exercises the actual overlay lifecycle, cache model packets, and stale/replayed answer rejection.
 *
 * @author lare96
 */
class BarrowsPuzzleTest {
    private val player = mock(Player::class.java, RETURNS_DEEP_STUBS)
    private val manager = PersistentVarpManager(player)
    private val overlays = AbstractOverlaySet(player)
    private val origin = Position(3550, 9690)
    private val packets = mutableListOf<GameMessageWriter>()

    @BeforeEach
    fun setup() {
        for (id in listOf(452, 453)) {
            if (VarpDefinition.ALL.get(id).isEmpty) VarpDefinition.ALL.storeDefinition(VarpDefinition(id, 0))
        }
        if (WidgetDefinition.ALL.get(4543).isEmpty) {
            val def = mock(WidgetDefinition::class.java)
            `when`(def.id()).thenReturn(4543)
            WidgetDefinition.ALL.storeDefinition(def)
        }
        `when`(player.overlays).thenReturn(overlays)
        `when`(player.varpManager).thenReturn(manager)
        `when`(player.position).thenReturn(origin)
        `when`(player.isAlive).thenReturn(true)
        doAnswer { packets += it.getArgument<GameMessageWriter>(0); null }
            .`when`(player).queue(any(GameMessageWriter::class.java))
        BarrowsRun.enterCrypt(player, Random(688))
    }

    @Test
    fun allTwentyFourVariantsSendCorrectModelsAndAcceptOnlyTheirDisplayedAnswer() {
        for (puzzle in BarrowsPuzzle.entries) {
            for (permutation in BarrowsPuzzle.permutations) {
                for (answer in 0..2) {
                    packets.clear()
                    val inter = BarrowsPuzzleInterface(origin, puzzle, permutation)
                    overlays.open(inter)
                    val models = packets.filterIsInstance<WidgetModelMessageWriter>()
                    val expected = puzzle.sequence + permutation.map { puzzle.answers[it] }
                    val widgets = listOf(4545, 4546, 4547, 4550, 4551, 4552)
                    assertEquals(6, models.size)
                    models.forEachIndexed { index, writer ->
                        val buffer = Unpooled.buffer()
                        try {
                            val message = writer.write(player, buffer)
                            assertEquals(216, message.opcode)
                            // Decode independently with the client's unsigned short LE-add operation.
                            fun read(): Int = ((buffer.readUnsignedByte().toInt() - 128) and 255) or
                                (buffer.readUnsignedByte().toInt() shl 8)
                            assertEquals(expected[index], read())
                            assertEquals(widgets[index], read())
                            assertFalse(buffer.isReadable)
                        } finally { buffer.release() }
                    }
                    val before = BarrowsRun.state(player)
                    inter.answer(player, answer, Random(42))
                    val after = BarrowsRun.state(player)
                    assertFalse(inter.isOpen)
                    if (permutation[answer] == 0) {
                        assertEquals(BarrowsVarbit.PUZZLE.write(before, 3), after)
                    } else {
                        assertEquals(BarrowsMaze.randomize(before, Random(42)), after)
                        assertNotEquals(3, BarrowsVarbit.PUZZLE.read(after))
                    }
                    inter.answer(player, permutation.indexOf(0))
                    assertEquals(after, BarrowsRun.state(player))
                }
            }
        }
    }

    @Test
    fun closedReplacedMovedDeadOrChangedRunCannotSubmitAnAnswer() {
        for (scenario in 0..4) {
            `when`(player.position).thenReturn(origin)
            `when`(player.isAlive).thenReturn(true)
            val inter = BarrowsPuzzleInterface(origin, BarrowsPuzzle.ARROWS, listOf(0, 1, 2))
            overlays.open(inter)
            when (scenario) {
                0 -> overlays.closeWindows()
                1 -> overlays.open(BarrowsPuzzleInterface(origin))
                2 -> `when`(player.position).thenReturn(origin.translate(1, 0))
                3 -> `when`(player.isAlive).thenReturn(false)
                4 -> manager.setValue(PersistentVarp.BARROWS, BarrowsRun.state(player) xor BarrowsRun.COLLAPSING_MASK)
            }
            val before = BarrowsRun.state(player)
            inter.answer(player, 0)
            assertEquals(before, BarrowsRun.state(player))
        }
    }
}
