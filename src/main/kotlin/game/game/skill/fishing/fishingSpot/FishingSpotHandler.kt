package game.skill.fishing.fishingSpot

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.task.Task

/**
 * An object that tracks fishing spots and handles their random movement.
 *
 * @author lare96
 */
object FishingSpotHandler : Task(false, 100) {

    /**
     * A model representing a randomly moving fishing spot.
     */
    class FishingSpot(val id: Int,
                      val home: Position,
                      val away: List<Position>,
                      var countdown: Int = 1)

    /**
     * A model representing a receiver for when registering fishing spots using [add].
     */
    class FishingSpotReceiver(var id: Int? = null,
                              var home: Position? = null,
                              val away: ArrayList<Position> = ArrayList())

    /**
     * A range of how often fishing spots can move.
     */
    private val MOVE_INTERVAL_MINUTES = 3..7

    /**
     * A map of tracked fishing spots.
     */
    private val fishingSpots = HashMap<Npc, FishingSpot>()

    override fun execute() {
        fishingSpots.forEach { (spotNpc, spot) ->
            if (--spot.countdown <= 0) {
                when (spotNpc.position) {
                    // Move NPC between home and away positions.
                    spot.home -> spotNpc.move(spot.away.random())
                    else -> spotNpc.move(spot.home)
                }
                spot.countdown = MOVE_INTERVAL_MINUTES.random()
            }
        }
    }

    /**
     * Adds a potential fishing spot to be tracked by this handler, making it eligible for random
     * periodic movement. [spawn] refers to if the fishing spot should be spawned using this method, or if
     * you wish to track an existing spot. Usage is as follows
     *
     * ```
     * add {
     *     id = 316
     *     home = Position(3222, 3222) // Must match spawn coordinates.
     *
     *     away += Position(3223, 3222)
     *     away += Position(3221, 3222)
     *     ...
     * }
     *
     * // Spawn is 'false', we need to explicitly add the NPC.
     * world.addNpc(id = 316, x = 3222, y = 3222)
     * ```
     */
    internal fun add(spawn: Boolean = false, action: FishingSpotReceiver.() -> Unit) {
        val receiver = FishingSpotReceiver()
        action(receiver)

        val id = receiver.id
        val home = receiver.home
        val away = receiver.away
        when {
            id == null -> throw IllegalStateException("ID must be set.")
            home == null -> throw IllegalStateException("Home position must be set.")
            away.isEmpty() -> throw IllegalStateException("At least one away position must be added.")
        }
        val npc = computeSpotNpc(id!!, home!!, spawn)
        when {
            npc == null -> throw IllegalStateException("No fishing spot matching $id found on $home.")
            fishingSpots.containsKey(npc) ->
                throw IllegalStateException("This fishing spot has already been registered.")
        }
        fishingSpots[npc!!] = FishingSpot(id, home, away)
    }

    /**
     * Computes the NPC that will be registered with this handler. Either an existing fishing spot, or create a new
     * one.
     */
    private fun computeSpotNpc(id: Int, home: Position, spawn: Boolean): Npc? {
        if (spawn) {
            val npc = Npc(ctx, id, home)
            // 'add' might be called from script loading thread. So sync it with game thread just in case.
            gameThread.sync { world.addNpc(npc) }
            return npc
        } else {
            return world.npcs.find { it.position == home && it.id == id && it.definition.name == "Fishing spot" }
        }
    }
}