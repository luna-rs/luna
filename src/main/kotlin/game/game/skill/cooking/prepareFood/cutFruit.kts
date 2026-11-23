package game.skill.cooking.prepareFood

import api.predef.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogue

/**
 * A fruit and what a knife makes of it.
 */
class Fruit(val id: Int, val slices: Int, val chunks: Int)

val PINEAPPLE = 2114

val fruits = listOf(
    Fruit(id = 2102, slices = 2106, chunks = 2104), // Lemon
    Fruit(id = 2108, slices = 2112, chunks = 2110), // Orange
    Fruit(id = 2120, slices = 2124, chunks = 2122), // Lime
    Fruit(id = PINEAPPLE, slices = 2118, chunks = 2116))

/**
 * Asks whether to slice or dice [fruit]. A pineapple slices into four rings.
 */
fun open(plr: Player, fruit: Fruit) {
    val name = itemName(fruit.id).lowercase()
    val items = intArrayOf(fruit.slices, fruit.chunks)
    val labels = arrayOf("Slice the $name.", "Dice the $name.")
    plr.overlays.open(object : MakeItemDialogue(items, labels) {
        override fun make(player: Player, id: Int, index: Int, forAmount: Int) {
            if (fruit.id == PINEAPPLE && id == fruit.slices) {
                val used = mutableSetOf(Mix.KNIFE, PINEAPPLE)
                player.submitAction(PrepareFoodActionItem(player, IncompleteFood.PINEAPPLE_RING, used, forAmount))
            } else {
                player.submitAction(CutFruitActionItem(player, fruit.id, id, forAmount))
            }
        }
    })
}

fruits.forEach { fruit -> useItem(Mix.KNIFE).onItem(fruit.id) { open(plr, fruit) } }
