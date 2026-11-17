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
     * Whether one matching container can be filled without a quantity selection.
     */
    protected open val autoFillSingle = false

    /**
     * Retrieves the id of [empty] filled with this resource.
     */
    abstract fun getFilled(empty: Int): Int?

    /**
     * Invoked when the item is filled with the resource.
     */
    open fun onFill(plr: Player, resourceObject: GameObject) {

    }

    /**
     * Starts a single fill when supported, or offers a quantity selection for [emptyId].
     */
    fun fill(plr: Player, emptyId: Int, resourceObject: GameObject) {
        val filled = getFilled(emptyId) ?: return
        val count = plr.inventory.computeAmountForId(emptyId)
        if (count == 0) return

        fun start(player: Player, amount: Int) {
            player.submitAction(FillActionItem(player, Item(emptyId), Item(filled), this, resourceObject, amount))
        }
        if (autoFillSingle && count == 1) {
            start(plr, 1)
            return
        }
        plr.overlays.open(object : MakeItemDialogue(filled) {
            override fun make(player: Player, id: Int, index: Int, forAmount: Int) = start(player, forAmount)
        })
    }
}
