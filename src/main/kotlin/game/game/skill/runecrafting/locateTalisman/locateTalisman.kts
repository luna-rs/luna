package game.skill.runecrafting.locateTalisman

import api.predef.*
import game.skill.runecrafting.enterAltar.Altar
import io.luna.game.model.Position
import io.luna.game.model.mob.Player

/**
 * The tiles each talisman's "Locate" option points to: the ruins of its altar.
 */
val RUINS = mapOf(
    Altar.AIR to Position(2985, 3292),
    Altar.MIND to Position(2982, 3514),
    Altar.WATER to Position(3185, 3165),
    Altar.EARTH to Position(3306, 3474),
    Altar.FIRE to Position(3313, 3255),
    Altar.BODY to Position(3053, 3445),
    Altar.COSMIC to Position(2408, 4377),
    Altar.CHAOS to Position(3060, 3591),
    Altar.NATURE to Position(2869, 3019),
    Altar.LAW to Position(2858, 3381)
)

/**
 * Outside Zanaris, the cosmic talisman points to the Lumbridge Swamp shed that leads there.
 */
val SHED_CENTRE = Position(3203, 3169)

/**
 * Whether [plr] is inside the Lumbridge Swamp shed.
 */
fun inShed(plr: Player) = plr.x in 3202..3205 && plr.y in 3167..3170

/**
 * Whether [plr] is in Zanaris.
 */
fun inZanaris(plr: Player) = plr.x in 2368..2495 && plr.y in 4352..4479

/**
 * Whether [plr] stands in the same map square as [altar]'s temple.
 */
fun inTemple(plr: Player, altar: Altar) =
    plr.x shr 6 == altar.enter.x shr 6 && plr.y shr 6 == altar.enter.y shr 6

/**
 * Tells [plr] which way [target] lies.
 */
fun pull(plr: Player, target: Position) {
    val dx = target.x - plr.x
    val dy = target.y - plr.y
    val direction = when {
        dx < 0 && dy > 0 -> "north-west"
        dx < 0 && dy < 0 -> "south-west"
        dx > 0 && dy > 0 -> "north-east"
        dx > 0 && dy < 0 -> "south-east"
        dx > 0 -> "east"
        dx < 0 -> "west"
        dy < 0 -> "south"
        dy > 0 -> "north"
        else -> return
    }
    plr.sendMessage("The talisman pulls towards the $direction.")
}

/**
 * Handles the "Locate" option of a talisman.
 */
fun locate(plr: Player, altar: Altar) {
    if (altar == Altar.DEATH) {
        plr.sendMessage("The talisman doesn't seem to work. Maybe the ruins are deep underground.")
        return
    }
    if (inTemple(plr, altar)) {
        plr.sendMessage("You are standing in the ${altar.name.lowercase().replaceFirstChar { it.uppercase() }} temple.")
        return
    }
    if (altar == Altar.COSMIC) {
        when {
            inShed(plr) -> {
                plr.sendMessage("You can feel the power flowing through the talisman.")
                plr.sendMessage("There must be something special about this place.")
            }
            inZanaris(plr) -> pull(plr, RUINS.getValue(altar))
            plr.y > 4800 -> plr.sendMessage("The talisman doesn't seem to work here...")
            else -> pull(plr, SHED_CENTRE)
        }
        return
    }
    if (plr.y > 4800) {
        plr.sendMessage("The talisman doesn't seem to work here...")
        return
    }
    pull(plr, RUINS.getValue(altar))
}

for (altar in Altar.ALL) {
    item4(altar.talisman) { locate(plr, altar) }
}
