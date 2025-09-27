package world.obj.restorePrayer

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import world.player.Animations

/**
 * The altars used to restore prayer.
 */
val altars: Set<Int> = hashSetOf(409, 3243)

/**
 * Restores the player's prayer points.
 */
fun restore(plr: Player) {
    val prayer = plr.prayer
    if (prayer.level < prayer.staticLevel) {
        prayer.level = prayer.staticLevel
        plr.animation(Animations.PRAY)
        plr.sendMessage("You recharge your prayer points.")
    } else {
        plr.sendMessage("You already have full prayer points.")
    }
}

/* Match all altar objects.  */
for (id in altars) {
    object1(id) { restore(plr) }
}
