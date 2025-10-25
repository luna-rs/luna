package game.obj.restorePrayer

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.def.GameObjectDefinition
import io.luna.game.model.mob.Player
import game.player.Animations
import game.player.Sounds

/**
 * Restores the player's prayer points.
 */
fun restore(plr: Player) {
    val prayer = plr.prayer
    if (prayer.level < prayer.staticLevel) {
        prayer.level = prayer.staticLevel
        plr.animation(Animations.PRAY)
        plr.playSound(Sounds.RECHARGE_PRAYER)
        plr.sendMessage("You recharge your prayer points.")
    } else {
        plr.sendMessage("You already have full prayer points.")
    }
}

/* Match all altar objects.  */
on(ServerLaunchEvent::class) {
    for(def in GameObjectDefinition.ALL) {
        if(def.id == 6552) {
            // Ancient altar.
            continue
        }
        if(def.actions.contains("Pray-at") && def.name.contentEquals("Altar")) {
            object1(def.id) { restore(plr) }
        }
    }
}
