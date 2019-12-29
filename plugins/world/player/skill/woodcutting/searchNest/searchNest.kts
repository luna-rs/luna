package world.player.skill.woodcutting.searchNest

import api.predef.*
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.mob.Player

fun searchNest(plr: Player, nest: Nest) {
    if (plr.inventory.computeRemainingSize() >= 1) {
        plr.inventory.replace(nest.nestId, 5075)
        plr.inventory.add(nest.pickItem())
    } else {
        plr.sendMessage("You do not have enough space in your inventory.")
    }
}

on(ItemFirstClickEvent::class) {
    val nest = Nest.VALUES[id]
    if (nest != null) {
        searchNest(plr, nest)
    }
}