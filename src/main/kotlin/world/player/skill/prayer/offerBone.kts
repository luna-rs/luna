package world.player.skill.prayer

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import world.player.skill.firemaking.Firemaking.TINDERBOX_ID
import world.player.skill.firemaking.LightAction
import java.time.Duration

/**
 * All altars that can be used with bones.
 */
val ALTARS = setOf(4090, 4091)

/**
 * Allows the player to light the altar for bones to give more XP. Will extinguish itself after 30 minutes.
 */
fun lightAltar(plr: Player, altarObject: GameObject) {
    plr.submitAction(object : LightAction(plr, rand(6, 12)) {
        override fun onLight() {
            if (world.removeObject(altarObject)) {
                world.addObject(GameObject.createDynamic(ctx, 4090, altarObject.position, altarObject
                    .objectType, altarObject.direction, altarObject.view))
                plr.sendMessage("You light the altar.")

                world.scheduleOnce(Duration.ofMinutes(30)) {
                    world.addObject(GameObject.createDynamic(ctx, 4091, altarObject.position, altarObject.objectType,
                                                             altarObject.direction, altarObject.view))
                }
            }
        }
    })
}

// Add all bone on altar object interactions.
for (bone in Bone.ALL) {
    for (altar in ALTARS) {
        useItem(bone.id).onObject(altar) {
            plr.submitAction(OfferBoneAction(plr, gameObject, bone))
        }
    }
}

// Use the tinderbox with the altar, or first click the altar to light it.
useItem(TINDERBOX_ID).onObject(4091) {
    lightAltar(plr, gameObject)
}
object1(4091) {
    lightAltar(plr, gameObject)
}

// Spawn the altar object at home.
on(ServerLaunchEvent::class) {
    world.addObject(id = 4091, x = 3091, y = 3247, direction = ObjectDirection.NORTH)
}