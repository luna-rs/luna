import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.ProducingAction
import io.luna.game.event.Event
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import world.player.skill.crafting.Battlestaff
import world.player.skill.crafting.Battlestaff.Companion.BATTLESTAFF
import world.player.skill.crafting.Battlestaff.Companion.BATTLESTAFF_ITEM
import world.player.skill.crafting.Battlestaff.Companion.ORB_TO_BATTLESTAFF

/**
 * A [ProducingAction] implementation that makes battlestaves.
 */
class MakeBattlestaffAction(val plr: Player, val battlestaff: Battlestaff, var amount: Int) :
        ProducingAction(plr, true, 2) {
    val skill = plr.skill(SKILL_CRAFTING)

    override fun canProduce(): Boolean =
        when {
            skill.level < battlestaff.level -> {
                plr.sendMessage("You need a Crafting level of ${battlestaff.level} to craft this.")
                false
            }
            amount == 0 -> false
            else -> true
        }

    override fun onProduce() {
        skill.addExperience(battlestaff.exp)
        amount--
    }

    override fun add() = arrayOf(battlestaff.staffItem)
    override fun remove() = arrayOf(battlestaff.orbItem, BATTLESTAFF_ITEM)

    override fun isEqual(other: Action<*>?): Boolean =
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
        msg.terminate()
    }
}

on(ItemOnItemEvent::class) {
    when (BATTLESTAFF) {
        usedId -> attachOrb(plr, this, targetId)
        targetId -> attachOrb(plr, this, usedId)
    }
}