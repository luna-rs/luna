package game.skill.fletching.cutLog

import api.predef.*
import api.predef.ext.*
import game.player.Animations
import game.skill.fletching.attachArrow.Arrow
import game.skill.fletching.stringBow.Bow
import io.luna.game.action.ActionState
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * An [InventoryAction] implementation that cuts logs. The animation starts when the action does, the first item
 * comes two ticks later, then one every three ticks. Each item takes the place of the log it was cut from.
 *
 * @author lare96
 */
class CutLogActionItem(plr: Player,
                       val log: Int,
                       val bow: Bow,
                       makeTimes: Int) : InventoryAction(plr, false, 2, makeTimes) {
    // TODO@0.5.0 Implement correct sounds: FLETCH_ONCE(812), FLETCH(813).

    companion object {

        /**
         * The message shown when [bow] is above the player's Fletching level.
         */
        fun levelMessage(bow: Bow): String {
            val name = bow.name.split('_').joinToString(" ") { it.lowercase().replaceFirstChar(Char::uppercase) }
            val article = if (name.first() in "AEIOU") "an" else "a"
            return "You need a Fletching skill of ${bow.level} or above to make $article $name."
        }
    }

    /**
     * The slot of the log being cut, and the first empty slot before it was removed.
     */
    private var logSlot = -1
    private var emptySlot = -1

    /**
     * If the cutting animation was started.
     */
    private var animating = false

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
                mob.sendMessage(levelMessage(bow))
                false
            }
            !start && executions > 0 && !mob.inventory.contains(log) -> {
                mob.sendMessage("You have run out of ${itemName(log).lowercase()}.")
                false
            }
            else -> {
                if (start) {
                    mob.animation(Animations.CUT_LOG)
                    animating = true
                } else {
                    logSlot = mob.inventory.computeIndexForId(log)
                    emptySlot = mob.inventory.nextFreeIndex
                }
                true
            }
        }


    override fun execute() {
        val product = when (bow) {
            Bow.ARROW_SHAFT -> "${Arrow.SET_AMOUNT} arrow shafts"
            Bow.SHORTBOW, Bow.OAK_SHORTBOW, Bow.WILLOW_SHORTBOW, Bow.MAPLE_SHORTBOW, Bow.YEW_SHORTBOW,
            Bow.MAGIC_SHORTBOW -> "a shortbow"
            else -> "a longbow"
        }
        mob.sendMessage("You carefully cut the wood into $product.")
        val inventory = mob.inventory
        if (emptySlot in 0 until logSlot && inventory.computeIdForIndex(emptySlot) == bow.unstrung &&
            inventory.get(logSlot) == null) {
            inventory.swap(emptySlot, logSlot)
        }

       //mob.playSound(Sound.LIGHT_FIRE)
        mob.animation(Animations.CUT_LOG)
        mob.fletching.addExperience(bow.exp)
        delay = 3
    }

    override fun onFinished() {
        // Only a finished run stops the animation; an interrupting action may have just set its own.
        if (animating && state == ActionState.COMPLETED) {
            mob.animation(Animation.CANCEL)
        }
    }
}