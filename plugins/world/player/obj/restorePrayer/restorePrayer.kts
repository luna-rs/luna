package world.player.obj.restorePrayer

import api.predef.*
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player

/**
 * The restore prayer animation.
 */
val prayerAnimation = Animation(645)

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
        plr.animation(prayerAnimation)
        plr.sendMessage("You restore your prayer points.")
    } else {
        plr.sendMessage("You already have full prayer points.")
    }
}

/**
 * Match all altars with [restore].
 */
on(ObjectFirstClickEvent::class)
    .match(altars)
    .then { restore(plr) }

