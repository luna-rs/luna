package game.content.abyss

import api.predef.*
import game.content.sailing.Entrana
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Returns `true` if the law rift won't let [item] through to Entrana. It searches like the Monks of Entrana, but lets
 * hand armour through.
 */
fun isForbiddenOnEntrana(item: Item) = Entrana.isForbidden(item) && item.equipDef?.index != Equipment.HANDS

/**
 * Returns `true` if [plr] is carrying or wearing anything the law rift won't let through.
 */
fun isCarryingForbidden(plr: Player) =
    plr.inventory.any { it != null && isForbiddenOnEntrana(it) } ||
        plr.equipment.any { it != null && isForbiddenOnEntrana(it) }

for (rift in AbyssRift.entries) {
    object1(rift.id) {
        val destination = rift.destination
        // TODO The Lost City, Troll Stronghold and Mourning's End Part II: the cosmic, law and death rifts only let
        //  through players who have finished them. The others get "A strange power blocks your exit."
        if (destination == null) {
            plr.sendMessage("A strange power blocks your exit.")
        } else if (rift == AbyssRift.LAW && isCarryingForbidden(plr)) {
            plr.sendMessage("The power of Saradomin prevents you taking weaponry and armour to Entrana.")
        } else {
            plr.move(destination)
        }
    }
}
