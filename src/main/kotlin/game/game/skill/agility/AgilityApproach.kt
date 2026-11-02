package game.skill.agility

import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.movement.NavigationResult
import java.util.concurrent.CompletableFuture

/**
 * Walks a player who clicked an obstacle from beside its start tile onto that tile, then starts the obstacle.
 *
 * This is a weak action, so walking away, or doing anything else, cancels it. The navigator that walks the player
 * also runs as a weak action, which a strong one would interrupt.
 *
 * @param plr The player.
 * @param start The start tile to walk to.
 * @param onArrival Starts the obstacle once the player stands on [start].
 * @author TheLining
 */
class AgilityApproach(plr: Player, private val start: Position, private val onArrival: () -> Unit) :
    Action<Player>(plr, ActionType.WEAK) {

    companion object {

        /**
         * How far from the start tile, in tiles, a click on the obstacle still starts it.
         */
        const val RADIUS = 3

        /**
         * The ticks after which an approach that never arrived gives up.
         */
        private const val MAX_TICKS = 10

        /**
         * Finds the start tile of [type] nearest to [plr] on their floor, within [RADIUS] tiles, or `null`.
         */
        fun findStart(plr: Player, type: AgilityObstacle): Position? {
            val here = plr.position
            var best: Position? = null
            for (dx in -RADIUS..RADIUS) {
                for (dy in -RADIUS..RADIUS) {
                    val tile = here.translate(dx, dy)
                    if (!type.startIf(tile)) continue
                    if (best == null || here.computeLongestDistance(tile) < here.computeLongestDistance(best)) {
                        best = tile
                    }
                }
            }
            return best
        }
    }

    /**
     * The walk to [start], requested when this action is submitted.
     */
    private var walking: CompletableFuture<NavigationResult>? = null

    /**
     * The ticks that have passed.
     */
    private var ticks = 0

    override fun onSubmit() {
        // Requested here, not in run(), so the player takes the first step on the next tick.
        walking = mob.navigator.navigate(start, false)
    }

    override fun run(): Boolean {
        // Checked before the walk's result, which only completes a tick after the player arrives.
        if (mob.position == start) {
            onArrival()
            return true
        }
        val pending = walking ?: return true
        return pending.isDone || ++ticks > MAX_TICKS
    }
}
