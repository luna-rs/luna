package game.skill.fletching.stringBow

import api.predef.*
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import game.player.Sounds

/**
 * An [InventoryAction] implementation that strings bows.
 *
 * @author lare96
 */
class StringBowActionItem(plr: Player,
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
        mob.playSound(Sounds.STRING_BOW)
        mob.sendMessage("You add a string to the bow.")
        mob.animation(ANIMATION)
        mob.fletching.addExperience(bow.exp)
    }
}