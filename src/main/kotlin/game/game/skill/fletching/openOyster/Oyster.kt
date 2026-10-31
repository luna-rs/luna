package game.skill.fletching.openOyster

import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.util.RandomUtils

/**
 * Prises oysters open with a chisel. Each oyster turns, in its own slot, into an empty oyster or the pearls it held.
 *
 * @author TheLining
 */
object Oyster {

    /**
     * The oyster identifier.
     */
    const val ID = 407

    /**
     * The empty oyster identifier.
     */
    const val EMPTY = 409

    /**
     * The single oyster pearl identifier.
     */
    const val PEARL = 411

    /**
     * The oyster pearls identifier.
     */
    const val PEARLS = 413

    /**
     * The chisel identifier.
     */
    const val CHISEL = 1755

    /**
     * What an opened oyster turns into, weighted against each other.
     */
    val CONTENTS: Map<Int, Double> = mapOf(EMPTY to 90.0,
                                           PEARL to 9.0,
                                           PEARLS to 1.0)

    /**
     * Opens the oyster in [plr]'s inventory slot [index].
     *
     * @return `true` if the oyster was opened.
     */
    fun open(plr: Player, index: Int): Boolean {
        val inventory = plr.inventory
        if (!inventory.contains(index, ID)) {
            return false
        }
        if (!inventory.contains(CHISEL)) {
            plr.sendMessage("You need a chisel to open this oyster.")
            return false
        }
        inventory.set(index, Item(RandomUtils.weightedRoll(CONTENTS)))
        plr.sendMessage("You open the oyster shell.")
        return true
    }
}
