package game.skill.fishing.fishingSpot

import api.predef.*
import io.luna.game.model.mob.Npc
import io.luna.game.task.Task

/**
 * An object that tracks fishing spots and handles their random movement.
 *
 * @author lare96
 */
object FishingSpotHandler : Task(false, 1) {

    /**
     * A model representing a fishing spot that moves around its [area].
     */
    private class MovingSpot(val npc: Npc, val area: FishingSpotArea, var countdown: Int = 1)
// todo this can probably be dynamically automated. Look for all possible water tiles within half viewing distance,
//  where a tile only has 1 non-water tile at one of its cardinal axes. Then that is a possible teleportation tile.
//  when doing the selection, fail/re-select if another fishing spot is already on that tile
    /**
     * How many ticks a fishing spot waits before moving again.
     */
    private val MOVE_INTERVAL = 280..529

    /**
     * How many random tiles are tried before a fishing spot gives up moving.
     */
    private const val MOVE_ATTEMPTS = 50

    /**
     * The tracked fishing spots.
     */
    private val movingSpots = ArrayList<MovingSpot>()

    /**
     * If the spawned fishing spots have been added to [movingSpots].
     */
    private var loaded = false

    override fun execute() {
        if (!loaded) {
            // Global NPC spawns are added before the first tick, so they can all be found here.
            for (npc in world.npcs) {
                val area = FishingSpotArea.NPC_TO_AREA[npc.id] ?: continue
                movingSpots += MovingSpot(npc, area)
            }
            loaded = true
        }
        for (spot in movingSpots) {
            if (--spot.countdown <= 0) {
                move(spot)
                spot.countdown = MOVE_INTERVAL.random()
            }
        }
    }

    /**
     * Moves [spot] to a random tile in its area that no other fishing spot is on. The spot stays where it is if no
     * such tile is found.
     */
    private fun move(spot: MovingSpot) {
        repeat(MOVE_ATTEMPTS) {
            val tile = spot.area.tiles.random()
            if (world.locator.findNpcsOnTile(tile) { it.def().name == "Fishing spot" }.isEmpty()) {
                spot.npc.move(tile)
                return
            }
        }
    }
}
