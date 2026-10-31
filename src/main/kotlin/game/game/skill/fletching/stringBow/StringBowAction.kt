package game.skill.fletching.stringBow

import api.predef.*
import game.player.Sound
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] implementation that strings bows. Stringing has no animation.
 *
 * @author lare96
 */
class StringBowAction(plr: Player,
                      val bow: Bow,
                      count: Int) : InventoryAction(plr, true, 2, count) {

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
        mob.playSound(Sound.STRING_BOW)
        mob.sendMessage("You add a string to the bow.")
        mob.fletching.addExperience(bow.stringExp)
    }
}