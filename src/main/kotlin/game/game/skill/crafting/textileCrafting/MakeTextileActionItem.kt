package game.skill.crafting.textileCrafting

import api.predef.*
import game.content.randomEvents.RandomEvents
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * Converts textile ingredients using the native inventory action. Lost City's wool and flax
 * spinning entry points offer general random events before starting, once per submitted batch.
 * Other textile recipes retain their ordinary conversion behavior without an invented event hook.
 *
 * @author lare96
 */
class MakeTextileActionItem(val plr: Player, val textile: Textile, amount: Int) : InventoryAction(plr, true, 2, amount) {

    override fun executeIf(start: Boolean): Boolean =
        when {
            mob.crafting.level < textile.level -> {
                plr.sendMessage("You need a Crafting level of ${textile.level} to make this.")
                false
            }

            start && (textile == Textile.BALL_OF_WOOL || textile == Textile.BOWSTRING) &&
                RandomEvents.offerProduction(mob, remove()) -> false
            else -> true
        }

    override fun execute() {
        mob.animation(Animation(894))
        mob.crafting.addExperience(textile.exp)
        plr.sendMessage("You make ${articleItemName(textile.processedItem.id)}.")
    }

    override fun add() = listOf(textile.processedItem)
    override fun remove() = listOf(textile.rawItem)
}
