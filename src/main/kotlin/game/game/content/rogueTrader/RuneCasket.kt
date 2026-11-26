package game.content.rogueTrader

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import game.content.rogueTrader.RogueTrader.largeCasketOpened
import game.content.rogueTrader.RogueTrader.smallCasketOpened
import game.skill.Skills
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.net.msg.out.WidgetItemsMessageWriter

/**
 * The caskets Aubury's runes arrive in. The small one holds elemental runes and can be forced, picked or solved; the
 * large one holds the other runes and only opens to its rune puzzle. Opening a casket once unlocks that half of
 * Ali's rune stock for good.
 *
 * @author TheLining
 */
enum class RuneCasket(
    val interfaceId: Int,
    val boardId: Int,
    val firstCell: Int,
    val openButton: Int,
    val closeButton: Int,
    val pickers: List<Int>,
    val removeButton: Int,
    val runes: List<Int>,
    val boxWidth: Int,
    val boxHeight: Int,
    val clues: Int,
    val shop: String
) {
    SMALL(interfaceId = 14378,
          boardId = 14442,
          firstCell = 14399,
          openButton = 14420,
          closeButton = 14421,
          pickers = listOf(14437, 14438, 14439, 14440),
          removeButton = 14441,
          runes = listOf(557, 555, 556, 554),
          boxWidth = 2,
          boxHeight = 2,
          clues = 6,
          shop = RogueTrader.ELEMENTAL_RUNES),
    LARGE(interfaceId = 14244,
          boardId = 14377,
          firstCell = 14256,
          openButton = 14254,
          closeButton = 14255,
          pickers = (14367..14375).toList(),
          removeButton = 14376,
          runes = listOf(557, 555, 556, 558, 554, 559, 560, 562, 563),
          boxWidth = 3,
          boxHeight = 3,
          clues = 30,
          shop = RogueTrader.CATALYTIC_RUNES);

    /**
     * The board's width and height.
     */
    val size get() = runes.size

    /**
     * The board's first and last "Place-rune" buttons.
     */
    val cells get() = firstCell until firstCell + size * size

    /**
     * Whether [plr] has opened this casket before.
     */
    fun opened(plr: Player) = if (this == SMALL) plr.smallCasketOpened else plr.largeCasketOpened

    private fun unlock(plr: Player) {
        if (this == SMALL) plr.smallCasketOpened = true else plr.largeCasketOpened = true
    }

    /**
     * Opens the casket for [plr] and shows them what's inside.
     */
    fun open(plr: Player) {
        unlock(plr)
        plr.sendMessage("You manage to open the casket.")
        RogueTrader.openShop(plr, shop)
    }

    /**
     * "Force box": a Strength and an Agility roll, both of which must pass. The large casket never gives.
     */
    fun force(plr: Player) {
        if (this == SMALL && Skills.success(70, 170, plr.strength.level) && Skills.success(120, 255, plr.agility.level)) {
            plr.strength.addExperience(2.5)
            plr.agility.addExperience(2.5)
            open(plr)
            return
        }
        plr.sendMessage("Your arms tire from the exertions of trying to open the casket...")
        val drain = rand(0, 3)
        plr.strength.drain(drain)
        plr.agility.drain(drain)
        if (this == LARGE) {
            plr.newDialogue().player("Hmmm... This lock looks like it may be impossible to prise open.").open()
        } else {
            plr.sendMessage("You fail to open the chest.")
        }
    }

    /**
     * "Pick lock": a Thieving roll, better with a lockpick, which can break. The large casket never gives.
     */
    fun pick(plr: Player) {
        val lockpick = plr.inventory.contains(LOCKPICK)
        if (this == SMALL && Skills.success(70, if (lockpick) 240 else 170, plr.thieving.level)) {
            plr.thieving.addExperience(5.0)
            open(plr)
            return
        }
        plr.sendMessage("You fail to pick the lock - your fingers get numb from fumbling with the lock.")
        plr.thieving.drain(rand(0, 3))
        if (lockpick && rand(1, 5) == 1) {
            plr.inventory.remove(Item(LOCKPICK))
            plr.thieving.addExperience(0.5)
            plr.sendMessage("Your lockpick breaks.")
        }
        if (this == LARGE) {
            plr.newDialogue().player("Hmmm... This lock looks like it may be impossible to pick open.").open()
        }
    }

    /**
     * "Examine lock": the rune puzzle.
     */
    fun examine(plr: Player) {
        val puzzle = Puzzle(this)
        plr.overlays.open(PuzzleInterface(puzzle))
        plr.casketPuzzle = puzzle
        puzzle.show(plr)
    }

    /**
     * A puzzle in progress: no rune may repeat in a row, a column or a box. [clues] start in place and can't move.
     */
    class Puzzle(val casket: RuneCasket) {

        /**
         * The board, row by row: 0 for an empty square, otherwise the rune's place in [RuneCasket.runes] plus one.
         */
        val board = IntArray(casket.size * casket.size)

        /**
         * The squares that started filled.
         */
        val fixed = BooleanArray(board.size)

        /**
         * The rune picked to place next, or 0 to clear squares.
         */
        var selected = 1

        init {
            val solution = solvedBoard()
            board.indices.shuffled().take(casket.clues).forEach {
                board[it] = solution[it]
                fixed[it] = true
            }
        }

        /**
         * A random finished board: a shifted pattern with its runes, rows, columns, bands and stacks shuffled.
         */
        private fun solvedBoard(): IntArray {
            val n = casket.size
            val w = casket.boxWidth
            val h = casket.boxHeight
            val symbols = (1..n).shuffled()
            val rows = (0 until n / h).shuffled().flatMap { band -> (0 until h).shuffled().map { band * h + it } }
            val cols = (0 until n / w).shuffled().flatMap { stack -> (0 until w).shuffled().map { stack * w + it } }
            return IntArray(n * n) {
                val r = rows[it / n]
                val c = cols[it % n]
                symbols[(w * (r % h) + r / h + c) % n]
            }
        }

        /**
         * Places the selected rune in square [index], or clears it.
         */
        fun place(index: Int) {
            if (!fixed[index]) {
                board[index] = selected
            }
        }

        /**
         * Whether every square is filled and no rune repeats in a row, a column or a box.
         */
        fun solved(): Boolean {
            val n = casket.size
            if (board.any { it == 0 }) {
                return false
            }
            val groups = ArrayList<List<Int>>()
            for (i in 0 until n) {
                groups += (0 until n).map { board[i * n + it] }
                groups += (0 until n).map { board[it * n + i] }
            }
            val w = casket.boxWidth
            val h = casket.boxHeight
            for (top in 0 until n step h) {
                for (left in 0 until n step w) {
                    groups += (0 until h).flatMap { r -> (0 until w).map { c -> board[(top + r) * n + left + c] } }
                }
            }
            return groups.all { it.toSet().size == n }
        }

        /**
         * Shows the board to [plr].
         */
        fun show(plr: Player) {
            val items = board.map { if (it == 0) null else Item(casket.runes[it - 1]) }
            plr.queue(WidgetItemsMessageWriter(casket.boardId, items))
        }
    }

    /**
     * The puzzle window, which forgets the puzzle when it closes.
     */
    class PuzzleInterface(val puzzle: Puzzle) : StandardInterface(puzzle.casket.interfaceId) {
        override fun onClose(player: Player) {
            if (player.casketPuzzle === puzzle) {
                player.casketPuzzle = null
            }
        }
    }

    companion object {

        /**
         * A lockpick, which makes picking the lock easier.
         */
        const val LOCKPICK = 1523

        /**
         * The puzzle [Player] is solving.
         */
        var Player.casketPuzzle by Attr.nullableObj(Puzzle::class)

        /**
         * Lowers [Skill] by [amount], never below 1.
         */
        private fun Skill.drain(amount: Int) {
            level = (level - amount).coerceAtLeast(1)
        }
    }
}
