package world.player.skill.herblore.identifyHerb

import api.predef.*
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
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

/**
 * Forwards to [identify] if [msg] contains a valid unidentified herb.
 */
fun tryIdentify(msg: ItemFirstClickEvent) {
    val herb = Herb.UNID_TO_HERB[msg.id]
    if (herb != null) {
        identify(msg.plr, herb)
    }
}

/**
 * Listen for an unidentified herb clicks.
 */
on(ItemFirstClickEvent::class)
    .filter { itemDef(id).hasInventoryAction(0, "Identify") }
    .then { tryIdentify(this) }
