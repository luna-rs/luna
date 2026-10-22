package game.content.barrows

import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.net.msg.out.WidgetModelMessageWriter
import io.luna.net.msg.out.SoundMessageWriter
import kotlin.random.Random

/**
 * One puzzle attempt bound to the displayed interface, player position and saved maze state.
 * A saved answer varbit alone cannot authorize a button after closing the interface or relogging.
 *
 * @author lare96
 */
class BarrowsPuzzleInterface(
    private val origin: Position,
    private val puzzle: BarrowsPuzzle = BarrowsPuzzle.entries.random(),
    private val permutation: List<Int> = BarrowsPuzzle.permutations.random()
) : StandardInterface(4543) {
    private var expectedState = 0
    private var answered = false

    init {
        require(permutation in BarrowsPuzzle.permutations)
    }

    override fun onOpen(player: Player) {
        puzzle.sequence.forEachIndexed { index, model -> player.queue(WidgetModelMessageWriter(4545 + index, model)) }
        permutation.forEachIndexed { index, answer ->
            player.queue(WidgetModelMessageWriter(4550 + index, puzzle.answers[answer]))
        }
        expectedState = BarrowsVarbit.PUZZLE.write(BarrowsRun.state(player), permutation.indexOf(0))
        player.varpManager.setAndSendValue(PersistentVarp.BARROWS, expectedState)
        player.sendMessage("The door is locked with a strange puzzle.")
    }

    fun answer(player: Player, index: Int, random: Random = Random.Default) {
        if (index !in 0..2 || answered || !isOpen ||
            player.overlays.getOverlay(BarrowsPuzzleInterface::class.java) !== this) return
        answered = true
        if (player.isAlive && player.position == origin && origin.z == 0 &&
            BarrowsAreaListener.inside(origin) && BarrowsRun.state(player) == expectedState) {
            val nextState = if (permutation[index] == 0) {
                player.sendMessage("You hear the doors' locking mechanism grind open.")
                player.queue(SoundMessageWriter(1278, player.varpManager.getValue(PersistentVarp.EFFECTS_VOLUME), 0))
                BarrowsVarbit.PUZZLE.write(expectedState, 3)
            } else {
                player.sendMessage("You got the puzzle wrong! You can hear the catacombs moving around you.")
                BarrowsMaze.randomize(expectedState, random)
            }
            player.varpManager.setAndSendValue(PersistentVarp.BARROWS, nextState)
        }
        player.overlays.closeWindows()
    }
}
