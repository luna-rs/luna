package game.obj.entrances

import api.predef.ext.animation
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.area.Area
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.`object`.GameObject

/**
 * Native raft and reusable-rope approach to the Waterfall Dungeon, and its free barrel return.
 * Lost City's waterfall script supplies the three approach destinations, rope ownership and return.
 * Quest progress is not manufactured; admission awaits Waterfall Quest when that quest is implemented.
 *
 * @author lare96
 */
object WaterfallApproach {
    const val RAFT = 1987
    const val ROCK = 1996
    const val TREE = 2020
    const val BARREL = 2022
    const val ROPE = 954
    const val AMULET = 295

    val RAFT_OBJECT = Position(2509, 3493)
    val RAFT_FROM = Position(2510, 3493)
    val ISLAND = Position(2512, 3481)
    val ROCK_OBJECT = Position(2512, 3468)
    val ROCK_FROM = Position(2512, 3476)
    val ROCK_LANDING = Position(2513, 3468)
    val TREE_OBJECT = Position(2512, 3465)
    val TREE_FROM = Position(2512, 3466)
    val LEDGE = Position(2511, 3463)
    val DUNGEON = Position(2575, 9861)
    val RIVERSIDE = Position(2527, 3413)
    val BARREL_OBJECT = Position(2512, 3463, 1)

    private val ropeBank = Area.of(2510, 3476, 2514, 3481)

    /**
     * Boards the existing raft. TODO Apply Waterfall Quest's started-state requirement when available.
     */
    fun board(player: Player, obj: GameObject) {
        if (!ready(player) || !matches(obj, RAFT, RAFT_OBJECT)) return
        Entrances.sequence(player,
            { player.sendMessage("You board the small raft.") }, null,
            { player.sendMessage("You push off downstream.") }, null,
            { if (living(player)) player.move(ISLAND) },
            { player.sendMessage("The raft crashes into a small land mound.") })
    }

    /**
     * Throws an owned rope from the native north-island bank and pulls across to the rock.
     * The rope is reusable. Presentation uses the source throw/pulling animations and normal exact movement.
     */
    fun ropeRock(player: Player, obj: GameObject) {
        if (!ready(player) || !matches(obj, ROCK, ROCK_OBJECT)) return
        if (player.position !in ropeBank || player.position.z != 0) {
            player.sendMessage("You can't get a good shot from here.")
            return
        }
        if (!player.inventory.contains(ROPE)) return
        Entrances.sequence(player,
            { player.animation(Animation(774)); player.sendMessage("You throw your rope towards the rock.") }, null,
            { if (living(player)) Entrances.slide(player, ROCK_LANDING, 0, 120, 776) }, null, null, null,
            { player.animation(Animation.CANCEL) })
    }

    /**
     * Lowers the player from the rock's dead tree to the ledge using their same reusable rope.
     */
    fun ropeTree(player: Player, obj: GameObject) {
        if (!ready(player) || !matches(obj, TREE, TREE_OBJECT) || !player.inventory.contains(ROPE)) return
        Entrances.sequence(player,
            { player.sendMessage("You tie the rope to the tree."); player.animation(Animation(774)) }, null,
            { if (living(player)) { player.sendMessage("You lower yourself onto the ledge."); player.move(LEDGE) } })
    }

    /**
     * Leaves the ledge without an amulet or rope through the source's barrel trip.
     */
    fun barrel(player: Player, obj: GameObject) {
        if (!ready(player) || !matches(obj, BARREL, BARREL_OBJECT)) return
        Entrances.sequence(player,
            { player.sendMessage("You climb into the barrel and start rocking.") }, null,
            { player.sendMessage("The barrel falls off the ledge.") }, null,
            { if (living(player)) player.move(RIVERSIDE) })
    }

    /**
     * Guards delayed moves after death or logout without changing native action ownership.
     */
    private fun living(player: Player) = player.state == EntityState.ACTIVE && player.isAlive

    private fun ready(player: Player) = living(player) && !player.isLocked

    private fun matches(obj: GameObject, id: Int, position: Position) = obj.id == id && obj.position == position
}
