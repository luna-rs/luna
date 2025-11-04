package game.obj.resource.fillable

import game.obj.resource.GameResource
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogue
import io.luna.game.model.`object`.GameObject

/**
 * An abstraction model representing a resource within the Runescape world that fills items.
 *
 * @author lare96
 */
abstract class FillableResource : GameResource() {

    /**
     * Retrieves the id of [empty] filled with this resource.
     */
    abstract fun getFilled(empty: Int): Int?

    /**
     * Invoked when the item is filled with the resource.
     */
    open fun onFill(plr: Player) {

    }

    /**
     * Open an interface that allows for filling [emptyId] with this resource, for [plr].
     */
    fun fill(plr: Player, emptyId: Int, resourceObject: GameObject) {
        val filled = getFilled(emptyId)
        if (filled != null) {
            plr.overlays.open(object : MakeItemDialogue(filled) {
                override fun make(player: Player, id: Int, index: Int, forAmount: Int) {
                    val emptyItem = Item(emptyId)
                    val filledItem = Item(filled)
                    plr.submitAction(FillActionItem(plr, emptyItem, filledItem, this@FillableResource, forAmount))
                }
            })
        }
    }
}