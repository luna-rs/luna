package world.player.skill.crafting.gemCutting

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.ProducingAction
import io.luna.game.event.Event
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import world.player.skill.crafting.gemCutting.Gem.Companion.CHISEL
import world.player.skill.crafting.gemCutting.Gem.Companion.UNCUT_TO_GEM

/**
 * A [ProducingAction] that cuts precious gems.
 */
class CutGemAction(val plr: Player, val gem: Gem, var amount: Int) : ProducingAction(plr, true, 3) {


    override fun remove() = arrayOf(gem.uncutItem)
    override fun add() = arrayOf(gem.cutItem)

    override fun canInit(): Boolean = canProduce()
    override fun canProduce(): Boolean =
        when {
            mob.crafting.level < gem.level -> {
                plr.sendMessage("You need a Crafting level of ${gem.level} to cut this.")
                false
            }
            amount == 0 -> false
            else -> true
        }

    override fun onProduce() {
        plr.animation(gem.animation)
        plr.sendMessage("You cut the ${itemDef(gem.cut).name}.")

        mob.crafting.addExperience(gem.exp)
        amount--
    }

    override fun isEqual(other: Action<*>?): Boolean =
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
        msg.terminate()
    }
}

// Use chisel with gems.
on(ItemOnItemEvent::class) {
    when (CHISEL) {
        usedId -> cutGem(plr, this, targetId)
        targetId -> cutGem(plr, this, usedId)
    }
}
