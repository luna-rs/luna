package game.content.barrows

import io.luna.game.model.Position
import io.luna.game.model.def.WidgetDefinition
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.controller.ControllerManager
import io.luna.game.model.mob.overlay.AbstractOverlaySet
import io.luna.game.model.mob.overlay.WalkableInterface
import io.luna.net.msg.GameMessageWriter
import io.luna.net.msg.out.MinimapStateMessageWriter
import io.luna.net.msg.out.WalkableInterfaceMessageWriter
import io.netty.buffer.Unpooled
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*

/**
 * Exercises Barrows presentation through the actual position-listener and overlay lifecycle.
 *
 * @author lare96
 */
class BarrowsAreaListenerTest {
    private lateinit var player: Player
    private lateinit var controllers: ControllerManager
    private lateinit var overlays: AbstractOverlaySet
    private val packets = mutableListOf<GameMessageWriter>()

    @BeforeEach
    fun setup() {
        for (id in listOf(4535, 197)) {
            if (WidgetDefinition.ALL.get(id).isEmpty) {
                val definition = mock(WidgetDefinition::class.java)
                `when`(definition.id()).thenReturn(id)
                WidgetDefinition.ALL.storeDefinition(definition)
            }
        }
        player = mock(Player::class.java, RETURNS_DEEP_STUBS)
        overlays = AbstractOverlaySet(player)
        controllers = ControllerManager(player)
        `when`(player.overlays).thenReturn(overlays)
        `when`(player.controllers).thenReturn(controllers)
        doAnswer { invocation ->
            packets += invocation.getArgument<GameMessageWriter>(0)
            null
        }.`when`(player).queue(any(GameMessageWriter::class.java))
    }

    private fun position(x: Int, y: Int, z: Int) {
        `when`(player.position).thenReturn(Position(x, y, z))
        controllers.checkPosition()
    }

    private fun minimapStates(): List<Int> = packets.filterIsInstance<MinimapStateMessageWriter>().map {
        val buffer = Unpooled.buffer()
        try {
            it.write(player, buffer).content().readUnsignedByte().toInt()
        } finally {
            buffer.release()
        }
    }

    private fun overlayIds(): List<Int> = packets.filterIsInstance<WalkableInterfaceMessageWriter>().map {
        val buffer = Unpooled.buffer()
        try {
            it.write(player, buffer).content().readShort().toInt()
        } finally {
            buffer.release()
        }
    }

    @Test
    fun boundsIncludeUndergroundMapSquareAndExcludeSurfaceAndNeighbors() {
        for (plane in 0..3) {
            assertTrue(BarrowsAreaListener.inside(Position(3520, 9664, plane)))
            assertTrue(BarrowsAreaListener.inside(Position(3583, 9727, plane)))
            assertFalse(BarrowsAreaListener.inside(Position(3519, 9700, plane)))
            assertFalse(BarrowsAreaListener.inside(Position(3584, 9700, plane)))
            assertFalse(BarrowsAreaListener.inside(Position(3550, 9663, plane)))
            assertFalse(BarrowsAreaListener.inside(Position(3550, 9728, plane)))
        }
        assertFalse(BarrowsAreaListener.inside(Position(3563, 3289, 0)))
    }

    @Test
    fun firstPositionCheckUndergroundRestoresDisplayAndRepeatedChecksDoNotResend() {
        position(3557, 9703, 3)
        controllers.checkPosition()
        assertTrue(controllers.contains(BarrowsAreaListener))
        assertTrue(overlays.has(BarrowsOverlay::class.java))
        assertEquals(listOf(2), minimapStates())
        assertEquals(listOf(4535), overlayIds())
    }

    @Test
    fun movingBetweenCryptsAndTunnelsKeepsDisplayOpen() {
        position(3557, 9703, 3)
        position(3551, 9694, 0)
        position(3556, 9718, 3)
        assertEquals(listOf(2), minimapStates())
        assertEquals(listOf(4535), overlayIds())
    }

    @Test
    fun leavingRegionRestoresMinimapAndClosesOverlayThenReentryOpensItAgain() {
        position(3557, 9703, 3)
        position(3563, 3289, 0)
        assertFalse(overlays.has(BarrowsOverlay::class.java))
        assertEquals(listOf(2, 0), minimapStates())
        assertEquals(listOf(4535, -1), overlayIds())
        position(3557, 9703, 3)
        assertEquals(listOf(2, 0, 2), minimapStates())
        assertEquals(listOf(4535, -1, 4535), overlayIds())
    }

    @Test
    fun exitDoesNotCloseReplacementOverlay() {
        position(3557, 9703, 3)
        overlays.open(WalkableInterface(197))
        packets.clear()
        position(3563, 3289, 0)
        assertTrue(overlays.has(WalkableInterface::class.java))
        assertEquals(listOf(0), minimapStates())
        assertTrue(overlayIds().isEmpty())
    }

    @Test
    fun surfaceLoginDoesNotOpenBarrowsDisplay() {
        position(3563, 3289, 0)
        assertTrue(minimapStates().isEmpty())
        assertTrue(overlayIds().isEmpty())
    }
}
