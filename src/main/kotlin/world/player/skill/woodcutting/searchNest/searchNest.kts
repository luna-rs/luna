package world.player.skill.woodcutting.searchNest

import api.predef.*
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.mob.Player
import world.player.Messages

/**
 * Search a bird's nest.
 */
fun searchNest(plr: Player, nest: Nest) {
    if (plr.inventory.computeRemainingSize() >= 1) {
        plr.inventory.replace(nest.id, 5075)
        plr.inventory.add(nest.pickItem())
    } else {
        plr.sendMessage(Messages.INVENTORY_FULL)
    }
}

// Item click interaction for all birds nests.
Nest.NEST_MAP.forEach { (id, nest) ->
    item1(id) { searchNest(plr, nest) }
}