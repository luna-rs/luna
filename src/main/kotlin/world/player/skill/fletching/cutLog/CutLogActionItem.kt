package world.player.skill.fletching.cutLog

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.Action
import io.luna.game.action.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.Player
import world.player.Animations
import world.player.Sounds
import world.player.skill.fletching.attachArrow.Arrow
import world.player.skill.fletching.stringBow.Bow

/**
 * An [InventoryAction] implementation that cuts logs.
 */
class CutLogActionItem(plr: Player,
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
        val unstrungName = itemName(bow.unstrung)
        mob.sendMessage("You carefully cut the wood into ${addArticle(unstrungName)}.")

        mob.playSound(Sounds.LIGHT_FIRE)
        mob.animation(Animations.CUT_LOG)
        mob.fletching.addExperience(bow.exp)
    }

    override fun ignoreIf(other: Action<*>) =
        when (other) {
            is CutLogActionItem -> log == other.log && bow == other.bow
            else -> false
        }
}