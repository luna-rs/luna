/*
 * Adapted from LostCityRS/Content quest_haunted in 377-wip, with the door interaction checked against 289.
 * See LICENSE.txt.
 */
package game.content.ernestTheChicken

import game.player.Sound
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.interact.InteractionType
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.mob.varp.Varp
import java.util.concurrent.CompletableFuture

/**
 * The six personal lever settings and nine doors in the manor basement.
 * Door availability is derived from the levers and sent through the cache's varp 668 morphs.
 *
 * @author lare96
 */
object ManorLeverPuzzle {

    private const val DOOR_VARP = 668
    private const val A = 1 shl 1
    private const val B = 1 shl 2
    private const val C = 1 shl 3
    private const val D = 1 shl 4
    private const val E = 1 shl 5
    private const val F = 1 shl 6

    val leverReach = InteractionPolicy(InteractionType.LINE_OF_SIGHT, 1)
    val doorReach = InteractionPolicy(InteractionType.DISTANCE, 1)
    val oilCan = Position(3092, 9755)

    /**
     * The open condition refers to the six lever bits stored in the client's varp 33.
     *
     * @author lare96
     */
    enum class Door(val id: Int, val tile: Position, val firstRoom: Int, val secondRoom: Int,
                    val firstSide: Direction, private val mask: Int, private val required: Int) {
        ONE_TWO(137, Position(3105, 9765), 1, 2, Direction.EAST, A or B or D or E or F, D or E or F),
        TWO_THREE(138, Position(3100, 9765), 2, 3, Direction.EAST, B or D or F, D or F),
        FOUR_FIVE(139, Position(3105, 9760), 4, 5, Direction.EAST, A or B or D, A or B or D),
        FIVE_SIX(140, Position(3100, 9760), 5, 6, Direction.EAST, D, D),
        EIGHT_NINE(141, Position(3100, 9755), 8, 9, Direction.EAST, E or F, F),
        TWO_FIVE(142, Position(3102, 9763), 2, 5, Direction.NORTH, A or B or C or D or E or F, C or D or F),
        THREE_SIX(143, Position(3097, 9763), 3, 6, Direction.NORTH, B or D or F, D),
        FOUR_SEVEN(144, Position(3108, 9758), 4, 7, Direction.NORTH, A or B or C or D or E or F, A or B),
        FIVE_EIGHT(145, Position(3102, 9758), 5, 8, Direction.NORTH, C or D, D);

        fun isOpen(levers: Int): Boolean =
            levers and mask == required || (this == FIVE_EIGHT && TWO_FIVE.isOpen(levers))

        fun stand(room: Int): Position =
            tile.translate(1, if (room == firstRoom) firstSide else firstSide.opposite())

        fun side(position: Position): Int? {
            val distance = if (firstSide == Direction.EAST) position.x - tile.x else position.y - tile.y
            return when {
                distance > 0 -> firstRoom
                distance < 0 -> secondRoom
                else -> null
            }
        }
    }

    /**
     * Lever identities, placements and the rooms from which they can be pulled.
     *
     * @author lare96
     */
    enum class Lever(val id: Int, val tile: Position, val room: Int, val bit: Int) {
        A(146, Position(3108, 9745), 7, ManorLeverPuzzle.A),
        B(147, Position(3118, 9752), 7, ManorLeverPuzzle.B),
        C(148, Position(3112, 9760), 4, ManorLeverPuzzle.C),
        D(149, Position(3108, 9767), 1, ManorLeverPuzzle.D),
        E(150, Position(3097, 9767), 3, ManorLeverPuzzle.E),
        F(151, Position(3096, 9765), 3, ManorLeverPuzzle.F)
    }

    fun levers(player: Player): Int = player.varpManager.getValue(PersistentVarp.ERNEST_LEVERS)

    fun reset(player: Player) {
        player.varpManager.setAndSendValue(PersistentVarp.ERNEST_LEVERS, 0)
        refreshDoors(player)
    }

    fun refreshDoors(player: Player) {
        val levers = levers(player)
        val doors = Door.entries.foldIndexed(0) { bit, packed, door ->
            if (door.isOpen(levers)) packed or (1 shl bit) else packed
        }
        player.sendVarp(Varp(DOOR_VARP, doors))
    }

    fun inspect(player: Player, lever: Lever) {
        val position = if (levers(player) and lever.bit == 0) "up" else "down"
        player.sendMessage("Lever ${lever.name} is $position.")
    }

    fun pull(player: Player, lever: Lever) {
        val settings = levers(player) xor lever.bit
        player.varpManager.setAndSendValue(PersistentVarp.ERNEST_LEVERS, settings)
        refreshDoors(player)
        player.animation(Animation(834))
        player.playSound(Sound.LEVER)
        val position = if (settings and lever.bit == 0) "up" else "down"
        player.sendMessage("You pull lever ${lever.name} $position and hear a clunk.")
    }

    fun cross(player: Player, door: Door) {
        if (!door.isOpen(levers(player))) {
            player.sendMessage("The door is locked.")
            player.playSound(Sound.LOCKED)
            return
        }
        val side = door.side(player.position) ?: return
        if (player.actions.first(Crossing::class.java) == null) player.submitAction(Crossing(player, door, side))
    }

    /**
     * Lines the player up on their side of an ajar door before stepping into the next room.
     *
     * @author lare96
     */
    private class Crossing(player: Player, private val door: Door, side: Int) : Action<Player>(player, ActionType.WEAK) {
        private val start = door.stand(side)
        private val destination = door.stand(if (side == door.firstRoom) door.secondRoom else door.firstRoom)
        private var walking: CompletableFuture<NavigationResult>? = null

        override fun run(): Boolean {
            if (executions > 8 || !door.isOpen(levers(mob))) return true
            if (mob.position == start) {
                mob.move(destination)
                return true
            }
            val move = walking
            if (move != null) return move.isDone
            walking = mob.navigator.navigate(start, false)
            return false
        }
    }
}
