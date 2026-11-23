package game.skill.cooking.churn

import api.predef.*
import game.player.Sound
import io.luna.game.action.impl.ItemContainerAction.AnimatedInventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.`object`.GameObject

/**
 * An [AnimatedInventoryAction] that churns every [from] in the inventory into [product]. Milk leaves its bucket
 * behind.
 */
class ChurnAction(plr: Player, private val churn: GameObject, private val from: Dairy, private val product: Dairy) :
    AnimatedInventoryAction(plr, Dairy.STAGE_TICKS * (product.ordinal - from.ordinal), ANIMATION_TICKS,
        plr.inventory.computeAmountForId(from.id)) {

    override fun executeIf(start: Boolean): Boolean {
        if (mob.cooking.level < product.lvl) {
            mob.sendMessage("You need a Cooking level of ${product.lvl} to make ${itemName(product.id).lowercase()}.")
            return false
        }
        if (start) {
            mob.face(churn.position)
            mob.playSound(Sound.CHURN)
        }
        return true
    }

    override fun animation() = ANIMATION

    override fun execute() {
        mob.sendMessage("You make ${articleItemName(product.id).lowercase()}.")
        mob.cooking.addExperience(product.exp(from))
        mob.playSound(Sound.CHURN)
    }

    override fun remove() = listOf(Item(from.id))

    override fun add() = listOfNotNull(Item(product.id), if (from == Dairy.MILK) Item(Dairy.EMPTY_BUCKET) else null)

    private companion object {
        val ANIMATION = Animation(2793)
        const val ANIMATION_TICKS = 5
    }
}
