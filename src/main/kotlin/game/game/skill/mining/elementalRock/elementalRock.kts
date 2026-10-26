package game.skill.mining.elementalRock

import api.predef.*
import api.predef.ext.*
import game.skill.mining.Pickaxe
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import java.util.WeakHashMap

/**
 * The elemental rock in the Elemental Workshop.
 */
val ELEMENTAL_ROCK = 3403

/**
 * The earth elemental that an elemental rock turns into. It drops the elemental ore when killed.
 */
val ROCK_ELEMENTAL = 1023

/**
 * The ticks until an earth elemental from a rock disappears, if nobody kills it.
 */
val ROCK_ELEMENTAL_TICKS = 500

/**
 * Player -> the earth elemental they last woke from a rock.
 */
val wokenElementals = WeakHashMap<Player, Npc>()

/**
 * Wakes the earth elemental in an elemental rock, which attacks [plr]. Mining the rock gives no ore or experience;
 * the elemental drops the ore.
 */
fun wakeElemental(plr: Player) {
    val elemental = Npc(ctx, ROCK_ELEMENTAL, plr.position)
    elemental.respawnTicks = -1
    world.npcs.add(elemental)
    wokenElementals[plr] = elemental
    elemental.speak("Grr... Ge'roff my rock!")
    elemental.combat.attack(plr)
    world.scheduleOnce(ROCK_ELEMENTAL_TICKS) {
        if (elemental.state == EntityState.ACTIVE) {
            world.npcs.remove(elemental)
        }
    }
}

/**
 * Determines if the earth elemental [plr] last woke is still alive and close by.
 */
fun hasWokenElemental(plr: Player): Boolean {
    val elemental = wokenElementals[plr] ?: return false
    return elemental.state == EntityState.ACTIVE && elemental.isAlive &&
            elemental.position.isWithinDistance(plr.position, 5)
}

object1(ELEMENTAL_ROCK) {
    when {
        plr.mining.level < 20 -> plr.sendMessage("You need a Mining level of at least 20 to mine elemental ore.")
        Pickaxe.computePickType(plr) == null ->
            plr.newDialogue().text("You do not have a pickaxe which you have the Mining level to use.").open()
        hasWokenElemental(plr) -> {}
        else -> wakeElemental(plr)
    }
}
