package game.skill.farming

import api.predef.*
import api.predef.ext.*
import game.player.*
import game.skill.farming.Farming.allotmentPatches
import game.skill.farming.Farming.herbPatches
import game.skill.farming.action.*
import game.skill.farming.patch.*
import game.skill.farming.seed.*
import io.luna.game.event.impl.*
import io.luna.game.model.item.*
import io.luna.game.model.mob.varp.*

// config 504 = allotment plots Falador, specific bits for each plot
// config 505 = allotment plots Ardy, probably specific bits for each plot

/* == config 515
 * bitshifting 24 times leftly changes the herb patch in port phatasmys
 * bitshifting 16 times leftly changes the herb patch in ardy
 * bitshifting 12 times leftly changes the herb patch in catherby
 * bitshifting 0 times leftly changes the herb patch in falador
 */

// object 7849 = flower patch ardy

// object 8150 = herb patch falador
// object 8151 = herb patch catherby
// object 8152 = herb patch ardy
// object 8153 = herb patch port phatasmys

// object 8552 = northern allotment patch catherby
// object 8553 = southern allotment patch catherby
// object 8554 = northern allotment patch ardy
// object 8555 = southern allotment patch ardy


/*
 * each herb patch has its own unique id
 */

// todo maybe hook mapupdate event to send correct patches

val rake = 5341
val spade = 952

HerbPatchLocation.values().forEach { location ->
    // Hook for raking herb patches
    useItem(rake).onObject(location.objectId) {
        plr.submitAction(RakePatchAction(plr, gameObject))
    }

    // Hook for clearing dead herbs
    useItem(spade).onObject(location.objectId) {
        var patch: FarmingPatch? = Farming.findPatch(objectId, plr)
        if (patch != null && patch.isDead) {
            plr.animation(Animations.SUPERHEAT)
            plr.sendMessage("You clear the dead plants away.")
            patch.reset(false)
            Farming.sendHerbState(plr)
        }
    }

    // Hook for harvesting herbs
    object1(location.objectId) {
        plr.submitAction(HarvestAction(plr, gameObject))
    }

    // Hook for planting seeds in herb patches
    HerbSeed.values().forEach { seed ->
        useItem(seed.seedId).onObject(location.objectId) {
            var patch: HerbPatch = plr.herbPatches[location] ?: return@onObject

            if (patch.needsRaking()) {
                plr.sendMessage("The patch has weeds in it.")
                return@onObject
            }

            if (!plr.inventory.contains(Item.byName("Seed dibber"))) {
                plr.sendMessage("You need a seed dipper to do this.")
                return@onObject
            }

            plr.animation(Animations.SUPERHEAT)
            plr.sendMessage("You plant the seed.")
            plr.inventory.remove(seed.seedId)
            patch.plant(seed)
            Farming.sendHerbState(plr)
        }
    }
}

AllotmentPatchLocation.values().forEach { location ->
    // Hook for raking allotment patches
    useItem(rake).onObject(location.objectId) {
        plr.submitAction(RakePatchAction(plr, gameObject))
    }

    // Hook for clearing dead herbs
    useItem(spade).onObject(location.objectId) {
        var patch: FarmingPatch? = Farming.findPatch(objectId, plr)
        if (patch != null && patch.isDead) {
            plr.animation(Animations.SUPERHEAT)
            plr.sendMessage("You clear the dead plants away.")
            patch.reset(false)
            Farming.sendAllotmentState(plr)
        }
    }

    // Hook for harvesting allotments
    object1(location.objectId) {
        plr.submitAction(HarvestAction(plr, gameObject))
    }

    // Hook for planting seeds in herb patches
    AllotmentSeed.values().forEach { seed ->
        useItem(seed.seed).onObject(location.objectId) {
            var patch: AllotmentPatch = plr.allotmentPatches[location] ?: return@onObject

            if (patch.needsRaking()) {
                plr.sendMessage("The patch has weeds in it.")
                return@onObject
            }

            if (!plr.inventory.contains(Item.byName("Seed dibber"))) {
                plr.sendMessage("You need a seed dipper to do this.")
                return@onObject
            }

            plr.animation(Animations.SUPERHEAT)
            plr.sendMessage("You plant the seed.")
            plr.inventory.remove(seed.seed)
            patch.plant(seed)
            Farming.sendAllotmentState(plr)
        }
    }
}

cmd("resetpatches", RIGHTS_ADMIN) {
    plr.herbPatches.values.forEach({ herbPatch ->
        herbPatch.reset(true)
    })
    plr.allotmentPatches.values.forEach({ patch ->
        patch.reset(true)
    })
    Farming.sendHerbState(plr)
    Farming.sendAllotmentState(plr)
}

cmd("progressplants", RIGHTS_ADMIN) {
    val rounds = asInt(0)
    plr.herbPatches.values.forEach({ herbPatch ->
        for (i in 0 until rounds) {
            UpdateFarmsTask.progressPlants(herbPatch)
        }
    })
    plr.allotmentPatches.values.forEach({ patch ->
        for (i in 0 until rounds) {
            UpdateFarmsTask.progressPlants(patch)
        }
    })
    Farming.sendHerbState(plr)
    Farming.sendAllotmentState(plr)
}

// Schedule task updating farms every minute
world.schedule(UpdateFarmsTask(world.players))

// Send farming state when logged in
on(LoginEvent::class) {
    Farming.sendHerbState(plr)
    Farming.sendAllotmentState(plr)
}

useItem(6032).onObject(7836) {
    plr.sendMessage("Composting")
    plr.sendVarp(Varp(511, 10))
}

useItem(6034).onObject(7836) {
    plr.sendVarp(Varp(512, 10))
    plr.sendMessage("Supercomposting")
}

npc1(2323) {
    plr.sendMessage("Talking")
}