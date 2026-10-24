package game.skill.farming

import api.predef.*
import game.skill.farming.action.*
import game.skill.farming.patch.*
import game.skill.farming.seed.*
import io.luna.game.event.impl.*
import io.luna.game.model.item.*
import io.luna.game.model.mob.*
import io.luna.game.model.mob.varp.*

/**
 * Plants [crop] in the patch [objectId], if the patch is weeded and empty and the player has the level, the seeds and
 * a seed dibber.
 */
fun plant(plr: Player, objectId: Int, crop: Crop) {
    val patch = Farming.findPatch(objectId, plr) ?: return
    val seeds = patch.seedsRequired
    val seedName = itemName(crop.seedId).lowercase()
    when {
        patch.needsRaking() -> plr.sendMessage("This patch needs weeding first.")
        patch.hasPlant() -> plr.sendMessage("There is already something growing in this patch.")
        plr.farming.level < crop.level ->
            plr.sendMessage("You need a Farming level of ${crop.level} to plant ${seedName}s.")

        plr.inventory.computeAmountForId(crop.seedId) < seeds ->
            plr.sendMessage("You need $seeds ${seedName}s to plant this patch.")

        !plr.inventory.contains(Farming.SEED_DIBBER) -> plr.sendMessage("You need a seed dibber to plant seeds.")
        else -> {
            plr.animation(Farming.SEED_DIBBING_ANIMATION)
            plr.inventory.remove(Item(crop.seedId, seeds))
            patch.plant(crop, System.currentTimeMillis())
            val planted = if (seeds == 1) "a $seedName" else "$seeds ${seedName}s"
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

Farming.LOCATIONS.forEach { location ->
    val objectId = location.objectId

    // Hook for raking patches
    useItem(Farming.RAKE).onObject(objectId) {
        plr.submitAction(RakePatchAction(plr, gameObject))
    }

    // Hook for clearing dead crops
    useItem(Farming.SPADE).onObject(objectId) {
        val patch = Farming.findPatch(objectId, plr)
        if (patch != null && patch.dead) {
            plr.animation(Farming.DIG_ANIMATION)
            plr.sendMessage("You clear the dead plants away.")
            patch.clear()
            Farming.commit(plr)
        }
    }

    // Hook for harvesting
    object1(objectId) {
        plr.submitAction(HarvestAction(plr, gameObject))
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

    // Hook for curing diseased crops
    useItem(Farming.PLANT_CURE).onObject(objectId) { cure(plr, objectId) }
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
