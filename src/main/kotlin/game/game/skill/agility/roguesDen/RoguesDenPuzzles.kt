package game.skill.agility.roguesDen

import api.predef.*
import game.skill.agility.roguesDen.RoguesDen.denRun
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.net.msg.out.WidgetAnimationMessageWriter
import io.luna.net.msg.out.WidgetItemsMessageWriter

/**
 * The maze's two puzzle doors: nine dials to turn until all point up, and a mosaic missing one tile.
 *
 * @author TheLining
 */
object RoguesDenPuzzles {

    /**
     * The rotation puzzle's interface, the item container whose slots carry its two options, and its nine dial models
     * (left to right, top to bottom).
     */
    const val ROTATION = 2302
    const val ROTATION_SLOTS = 3219
    private val DIALS = (3221..3229).toList()

    /**
     * The dial item that fills the container's slots, so the slots show their options.
     */
    private const val DIAL_ITEM = 5572

    /**
     * The quarter turns that end on each direction (up, right, down, left), clockwise and anticlockwise. Each one
     * holds its last frame, so it leaves the dial pointing that way.
     */
    private val CLOCKWISE_INTO = intArrayOf(2228, 2222, 2224, 2226)
    private val ANTICLOCKWISE_INTO = intArrayOf(2223, 2225, 2227, 2229)

    /**
     * The mosaic puzzle's interface and its four tile buttons, for tiles 5568–5571.
     */
    const val MOSAIC = 13749
    val MOSAIC_BUTTONS = mapOf(13754 to 5568, 13755 to 5569, 13756 to 5570, 13757 to 5571)

    /**
     * Opens the rotation puzzle, scrambling it with three random turns the first time in a run.
     */
    fun openRotation(plr: Player) {
        val run = plr.denRun ?: return
        val dials = run.dials ?: IntArray(9).also { fresh ->
            do {
                fresh.fill(0)
                repeat(3) { turn(fresh, rand(0, 8), rand().nextBoolean()) }
            } while (fresh.all { it == 0 })
            run.dials = fresh
        }
        plr.overlays.open(StandardInterface(ROTATION))
        plr.queue(WidgetItemsMessageWriter(ROTATION_SLOTS, List(9) { Item(DIAL_ITEM) }))
        dials.forEachIndexed { i, d -> plr.queue(WidgetAnimationMessageWriter(DIALS[i], CLOCKWISE_INTO[d])) }
    }

    /**
     * Turns dial [index], clockwise or not; the dials above, below and to each side turn the other way. Returns
     * `true` once all nine point up.
     */
    fun rotate(plr: Player, index: Int, clockwise: Boolean): Boolean {
        val dials = plr.denRun?.dials ?: return false
        val before = dials.copyOf()
        turn(dials, index, clockwise)
        for (i in 0 until 9) {
            if (dials[i] == before[i]) continue
            val forward = (before[i] + 1) % 4 == dials[i]
            plr.queue(WidgetAnimationMessageWriter(DIALS[i], (if (forward) CLOCKWISE_INTO else ANTICLOCKWISE_INTO)[dials[i]]))
        }
        return dials.all { it == 0 }
    }

    private fun turn(dials: IntArray, index: Int, clockwise: Boolean) {
        val step = if (clockwise) 1 else 3
        dials[index] = (dials[index] + step) % 4
        val row = index / 3
        val col = index % 3
        for ((r, c) in listOf(row - 1 to col, row + 1 to col, row to col - 1, row to col + 1)) {
            if (r in 0..2 && c in 0..2) dials[r * 3 + c] = (dials[r * 3 + c] + 4 - step) % 4
        }
    }

    /**
     * Opens the mosaic puzzle; the client shows only the tiles the player holds.
     */
    fun openMosaic(plr: Player) {
        plr.overlays.open(StandardInterface(MOSAIC))
    }
}
