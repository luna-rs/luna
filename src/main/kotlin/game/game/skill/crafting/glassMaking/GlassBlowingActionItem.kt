package game.skill.crafting.glassMaking

import api.predef.*
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation

/**
 * Converts one molten glass into the selected [GlassMaterial] per production cycle.
 * The glassblowing-pipe interaction and interface buttons select the recipe and requested amount.
 * Current Crafting level is checked on submission and before each conversion, so unsupported recipes
 * and level drains stop production without consuming more glass or awarding experience.
 * Successful conversions retain the pipe and use the recipe's existing output and XP values.
 *
 * @author lare96
 */
class GlassBlowingActionItem(plr: Player, private val material: GlassMaterial, amount: Int) : InventoryAction(plr, true, 2, amount) {

    override fun executeIf(start: Boolean): Boolean =
        if (mob.crafting.level < material.level) {
            mob.sendMessage("You need a Crafting level of ${material.level} to make this.")
            false
        } else {
            true
        }
    override fun execute() {
        mob.animation(Animation(884))
        mob.crafting.addExperience(material.exp)
        mob.sendMessage("You turn the molten glass into ${articleItemName(material.id)}.")
    }

    override fun add() = listOf(Item(material.id))
    override fun remove() = listOf(Item(1775))
}