package game.skill.farming.patch

import api.predef.*
import game.skill.farming.*
import game.skill.farming.action.*
import game.skill.farming.bush.*
import game.skill.farming.tree.*
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Plants [crop] in the patch [objectId], if the patch is weeded and empty and the player has the level, the seeds and
 * the patch's [FarmingPatch.plantTool].
 */
fun plant(plr: Player, objectId: Int, crop: Crop) {
    val patch = Farming.findPatch(objectId, plr) ?: return
    val seeds = patch.seedsRequired(crop)
    val seedName = itemName(crop.seedId).lowercase()
    val tool = patch.plantTool
    when {
        patch.needsRaking() -> plr.sendMessage("This patch needs weeding first.")
        patch.isOccupied() -> plr.sendMessage("There is already something growing in this patch.")
        plr.farming.level < crop.level ->
            plr.sendMessage("You need a Farming level of ${crop.level} to plant ${seedName}s.")

        plr.inventory.computeAmountForId(crop.seedId) < seeds ->
            plr.sendMessage("You need $seeds ${seedName}s to plant this patch.")

        !plr.inventory.contains(tool) -> if (tool == Farming.SEED_DIBBER) {
            plr.sendMessage("You need a seed dibber to plant seeds.")
        } else {
            plr.sendMessage("You need ${articleItemName(tool).lowercase()} to plant ${addArticle(seedName)}.")
        }

        else -> {
            plr.animation(patch.plantAnimation)
            plr.inventory.remove(Item(crop.seedId, seeds))
            patch.plantLeftover?.let { plr.inventory.add(Item(it, seeds)) }
            patch.plant(crop, System.currentTimeMillis())
            if (patch.plantXpOnPlanting) {
                plr.farming.addExperience(crop.plantXp)
            }
            val planted = if (seeds == 1) addArticle(seedName) else "$seeds ${seedName}s"
            plr.sendMessage("You plant $planted in the ${patch.patchName}.")
            Farming.commit(plr)
        }
    }
}

/**
 * Waters the crop in the patch [objectId] with the watering can [can], which loses one dose of water.
 */
fun water(plr: Player, objectId: Int, can: Int) {
    val patch = Farming.findPatch(objectId, plr) ?: return
    when {
        patch.canBeWatered && patch.hasPlant() && patch.watered ->
            plr.sendMessage("This patch has already been watered.")

        !patch.canWater() -> plr.sendMessage("This patch doesn't need watering.")
        else -> {
            plr.animation(Farming.WATERING_ANIMATION)
            plr.inventory.replace(can, Farming.lowerWateringCan(can))
            patch.water()
            plr.farming.addExperience(Farming.WATER_XP)
            plr.sendMessage("You water the ${patch.patchName}.")
            Farming.commit(plr)
        }
    }
}

/**
 * Treats the patch [objectId] with compost [type], which leaves an empty bucket.
 */
fun compost(plr: Player, objectId: Int, type: CompostType) {
    val patch = Farming.findPatch(objectId, plr) ?: return
    when {
        patch.needsRaking() -> plr.sendMessage("This patch needs weeding first.")
        patch.compost != CompostType.NONE -> plr.sendMessage("This patch has already been treated with compost.")
        !patch.canApplyCompost() -> plr.sendMessage("This patch doesn't need compost.")
        else -> {
            plr.animation(Farming.COMPOST_ANIMATION)
            plr.inventory.replace(type.itemId, Farming.BUCKET)
            patch.applyCompost(type)
            plr.farming.addExperience(type.experience)
            plr.sendMessage("You treat the ${patch.patchName} with ${type.name.lowercase()}.")
            Farming.commit(plr)
        }
    }
}

/**
 * Cures the diseased crop in the patch [objectId] with a plant cure, which leaves an empty vial.
 */
fun cure(plr: Player, objectId: Int) {
    val patch = Farming.findPatch(objectId, plr) ?: return
    if (!patch.diseased) {
        plr.sendMessage("This patch doesn't need curing.")
        return
    }
    plr.animation(Farming.PLANT_CURE_ANIMATION)
    plr.inventory.replace(Farming.PLANT_CURE, Farming.VIAL)
    patch.cure()
    plr.farming.addExperience(Farming.CURE_XP)
    plr.sendMessage("You treat the ${patch.patchName} with the plant cure.")
    plr.sendMessage("It is restored to health.")
    Farming.commit(plr)
}

/**
 * Prunes the diseased tree or bush in the tree, fruit tree or bush patch [objectId] with secateurs or magic secateurs.
 * Each attempt has a [AbstractTreePatch.PRUNE_CHANCE] of removing all of the diseased leaves.
 */
fun prune(plr: Player, objectId: Int) {
    val patch = Farming.findPatch(objectId, plr) ?: return
    if (patch !is AbstractTreePatch && patch !is BushPatch) {
        return
    }
    if (!patch.diseased) {
        plr.sendMessage(if (patch is BushPatch) "This bush doesn't need pruning." else "This tree doesn't need pruning.")
        return
    }
    plr.animation(Farming.PRUNE_ANIMATION)
    if (!rand(AbstractTreePatch.PRUNE_CHANCE)) {
        plr.sendMessage("There are still some diseased leaves left.")
        return
    }
    patch.cure()
    plr.farming.addExperience(Farming.CURE_XP)
    plr.sendMessage("You have successfully removed all the diseased leaves.")
    Farming.commit(plr)
}

/**
 * Clears the dead crop away from [patch] with a spade, leaving it weeded and empty.
 */
fun clearDead(plr: Player, patch: FarmingPatch) {
    plr.animation(Farming.DIG_ANIMATION)
    plr.sendMessage("You clear the dead plants away.")
    patch.clear()
    Farming.commit(plr)
}

/**
 * Handles the first option of the fully grown crop in the [HealthCheckPatch] [objectId], such as a cactus or a bush:
 * checking its health, which gives the crop's [Crop.checkHealthXp], then picking from it.
 */
fun checkHealthOrPick(plr: Player, objectId: Int) {
    val patch = Farming.findPatch(objectId, plr) ?: return
    val crop = patch.crop ?: return
    if (patch !is HealthCheckPatch || patch !is PickablePatch) {
        return
    }
    when {
        patch.canCheckHealth() -> {
            patch.checkHealth()
            plr.farming.addExperience(crop.checkHealthXp)
            plr.sendMessage("You examine the ${patch.checkedName} for signs of disease and find that it is in perfect health.")
            Farming.commit(plr)
        }

        patch.canPick() -> plr.submitAction(PickFruitAction(plr, patch))
    }
}
