package world.player.skill.crafting.potteryCrafting

import api.predef.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface

/**
 * A list of pottery wheel object IDs.
 */
val pottersWheelObjects = listOf(2642, 4310)

/**
 * A list of pottery oven object IDs.
 */
val potteryOvenObjects = listOf(2643, 4308, 11601)

/**
 * Opens the pottery wheel interface for all materials.
 */
fun openPottersWheel(plr: Player) {
    plr.interfaces.open(object : MakeItemDialogueInterface(*Unfired.UNFIRED_ID_ARRAY) {
        override fun makeItem(player: Player, id: Int, index: Int, forAmount: Int) {
            val unfired = Unfired.UNFIRED_ID_MAP[id]
            if (unfired != null) {
                plr.submitAction(PotteryWheelActionItem(player, unfired, forAmount))
            }
        }
    })
}

/**
 * Opens the pottery oven interface for all materials.
 */
fun openPotteryOven(plr: Player) {
    plr.interfaces.open(object : MakeItemDialogueInterface(*Unfired.FIRED_ID_ARRAY) {
        override fun makeItem(player: Player, id: Int, index: Int, forAmount: Int) {
            val unfired = Unfired.FIRED_ID_MAP[id]
            if (unfired != null) {
                plr.submitAction(PotteryOvenActionItem(player, unfired, forAmount))
            }
        }
    })
}

/**
 * Opens the pottery oven interface for just a single material.
 */
fun openSpecializedPotteryOven(plr: Player, unfired: Unfired) {
    plr.interfaces.open(object : MakeItemDialogueInterface(unfired.firedId) {
        override fun makeItem(player: Player, id: Int, index: Int, forAmount: Int) {
            plr.submitAction(PotteryOvenActionItem(player, unfired, forAmount))
        }
    })
}

// Open the full interface on pottery wheel click, or when using soft clay on the pottery wheel.
pottersWheelObjects.forEach {
    object2(it) { openPottersWheel(plr) }
    useItem(1761).onObject(it) { openPottersWheel(plr) }
}

// Map all oven first clicks to open the full interface, using an unfired material on the oven opens an interface
// with just that item.
potteryOvenObjects.forEach {
    object1(it) { openPotteryOven(plr) }
    Unfired.UNFIRED_ID_MAP.entries.forEach { entry ->
        useItem(entry.key).onObject(it) { openSpecializedPotteryOven(plr, entry.value) }
    }
}
