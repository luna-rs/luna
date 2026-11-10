package game.skill.crafting.hideTanning

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ButtonClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcFirstClickEvent
import io.luna.game.event.impl.NpcClickEvent.NpcSecondClickEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.NumberInput

/**
 * A data class representing the amount of a hide to tan.
 */
data class TanButton(val hide: Hide, val amount: TanAmount) {
    fun forAmount(plr: Player, action: (Int) -> Unit) {
        when (amount) {
            TanAmount.TAN_1 -> action(1)
            TanAmount.TAN_5 -> action(5)
            TanAmount.TAN_X -> plr.overlays.open(object : NumberInput() {
                override fun input(player: Player?, value: Int) = action(value)
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
    Tanning.tan(plr, hide, selectedAmount)
}

/**
 * Opens the tanning interface.
 */
fun open(plr: Player) = plr.overlays.open(TanInterface())

// Prepare tanning buttons.
on(ServerLaunchEvent::class) {
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
        TanInterface.hideToMakeAll[it] = makeAll
    }
}

// Tanning button actions (1, 5, 10, X).
on(ButtonClickEvent::class)
    .filter { TanInterface::class in plr.overlays }
    .then {
        val tan = buttonToTan[id]
        tan?.forAmount(plr) { tan(plr, tan.hide, it) }
    }

// "Talk" option for tanner NPC.
on(NpcFirstClickEvent::class).match(TanInterface.TANNER_IDS).then {
    plr.newDialogue()
        .npc(targetNpc.id, "Would you like me to tan some hides?")
        .options("Yes", { open(it) },
                 "No", { it.overlays.closeWindows() })
        .open()
}

// "Trade" option for tanner NPC.
on(NpcSecondClickEvent::class).match(TanInterface.TANNER_IDS).then { open(plr) }
