package world.player.skill.crafting.hideTanning

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.AmountInputInterface
import kotlin.collections.set

/**
 * A data class representing the amount of a hide to tan.
 */
data class TanButton(val hide: Hide, val amount: TanAmount) {
    fun forAmount(plr: Player, action: (Int) -> Unit) {
        when (amount) {
            TanAmount.TAN_1 -> action(1)
            TanAmount.TAN_5 -> action(5)
            TanAmount.TAN_X -> plr.interfaces.open(object : AmountInputInterface() {
                override fun onAmountInput(player: Player?, value: Int) = action(value)
            })

            TanAmount.TAN_ALL -> action(plr.inventory.computeAmountForId(hide.hide))
        }
    }
}

/**
 * Tanning amounts.
 */
enum class TanAmount {
    TAN_1, TAN_5, TAN_X, TAN_ALL
}

/**
 * Mappings of button identifiers to the amount of hides made.
 */
val buttonToTan = mutableMapOf<Int, TanButton>()

/**
 * Tans the specified amount of hides.
 */
fun tan(plr: Player, hide: Hide, selectedAmount: Int) {
    val inv = plr.inventory
    var makeAmount = inv.computeAmountForId(hide.hide)
    if (makeAmount == 0) {
        plr.sendMessage("You do not have any of these hides to tan.")
        return
    }

    if (makeAmount > selectedAmount) {
        makeAmount = selectedAmount
    }

    val totalCost = hide.cost * makeAmount
    val totalMoney = inv.computeAmountForId(995)
    if (totalCost > totalMoney) {
        plr.sendMessage("You need ${numF(totalCost - totalMoney)} more coins to tan these.")
        return
    }

    val moneyItem = Item(995, totalCost)
    if (inv.remove(moneyItem)) {
        inv.replace(hide.hide, hide.tan, makeAmount)
        plr.sendMessage("The tanner tans $makeAmount hides for you.")
    }
}

/**
 * Opens the tanning interface.
 */
fun open(plr: Player) = plr.interfaces.open(TanInterface())

// Prepare tanning buttons and spawn tanner NPC.
on(ServerLaunchEvent::class) {

    world.addNpc(804, 3093, 3250)

    var button = 14817
    for (it in TanInterface.HIDES) {
        val make1 = button++
        val make5 = make1 - 8
        val makeX = make5 - 8
        val makeAll = makeX - 8
        buttonToTan[make1] = TanButton(it, TanAmount.TAN_1)
        buttonToTan[make5] = TanButton(it, TanAmount.TAN_5)
        buttonToTan[makeX] = TanButton(it, TanAmount.TAN_X)
        buttonToTan[makeAll] = TanButton(it, TanAmount.TAN_ALL)
    }
}

// Tanning button actions (1, 5, 10, X).
on(ButtonClickEvent::class)
    .filter { plr.interfaces.isOpen(TanInterface::class) }
    .then {
        val tan = buttonToTan[id]
        tan?.forAmount(plr) { tan(plr, tan.hide, it) }
    }

// "Talk" option for tanner NPC.
npc1(804) {
    plr.newDialogue()
        .npc(targetNpc.id, "Would you like me to tan some hides?")
        .options("Yes", { open(it) },
                 "No", { it.interfaces.close() })
        .open()
}

// "Trade" option for tanner NPC.
npc2(804) { open(plr) }