package world.player.skill.crafting.glassMaking

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.Player

/**
 * An [InventoryAction] that blows molten glass into a [GlassMaterial].
 */
class GlassBlowingActionItem(plr: Player, private val material: GlassMaterial, amount: Int) : InventoryAction(plr, true, 2, amount) {

    override fun executeIf(start: Boolean): Boolean = true
    override fun execute() {
        mob.animation(Animation(884))
        mob.crafting.addExperience(material.exp)
        mob.sendMessage("You turn the molten glass into ${articleItemName(material.id)}.")
    }

    override fun add() = listOf(Item(material.id))
    override fun remove() = listOf(Item(1775))

    override fun ignoreIf(other: Action<*>?): Boolean =
        when (other) {
            is MakeMoltenGlassActionItem -> true
            else -> false
        }
}