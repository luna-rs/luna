package world.areas.wilderness.obelisk

import api.predef.*
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Graphic
import io.luna.game.model.mob.Player
import java.util.*
import kotlin.collections.ArrayList

/**
 * A set of activated obeliks.
 */
val activatedSet = EnumSet.noneOf(Obelisk::class.java)

/**
 * Starts the obelisk activation process.
 */
fun activate(plr: Player, obelisk: Obelisk) {
    if (!activatedSet.contains(obelisk)) {

        // Activate the obelisk.
        activatedSet.add(obelisk)
        plr.sendMessage("You activate the ancient obelisk...")
        obelisk.objectPositions.forEach { world.addObject(14825, it) }

        // Start teleporting players.
        val teleporting = ArrayList<Player>()
        world.scheduleOnce(7) {
            val players = world.chunks.getViewablePlayers(obelisk.teleportTo)
            players.forEach {
                if (obelisk.teleportFrom.contains(it)) {
                    // TODO Lock players here
                    it.graphic(Graphic(342))
                    it.animation(Animation(1816))
                    teleporting.add(it)
                }
            }
        }

        // Pick next obelisk.
        val nextObelisk = Obelisk.ALL.filter { it != obelisk }.toList().random()
        world.scheduleOnce(10) {
            teleporting.forEach { other ->

                // Finish teleporting players.
                if (obelisk.teleportFrom.contains(other)) {
                    // TODO Unlock players here
                    other.teleport(nextObelisk.teleportTo)
                    other.sendMessage("You have been teleported by ancient magic!")
                }
            }

            // Reset previous obelisk.
            activatedSet.remove(obelisk)
            obelisk.objectPositions.forEach { world.addObject(obelisk.id, it) }
        }

        // Reset player animations.
        world.scheduleOnce(11) {
            teleporting.forEach { it.animation(Animation(715)) }
        }
    }
}

// Dynamically prepare all obelisk listeners.
Obelisk.ALL.forEach {
    object1(it.id) { activate(plr, it) }
}
