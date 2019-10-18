package world.player.skill.crafting.battlestaffCrafting

import api.predef.crafting
import api.predef.on
import io.luna.game.action.Action
import io.luna.game.action.InventoryAction
import io.luna.game.event.Event
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import world.player.skill.crafting.battlestaffCrafting.Battlestaff.Companion.BATTLESTAFF
import world.player.skill.crafting.battlestaffCrafting.Battlestaff.Companion.BATTLESTAFF_ITEM
import world.player.skill.crafting.battlestaffCrafting.Battlestaff.Companion.ORB_TO_BATTLESTAFF

/**
 * An [InventoryAction] implementation that makes battlestaves.
 */
class MakeBattlestaffAction(val plr: Player, val battlestaff: Battlestaff, amount: Int) :
        InventoryAction(plr, true, 2, amount) {

    override fun executeIf(start: Boolean): Boolean =
        when {
            mob.crafting.level < battlestaff.level -> {
                plr.sendMessage("You need a Crafting level of ${battlestaff.level} to make this.")
                false
            }
            else -> true
        }

    override fun execute() {
        mob.crafting.addExperience(battlestaff.exp)
    }

    override fun add() = listOf(battlestaff.staffItem)
    override fun remove() = listOf(battlestaff.orbItem, BATTLESTAFF_ITEM)

    override fun ignoreIf(other: Action<*>?): Boolean =
        when (other) {
            is MakeBattlestaffAction -> battlestaff == other.battlestaff
            else -> false
        }
}

/**
 * Opens the battlestaff making interface.
 */
fun attachOrb(plr: Player, msg: Event, orb: Int) {
    val battlestaff = ORB_TO_BATTLESTAFF[orb]
    if (battlestaff != null) {
        plr.interfaces.open(object : MakeItemDialogueInterface(battlestaff.staff) {
            override fun makeItem(player: Player?, id: Int, index: Int, forAmount: Int) =
                plr.submitAction(MakeBattlestaffAction(plr, battlestaff, forAmount))
        })
    }
}

// Use battlestaff with orb.
on(ItemOnItemEvent::class) {
    when (BATTLESTAFF) {
        usedId -> attachOrb(plr, this, targetId)
        targetId -> attachOrb(plr, this, usedId)
    }
}