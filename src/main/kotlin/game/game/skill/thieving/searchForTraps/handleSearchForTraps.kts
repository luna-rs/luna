package game.skill.thieving.searchForTraps

import api.predef.*
import game.content.randomEvents.RandomEvents
import io.luna.game.event.impl.ObjectClickEvent
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.mob.Player

/**
 * Submits the [OpenTrapAction].
 */
fun openTrap(plr: Player, event: ObjectClickEvent) {
    val thievable = ThievingChest.CHESTS[event.id]
    if (thievable != null) {
        plr.submitAction(OpenTrapAction(plr, event.gameObject, thievable))
    }
}

/**
 * Submits the [SearchForTrapsAction].
 */
fun searchForTraps(plr: Player, event: ObjectClickEvent) {
    val thievable = ThievingChest.CHESTS[event.id]
    // The source steel-arrowtip chest uses the separate locked-chest procedure without this hook.
    if (thievable != null && (thievable == ThievingChest.STEEL_ARROWTIPS ||
            !RandomEvents.offerActivity(plr, RandomEvents.Activity.THIEVING_CHEST))) {
        plr.submitAction(SearchForTrapsAction(plr, event.gameObject, thievable))
    }
}

/* Add object interactions for 'open' and 'search for traps.' */
for (def in GameObjectDefinition.ALL) {
    if (def != null && def.actions.contains("Search for traps")) {
        object1(def.id()) { openTrap(plr, this) }
        object2(def.id()) { searchForTraps(plr, this) }
    }
}
