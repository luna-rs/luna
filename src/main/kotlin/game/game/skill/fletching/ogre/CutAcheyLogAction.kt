package game.skill.fletching.ogre

import api.predef.*
import api.predef.ext.*
import game.player.Animations
import game.skill.fletching.ogre.OgreFletching.ACHEY_LOGS
import game.skill.fletching.ogre.OgreFletching.WOLF_BONES
import io.luna.game.action.ActionState
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * An [InventoryAction] that cuts achey tree logs with a knife. It keeps the knife's rhythm: the animation starts when
 * the action does, the first item comes two ticks later, then one every three ticks. Each log gives 2 to 6 ogre arrow
 * shafts, or one unstrung comp bow, which also uses up one set of wolf bones.
 *
 * @author lare96
 */
class CutAcheyLogAction(plr: Player,
                        val product: AcheyProduct,
                        makeTimes: Int) : InventoryAction(plr, false, 2, makeTimes) {

    /**
     * The amount made by the current cut.
     */
    private var made = 0

    /**
     * The slots of the log and wolf bones being used, and the first empty slot before they were removed.
     */
    private var logSlot = -1
    private var boneSlot = -1
    private var emptySlot = -1

    /**
     * If the cutting animation was started.
     */
    private var animating = false

    override fun remove() =
        when (product) {
            AcheyProduct.OGRE_ARROW_SHAFTS -> listOf(Item(ACHEY_LOGS))
            AcheyProduct.COMP_OGRE_BOW -> listOf(Item(ACHEY_LOGS), Item(WOLF_BONES))
        }

    override fun add(): List<Item> {
        made = if (product == AcheyProduct.OGRE_ARROW_SHAFTS) rand(OgreFletching.SHAFTS_PER_LOG) else 1
        return listOf(Item(product.id, made))
    }

    override fun executeIf(start: Boolean) =
        when {
            mob.fletching.level < product.level -> {
                mob.sendMessage("You need a Fletching level of ${product.level} to cut this.")
                false
            }
            // TODO Ogre arrow shafts need Big Chompy Bird Hunting completed; the comp bow is learned in Zogre Flesh
            //  Eaters. Check both here once quests exist.
            start && !mob.inventory.containsAll(remove()) -> false
            !start && executions > 0 && !mob.inventory.contains(ACHEY_LOGS) -> {
                mob.sendMessage("You have run out of logs.")
                false
            }
            else -> {
                if (start) {
                    mob.animation(Animations.CUT_LOG)
                    animating = true
                } else {
                    val inventory = mob.inventory
                    logSlot = inventory.computeIndexForId(ACHEY_LOGS)
                    boneSlot = if (product == AcheyProduct.COMP_OGRE_BOW) inventory.computeIndexForId(WOLF_BONES) else -1
                    emptySlot = inventory.nextFreeIndex
                }
                true
            }
        }

    override fun execute() {
        mob.sendMessage(OgreFletching.cutMessage(product, made))
        moveToLogSlot()
        mob.animation(Animations.CUT_LOG)
        mob.fletching.addExperience(product.exp * made)
        delay = 3
    }

    override fun onFinished() {
        // Only a finished run stops the animation; an interrupting action may have just set its own.
        if (animating && state == ActionState.COMPLETED) {
            mob.animation(Animation.CANCEL)
        }
    }

    /**
     * Puts a newly placed item into the slot of the log it was cut from.
     */
    private fun moveToLogSlot() {
        val inventory = mob.inventory
        val placed = listOf(emptySlot, logSlot, boneSlot).filter { it >= 0 }.minOrNull() ?: return
        if (placed != logSlot && inventory.computeIdForIndex(placed) == product.id && inventory.get(logSlot) == null) {
            inventory.swap(placed, logSlot)
        }
    }
}
