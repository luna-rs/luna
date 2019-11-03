import Gem.Companion.CHISEL
import Gem.Companion.UNCUT_TO_GEM
import io.luna.game.action.Action
import io.luna.game.action.InventoryAction
import io.luna.game.event.Event
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface

/**
 * An [InventoryAction] that cuts precious gems.
 */
class CutGemAction(val plr: Player, val gem: Gem, amount: Int) : InventoryAction(plr, true, 3, amount) {


    override fun remove() = listOf(gem.uncutItem)
    override fun add() = listOf(gem.cutItem)

    override fun executeIf(start: Boolean): Boolean =
        when {
            mob.crafting.level < gem.level -> {
                plr.sendMessage("You need a Crafting level of ${gem.level} to cut this.")
                false
            }
            else -> true
        }

    override fun execute() {
        plr.animation(gem.animation)
        plr.sendMessage("You cut the ${itemDef(gem.cut).name}.")

        mob.crafting.addExperience(gem.exp)
    }

    override fun ignoreIf(other: Action<*>?): Boolean =
        when (other) {
            is CutGemAction -> gem == other.gem
            else -> false
        }
}

/**
 * Opens the gem cutting interface.
 */
fun cutGem(plr: Player, msg: Event, uncut: Int) {
    val gem = UNCUT_TO_GEM[uncut]
    if (gem != null) {
        plr.interfaces.open(object : MakeItemDialogueInterface(gem.cut) {
            override fun makeItem(player: Player?, id: Int, index: Int, forAmount: Int) =
                plr.submitAction(CutGemAction(plr, gem, forAmount))
        })
    }
}

// Use chisel with gems.
on(ItemOnItemEvent::class) {
    when (CHISEL) {
        usedId -> cutGem(plr, this, targetId)
        targetId -> cutGem(plr, this, usedId)
    }
}
