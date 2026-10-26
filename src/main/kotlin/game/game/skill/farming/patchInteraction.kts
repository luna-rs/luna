package game.skill.farming

import api.predef.*
import game.skill.farming.action.*
import game.skill.farming.bush.*
import game.skill.farming.flower.*
import game.skill.farming.patch.*
import game.skill.farming.special.*
import game.skill.farming.tree.*

// Registers the hooks of every farming patch. Luna allows one handler per item and object, so a hook shared by several
// patch types routes to each type's own interaction, found in that type's package.
Farming.LOCATIONS.forEach { location ->
    val objectId = location.objectId
    val isTreePatch = location.createPatch() is AbstractTreePatch
    val isFlowerPatch = location.createPatch() is FlowerPatch
    val isHealthCheckPatch = location.createPatch() is HealthCheckPatch
    val isBushPatch = location.createPatch() is BushPatch
    val isBelladonnaPatch = location.createPatch() is BelladonnaPatch

    // Hook for raking patches
    useItem(Farming.RAKE).onObject(objectId) {
        plr.submitAction(RakePatchAction(plr, gameObject))
    }

    // Hook for clearing dead crops, digging up tree stumps, scarecrows and bushes
    useItem(Farming.SPADE).onObject(objectId) {
        val patch = Farming.findPatch(objectId, plr)
        if (patch != null && patch.dead) {
            clearDead(plr, patch)
        } else if (patch is AbstractTreePatch) {
            digStump(plr, patch)
        } else if (patch is FlowerPatch && patch.scarecrow) {
            digScarecrow(plr, patch)
        } else if (patch is BushPatch) {
            digUpBush(plr, patch)
        }
    }

    // Hook for harvesting, picking flowers, or checking the health of and picking from trees, cacti and bushes
    object1(objectId) {
        when {
            isTreePatch -> treeOption(plr, objectId)
            isFlowerPatch -> pickFlower(plr, objectId)
            isHealthCheckPatch -> checkHealthOrPick(plr, objectId)
            isBelladonnaPatch -> pickBelladonna(plr, gameObject)
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

    // Hook for curing diseased crops, or pruning diseased trees and bushes
    if (isTreePatch || isBushPatch) {
        listOf(Farming.SECATEURS, Farming.MAGIC_SECATEURS).forEach { secateurs ->
            useItem(secateurs).onObject(objectId) { prune(plr, objectId) }
        }
    } else {
        useItem(Farming.PLANT_CURE).onObject(objectId) { cure(plr, objectId) }
    }
}
