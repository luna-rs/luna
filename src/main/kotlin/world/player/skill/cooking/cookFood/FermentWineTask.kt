package world.player.skill.cooking.cookFood

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import io.luna.game.model.EntityState
import io.luna.game.model.item.DynamicItem
import io.luna.game.model.item.Item
import io.luna.game.model.item.ItemContainer
import io.luna.game.model.mob.Player
import io.luna.game.task.Task
import world.player.skill.cooking.prepareFood.IncompleteFood

/**
 * A [Task] implementation that will ferment all wines in the inventory and bank of a [Player].
 */
class FermentWineTask(val plr: Player) : Task(false, 1) {

    companion object {

        /**
         * How many ticks this wine has been fermenting for.
         */
        internal var DynamicItem.wineFermentCounter by Attr.int().persist("wine_ferment_counter")
    }

    /**
     * Adds a tick to wine fermenting counter for [item] in [container] on [index].
     */
    private fun addCounter(item: Item?, index: Int, container: ItemContainer): Boolean {
        if (item != null && item.id == 1995 && item is DynamicItem) {
            if (++item.wineFermentCounter >= 20) {
                plr.cooking.addExperience(IncompleteFood.UNFERMENTED_WINE.exp)
                item.wineFermentCounter = 0
                game.sync { container[index] = Item(1993) }
                return false
            }
        }
        return true
    }

    override fun execute() {
        if (plr.state == EntityState.INACTIVE) {
            cancel()
            return
        }
        var cancel = true
        plr.inventory.forIndexedItems { index, item ->
            if (addCounter(item, index, plr.inventory)) {
                // Wine is still fermenting.
                cancel = false
            }
        }
        plr.bank.forIndexedItems { index, item ->
            if (addCounter(item, index, plr.bank)) {
                // Wine is still fermenting.
                cancel = false
            }
        }
        if (cancel) {
            cancel()
        }
    }
}