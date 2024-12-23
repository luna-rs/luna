package world.player.skill.herblore.identifyHerb

import api.predef.*
import io.luna.game.model.mob.Player

/**
 * Identifies [herb] for [player].
 */
fun identify(plr: Player, herb: Herb) {
    val level = herb.level

    if (plr.herblore.level >= level) {

        plr.inventory.remove(herb.idItem)
        plr.inventory.add(herb.identifiedItem)

        plr.herblore.addExperience(herb.exp)

        val herbName = herb.identifiedItem.itemDef.name
        plr.sendMessage("You identify the $herbName.")
    } else {
        plr.sendMessage("You need a Herblore level of $level to identify this herb.")
    }
}

// Listen for an unidentified herb clicks.
Herb.UNID_TO_HERB.values.forEach {
    item1(it.id) { identify(plr, it) }
}