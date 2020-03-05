package world.player.skill.fletching.cutLog

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player
import world.player.skill.fletching.attachArrow.Arrow
import world.player.skill.fletching.stringBow.Bow

/**
 * An [InventoryAction] implementation that cuts logs.
 */
class CutLogAction(plr: Player,
                   val log: Int,
                   val bow: Bow,
                   makeTimes: Int) : InventoryAction(plr, true, 3, makeTimes) {

    companion object {

        /**
         * The log cutting animation.
         */
        val ANIMATION = Animation(6782)
    }

    override fun add(): List<Item> {
        val unstrungItem =
            when (bow) {
                Bow.ARROW_SHAFT -> Item(bow.unstrung, Arrow.SET_AMOUNT)
                else -> Item(bow.unstrung)
            }
        return listOf(unstrungItem)
    }

    override fun remove() = listOf(Item(log))

    override fun executeIf(start: Boolean) =
        when {
            mob.fletching.level < bow.level -> {
                mob.sendMessage("You need a Fletching level of ${bow.level} to cut this.")
                false
            }
            else -> true
        }


    override fun execute() {
        val unstrungName = itemDef(bow.unstrung).name
        mob.sendMessage("You carefully cut the wood into ${addArticle(unstrungName)}.")

        mob.animation(ANIMATION)
        mob.fletching.addExperience(bow.exp)
    }

    override fun ignoreIf(other: Action<*>) =
        when (other) {
            is CutLogAction -> log == other.log && bow == other.bow
            else -> false
        }
}