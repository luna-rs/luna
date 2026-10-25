package game.skill.farming.flower

import api.predef.*
import game.skill.farming.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Picks the fully grown flower in the flower patch [objectId]. One pick gives the flower's whole yield, its harvest and
 * planting experience, and leaves the patch empty.
 */
fun pickFlower(plr: Player, objectId: Int) {
    val patch = Farming.findPatch(objectId, plr) as? FlowerPatch ?: return
    val seed = patch.crop as? FlowerSeed ?: return
    if (!patch.harvestReady()) {
        return
    }
    val amount = seed.amount(plr.farming.level)
    val produce = Item(seed.produce.id, amount)
    if (!plr.inventory.hasSpaceFor(produce)) {
        plr.sendMessage("You don't have enough inventory space.")
        return
    }
    plr.animation(patch.harvestAnimation)
    plr.inventory.add(produce)
    plr.farming.addExperience(seed.harvestXp + seed.plantXp)
    val name = itemName(seed.produce.id).lowercase()
    plr.sendMessage(if (amount == 1) "You pick ${addArticle(name)}." else "You pick some $name.")
    patch.clear()
    Farming.commit(plr)
}

/**
 * Places a scarecrow in the flower patch [objectId], if the patch is weeded and empty.
 */
fun placeScarecrow(plr: Player, objectId: Int) {
    val patch = Farming.findPatch(objectId, plr) as? FlowerPatch ?: return
    when {
        patch.needsRaking() -> plr.sendMessage("This patch needs weeding first.")
        patch.isOccupied() -> plr.sendMessage("There is already something growing in this patch.")
        else -> {
            plr.inventory.remove(Item(Farming.SCARECROW))
            patch.placeScarecrow()
            plr.sendMessage("You place the scarecrow in the ${patch.patchName}.")
            Farming.commit(plr)
        }
    }
}

/**
 * Digs up the scarecrow in the flower [patch], giving it back and leaving the patch weeded and empty.
 */
fun digScarecrow(plr: Player, patch: FlowerPatch) {
    if (!plr.inventory.hasSpaceFor(Item(Farming.SCARECROW))) {
        plr.sendMessage("You don't have enough inventory space.")
        return
    }
    plr.animation(Farming.DIG_ANIMATION)
    plr.inventory.add(Item(Farming.SCARECROW))
    plr.sendMessage("You dig up the scarecrow.")
    patch.clear()
    Farming.commit(plr)
}
