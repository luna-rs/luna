package game.skill.fletching.ogre

import api.predef.*
import game.skill.fletching.ogre.OgreFletching.CHISEL
import game.skill.fletching.ogre.OgreFletching.WOLFBONE_ARROWTIPS
import game.skill.fletching.ogre.OgreFletching.WOLF_BONES
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] that chisels wolf bones into wolfbone arrowtips, one set of bones every two ticks, starting on
 * the tick after the choice. Each set gives 2 to 6 tips. There is no animation.
 *
 * @author lare96
 */
class ChiselBonesAction(plr: Player, amount: Int) : InventoryAction(plr, false, 1, amount) {

    /**
     * The tips made by the current set of bones.
     */
    private var made = 0

    override fun remove() = listOf(Item(WOLF_BONES))

    override fun add(): List<Item> {
        made = rand(OgreFletching.TIPS_PER_BONE)
        return listOf(Item(WOLFBONE_ARROWTIPS, made))
    }

    override fun executeIf(start: Boolean) =
        when {
            mob.fletching.level < OgreFletching.OGRE_ARROW_LEVEL -> {
                mob.sendMessage("You need a Fletching level of ${OgreFletching.OGRE_ARROW_LEVEL} to make this.")
                false
            }
            // TODO Ogre arrows need Big Chompy Bird Hunting completed. Check it here once quests exist.
            !mob.inventory.contains(CHISEL) -> false
            else -> true
        }

    override fun execute() {
        mob.sendMessage("You chisel $made wolf bone arrow tips.")
        mob.fletching.addExperience(OgreFletching.TIP_EXP * made)
        // TODO Chiselling wolf bones also gave Crafting experience; the amount is unknown.
        delay = 2
    }
}
