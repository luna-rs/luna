package world.player.wilderness.obelisk

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic
import io.luna.game.task.Task
import java.util.*

/**
 * A set of activated obeliks.
 */
val activatedSet = EnumSet.noneOf(Obelisk::class.java)

/**
 * Get nearby players, and determine which ones we will teleport.
 */
fun selectNearbyPlayers(obelisk: Obelisk, teleporting: ArrayList<Player>) {
    val players = world.chunks.getViewableEntities<Player>(obelisk.teleportTo, TYPE_PLAYER)
    for (nearby in players) {
        if (obelisk.teleportFrom.contains(nearby)) {
            nearby.walking.isLocked = true
            nearby.graphic(Graphic(342))
            nearby.animation(Animation(1816))
            teleporting.add(nearby)
        }
    }
}

/**
 * Pick random obelisk, and teleport players there. Reset the previous obelisk.
 */
fun teleportPlayers(obelisk: Obelisk, teleporting: ArrayList<Player>) {
    val nextObelisk = Obelisk.ALL.filter { nextObelisk -> nextObelisk != obelisk }.random()
    val iterator = teleporting.iterator()
    while (iterator.hasNext()) {
        val nearby = iterator.next()
        if (obelisk.teleportFrom.contains(nearby)) {
            nearby.walking.isLocked = true
            nearby.move(nextObelisk.teleportTo)
            nearby.sendMessage("You have been teleported by ancient magic.")
        } else {
            iterator.remove()
        }
    }
    activatedSet.remove(obelisk)
    for (position in obelisk.objectPositions) {
        world.addObject(obelisk.id, position)
    }
}

/**
 * Finish the teleport animations.
 */
fun finishPlayerTeleport(teleporting: ArrayList<Player>) {
    for (nearby in teleporting) {
        nearby.animation(Animation(715))
        nearby.walking.isLocked = false
    }
}

/**
 * Finish the teleport animations and cancel the task.
 */
fun cancelTeleportTask(task: Task, teleporting: ArrayList<Player>) {
    task.cancel()
    for (nearby in teleporting) {
        nearby.walking.isLocked = false
    }
}

/**
 * Starts the obelisk activation process.
 */
fun activate(plr: Player, obelisk: Obelisk) {
    if (!activatedSet.contains(obelisk)) {

        // Activate the obelisk.
        activatedSet.add(obelisk)
        plr.sendMessage("You activate the ancient obelisk...")
        obelisk.objectPositions.forEach { world.addObject(14825, it) }

        val teleporting = ArrayList<Player>()
        world.schedule(1) { task ->
            when (task.executionCounter) {
                7 -> selectNearbyPlayers(obelisk, teleporting)
                9 -> teleportPlayers(obelisk, teleporting)
                10 -> finishPlayerTeleport(teleporting)
                11 -> cancelTeleportTask(task, teleporting)
            }
        }
    }
}

// Dynamically prepare all obelisk listeners.
Obelisk.ALL.forEach {
    object1(it.id) { activate(plr, it) }
}
