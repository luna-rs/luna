import api.*
import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player

/**
 * The restore prayer animation.
 */
val prayerAnimation = Animation(645)

/**
 * Restores the player's prayer points.
 */
fun restore(plr: Player) {
    val prayer = plr.skill(SKILL_PRAYER)
    if (prayer.level < prayer.staticLevel) {
        prayer.level = prayer.staticLevel
        plr.animation(prayerAnimation)
        plr.sendMessage("You restore your Prayer points.")
    } else {
        plr.sendMessage("You already have full Prayer points.")
    }
}

/**
 * Restore prayer points if clicking on correct object.
 */
on(ObjectFirstClickEvent::class)
    .args(409, 3243)
    .run {
        restore(it.plr)
        it.terminate()
    }
