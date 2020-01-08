package world.player.skill.fletching.stringBow

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] implementation that strings bows.
 */
class StringBowAction(plr: Player,
                      val bow: Bow,
                      count: Int) : InventoryAction(plr, true, 2, count) {

    companion object {

        /**
         * The stringing animation.
         */
        val ANIMATION = Animation(713)
    }

    override fun add() = listOf(bow.strungItem)
    override fun remove() = listOf(bow.unstrungItem, Item(Bow.BOW_STRING))

    override fun executeIf(start: Boolean) =
        when {
            mob.fletching.level < bow.level -> {
                mob.sendMessage("You need a Fletching level of ${bow.level} to string this bow.")
                false
            }
            !mob.inventory.containsAll(Bow.BOW_STRING, bow.unstrung) -> false
            else -> true
        }

    override fun execute() {
        mob.sendMessage("You add a string to the bow.")
        mob.animation(ANIMATION)
        mob.fletching.addExperience(bow.exp)
    }

    override fun ignoreIf(other: Action<*>) =
        when (other) {
            is StringBowAction -> bow == other.bow
            else -> false
        }
}