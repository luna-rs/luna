package game.skill.farming

import api.predef.*
import game.player.Sound
import game.skill.farming.action.*
import game.skill.farming.patch.*
import game.skill.farming.seed.*
import game.skill.woodcutting.cutTree.Axe
import io.luna.game.event.impl.*
import io.luna.game.model.item.*
import io.luna.game.model.mob.*
import io.luna.game.model.mob.varp.*

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
            val dose = Farming.WATERING_CANS.indexOf(can)
            val lowerCan = if (dose == 0) Farming.EMPTY_WATERING_CAN else Farming.WATERING_CANS[dose - 1]
            plr.animation(Farming.WATERING_ANIMATION)
            plr.inventory.replace(can, lowerCan)
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
 * Prunes the diseased tree in the tree or fruit tree patch [objectId] with secateurs or magic secateurs. Each attempt
 * has a [AbstractTreePatch.PRUNE_CHANCE] of removing all of the diseased leaves.
 */
fun prune(plr: Player, objectId: Int) {
    val patch = Farming.findPatch(objectId, plr) as? AbstractTreePatch ?: return
    if (!patch.diseased) {
        plr.sendMessage("This tree doesn't need pruning.")
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
 * Handles the first option of the fully grown tree in the tree or fruit tree patch [objectId]: checking its health,
 * which gives the tree's [Crop.checkHealthXp], then picking its fruit while it has any, then chopping it down.
 */
fun treeOption(plr: Player, objectId: Int) {
    val patch = Farming.findPatch(objectId, plr) as? AbstractTreePatch ?: return
    val crop = patch.crop ?: return
    when {
        patch.canCheckHealth() -> {
            patch.checkHealth()
            plr.farming.addExperience(crop.checkHealthXp)
            plr.sendMessage("You examine the tree for signs of disease and find that it is in perfect health.")
            Farming.commit(plr)
        }

        patch is FruitTreePatch && patch.canPick() -> plr.submitAction(PickFruitAction(plr, patch))
        patch.canChop() -> {
            val axe = Axe.computeAxeType(plr)
            if (axe == null) {
                Axe.sendNoAxeMessages(plr)
                return
            }
            when (patch) {
                is TreePatch -> plr.submitAction(ChopPatchTreeAction(plr, axe, patch, crop as TreeSapling))
                is FruitTreePatch -> chopFruitTree(plr, axe, patch)
            }
        }
    }
}

/**
 * Chops down the fruit tree in [patch] with [axe]. It needs no Woodcutting level, gives no logs or experience, and
 * falls on the first swing, leaving a stump until it is dug up.
 */
fun chopFruitTree(plr: Player, axe: Axe, patch: FruitTreePatch) {
    plr.animation(axe.animation)
    plr.playSound(Sound.TREE_FALLEN)
    patch.fell()
    Farming.commit(plr)
}

/**
 * Digs up the stump in the tree or fruit tree [patch], which empties the patch. A tree stump also gives the tree's
 * roots.
 */
fun digStump(plr: Player, patch: AbstractTreePatch) {
    val roots = (patch.crop as? TreeSapling)?.roots
    when {
        patch.harvestReady() && !patch.stump -> plr.sendMessage("You need to chop this tree down first.")
        !patch.stump -> return
        roots != null && !plr.inventory.hasSpaceFor(Item(roots)) ->
            plr.sendMessage("You don't have enough inventory space.")

        else -> {
            plr.animation(Farming.DIG_ANIMATION)
            roots?.let { plr.inventory.add(Item(it)) }
            plr.sendMessage("You dig up the tree stump.")
            patch.clear()
            Farming.commit(plr)
        }
    }
}

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

Farming.LOCATIONS.forEach { location ->
    val objectId = location.objectId
    val isTreePatch = location.createPatch() is AbstractTreePatch
    val isFlowerPatch = location.createPatch() is FlowerPatch

    // Hook for raking patches
    useItem(Farming.RAKE).onObject(objectId) {
        plr.submitAction(RakePatchAction(plr, gameObject))
    }

    // Hook for clearing dead crops, digging up tree stumps and digging up scarecrows
    useItem(Farming.SPADE).onObject(objectId) {
        val patch = Farming.findPatch(objectId, plr)
        if (patch != null && patch.dead) {
            plr.animation(Farming.DIG_ANIMATION)
            plr.sendMessage("You clear the dead plants away.")
            patch.clear()
            Farming.commit(plr)
        } else if (patch is AbstractTreePatch) {
            digStump(plr, patch)
        } else if (patch is FlowerPatch && patch.scarecrow) {
            digScarecrow(plr, patch)
        }
    }

    // Hook for harvesting, picking flowers, or checking the health of, picking from and chopping down trees
    object1(objectId) {
        when {
            isTreePatch -> treeOption(plr, objectId)
            isFlowerPatch -> pickFlower(plr, objectId)
            else -> plr.submitAction(HarvestAction(plr, gameObject))
        }
    }

    // Hook for placing scarecrows in flower patches
    if (isFlowerPatch) {
        useItem(Farming.SCARECROW).onObject(objectId) { placeScarecrow(plr, objectId) }
    }

    // Hook for planting seeds
    location.createPatch().crops.forEach { crop ->
        useItem(crop.seedId).onObject(objectId) { plant(plr, objectId, crop) }
    }

    // Hook for watering
    Farming.WATERING_CANS.forEach { can ->
        useItem(can).onObject(objectId) { water(plr, objectId, can) }
    }
    useItem(Farming.EMPTY_WATERING_CAN).onObject(objectId) {
        plr.sendMessage("This watering can is empty.")
    }

    // Hook for compost and supercompost
    CompostType.values().filter { it != CompostType.NONE }.forEach { type ->
        useItem(type.itemId).onObject(objectId) { compost(plr, objectId, type) }
    }

    // Hook for curing diseased crops, or pruning diseased trees
    if (isTreePatch) {
        listOf(Farming.SECATEURS, Farming.MAGIC_SECATEURS).forEach { secateurs ->
            useItem(secateurs).onObject(objectId) { prune(plr, objectId) }
        }
    } else {
        useItem(Farming.PLANT_CURE).onObject(objectId) { cure(plr, objectId) }
    }
}

cmd("resetpatches", RIGHTS_ADMIN) {
    Farming.patches(plr).values.forEach { it.reset(true) }
    Farming.commit(plr)
}

cmd("rakepatches", RIGHTS_ADMIN) {
    Farming.patches(plr).values.forEach { it.reset(false) }
    Farming.commit(plr)
    plr.sendMessage("Raked patches")
}

cmd("progressplants", RIGHTS_ADMIN) {
    val rounds = asInt(0) ?: 1
    Farming.patches(plr).values.forEach { patch ->
        for (i in 0 until rounds) {
            patch.tick()
        }
    }
    Farming.commit(plr)
}

/**
 * Plants a crop in every patch that accepts it, straight at a stage, for testing. Usage:
 * `::setcrop <crop> <stage> [watered|diseased|dead]`, for example `::setcrop potato 3 diseased`.
 */
cmd("setcrop", RIGHTS_ADMIN) {
    val usage = "Usage: ::setcrop <crop> <stage> [watered|diseased|dead]"
    val cropName = args.getOrNull(0)?.uppercase()
    val stageArg = asInt(1)
    val state = args.getOrNull(2)?.lowercase()
    if (cropName == null || stageArg == null || state !in listOf(null, "watered", "diseased", "dead")) {
        plr.sendMessage(usage)
        return@cmd
    }
    var planted = 0
    Farming.patches(plr).values.forEach { patch ->
        val crop = patch.crops.find { it.name == cropName } ?: return@forEach
        val stage = stageArg.coerceIn(1, crop.stages)
        patch.reset(false)
        patch.plant(crop, System.currentTimeMillis())
        patch.stage = stage
        when (state) {
            "watered" -> patch.watered = patch.canWater()
            "diseased" -> patch.diseased = patch.canBeDiseased(stage)
            "dead" -> patch.dead = patch.canBeDiseased(stage)
        }
        if (patch.harvestReady()) {
            patch.harvestLives = patch.startingLives()
        }
        planted++
    }
    if (planted == 0) {
        plr.sendMessage("No patch accepts the crop '${args[0]}'.")
        return@cmd
    }
    Farming.commit(plr)
    plr.sendMessage("Set $planted patches to ${cropName.lowercase()} at stage ${stageArg}${state?.let { " ($it)" } ?: ""}.")
}

// Schedule task updating farms every minute
world.schedule(UpdateFarmsTask(world.players))

// Bring patches up to date and send farming state when logged in
on(LoginEvent::class) {
    Farming.update(plr)
    Farming.sendPatches(plr, true)
}

useItem(6032).onObject(7836) {
    plr.sendMessage("Composting")
    plr.sendVarp(Varp(Farming.COMPOST_BIN_VARP, 10))
}

useItem(6034).onObject(7836) {
    plr.sendVarp(Varp(Farming.SUPERCOMPOST_BIN_VARP, 10))
    plr.sendMessage("Supercomposting")
}
