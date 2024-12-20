package world.player.skill.prayer

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.Action
import io.luna.game.action.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.`object`.GameObject
import world.player.Animations

/**
 * An [InventoryAction] that allows players to use [Bone] types on an altar for increased experience.
 */
class OfferBoneAction(plr: Player, private val altar: GameObject, private val bone: Bone) :
    InventoryAction(plr, true, 5, Int.MAX_VALUE) {

    companion object {

        /**
         * The normal unlit XP bonus.
         */
        private const val EXP_BONUS = 1.75

        /**
         * The lit altar XP bonus.
         */
        private const val LIT_EXP_BONUS = 2.50
    }

    override fun ignoreIf(other: Action<*>?): Boolean =
        when (other) {
            is OfferBoneAction -> bone == other.bone
            else -> false
        }

    override fun execute() {
        val isLit = altar.id == 4090
        val xpBonus = if (isLit) LIT_EXP_BONUS else EXP_BONUS
        val sb = StringBuilder("The gods are")
        if (isLit) {
            sb.append(" very pleased ")
        } else {
            sb.append(" pleased ")
        }
        sb.append("with your offering.")
        mob.sendMessage(sb)
        mob.graphic(Graphic(247))
        mob.animation(Animations.RANGE_COOKING)
        mob.prayer.addExperience(bone.exp * xpBonus)
        mob.interact(altar)
    }

    override fun remove(): MutableList<Item> = arrayListOf(Item(bone.id))
}