package world.obj.resource.fillable

import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import io.luna.game.model.`object`.GameObject
import world.obj.resource.GameResource

/**
 * An abstraction model representing a resource within the Runescape world that fills items.
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
            plr.interfaces.open(object : MakeItemDialogueInterface(filled) {
                override fun makeItem(player: Player, id: Int, index: Int, forAmount: Int) {
                    val emptyItem = Item(emptyId)
                    val filledItem = Item(filled)
                    plr.submitAction(FillActionItem(plr, emptyItem, filledItem, resourceObject,
                                                    this@FillableResource, forAmount))
                }
            })
        }
    }
}