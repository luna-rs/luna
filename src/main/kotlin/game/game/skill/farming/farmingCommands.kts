package game.skill.farming

import api.predef.*

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
