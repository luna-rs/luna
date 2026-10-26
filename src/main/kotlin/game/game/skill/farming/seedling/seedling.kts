package game.skill.farming.seedling

import api.predef.*
import game.skill.farming.*
import io.luna.game.model.item.*
import io.luna.game.model.mob.*

/**
 * Fills an empty plant pot with soil from the patch [objectId], if the patch is weeded and empty and the player has a
 * gardening trowel.
 */
fun fillPlantPot(plr: Player, objectId: Int) {
    val patch = Farming.findPatch(objectId, plr) ?: return
    when {
        patch.needsRaking() -> plr.sendMessage("This patch needs weeding first.")
        patch.isOccupied() -> plr.sendMessage("There is already something growing in this patch.")
        !plr.inventory.contains(Farming.GARDENING_TROWEL) -> plr.sendMessage("You need a gardening trowel to do that.")
        else -> {
            plr.animation(Farming.FILL_PLANT_POT_ANIMATION)
            plr.inventory.replace(Farming.PLANT_POT, Farming.FILLED_PLANT_POT)
            plr.sendMessage("You fill the plant pot with soil.")
        }
    }
}

/**
 * Plants the seed of [seedling] in the filled plant pot [pot], if the player has a gardening trowel and the tree's
 * Farming level.
 */
fun plantSeedling(plr: Player, seedling: TreeSeedling, pot: Int) {
    val seedName = itemName(seedling.seed).lowercase()
    when {
        plr.farming.level < seedling.level ->
            plr.sendMessage("You need a Farming level of ${seedling.level} to plant ${seedName}s.")

        !plr.inventory.contains(Farming.GARDENING_TROWEL) -> plr.sendMessage("You need a gardening trowel to do that.")
        else -> {
            plr.animation(Farming.TROWEL_ANIMATION)
            plr.inventory.remove(Item(seedling.seed))
            plr.inventory.replace(pot, seedling.seedling)
            plr.sendMessage("You plant the $seedName in the plant pot.")
        }
    }
}

/**
 * Waters the seedling of [seedling] with the watering can [can], which loses one dose of water. The watered seedling
 * grows into a sapling on the next growth tick, see [Farming.growSeedlings].
 */
fun waterSeedling(plr: Player, seedling: TreeSeedling, can: Int) {
    plr.animation(Farming.WATERING_ANIMATION)
    plr.inventory.replace(can, Farming.lowerWateringCan(can))
    plr.inventory.replace(seedling.seedling, seedling.wateredSeedling)
    plr.sendMessage("You water the seedling.")
}

// Hook for filling plant pots with soil from any farming patch
Farming.LOCATIONS.forEach { location ->
    useItem(Farming.PLANT_POT).onObject(location.objectId) { fillPlantPot(plr, location.objectId) }
}

TreeSeedling.values().forEach { seedling ->

    // Hook for planting seeds in filled plant pots
    Farming.FILLED_PLANT_POTS.forEach { pot ->
        useItem(seedling.seed).onItem(pot) { plantSeedling(plr, seedling, pot) }
    }

    // Hook for watering seedlings
    Farming.WATERING_CANS.forEach { can ->
        useItem(can).onItem(seedling.seedling) { waterSeedling(plr, seedling, can) }
    }
    useItem(Farming.EMPTY_WATERING_CAN).onItem(seedling.seedling) {
        plr.sendMessage("This watering can is empty.")
    }
}
