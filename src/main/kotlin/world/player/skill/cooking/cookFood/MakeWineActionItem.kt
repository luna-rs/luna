package world.player.skill.cooking.cookFood

import api.attr.Attr
import api.predef.*
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import world.player.skill.cooking.prepareFood.IncompleteFood

/**
 * An [InventoryAction] implementation that makes wine.
 */
class MakeWineActionItem(plr: Player, amount: Int) : InventoryAction(plr, true, 1, amount) {

    companion object {

        /**
         * An attribute holding the wine fermenting task.
         */
        internal var Player.wineFermentTask by Attr.nullableObj(FermentWineTask::class)

        /**
         * A reference to the wine data.
         */
        val WINE = IncompleteFood.UNFERMENTED_WINE
    }

    override fun remove(): MutableList<Item> = arrayListOf(Item(1987), Item(1937))

    override fun add(): MutableList<Item> = arrayListOf(Item(1995))

    override fun executeIf(start: Boolean): Boolean = when {
        mob.cooking.level < WINE.lvl -> {
            mob.sendMessage("You need a Cooking level of ${WINE.lvl} to make this.")
            false
        }

        else -> true
    }

    override fun execute() {
        mob.wineFermentTask?.cancel()
        mob.sendMessage("You add the grapes to the jug of water.")
    }

    override fun onFinished() {
        val task = FermentWineTask(mob)
        mob.wineFermentTask = task
        world.schedule(task)
    }
}