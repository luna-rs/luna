package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.webwalk.plan.BankAccess
import api.predef.*
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Native entrances and pit exits shared by agility training and subzone travel.
 *
 * @author lare96
 */
object AgilityCourseTravel {
    /**
     * Uses the course entrance pipe in either direction.
     */
    suspend fun barbarianPipe(bot: Bot, entering: Boolean): Boolean = traverse(bot,
        Position(2552, if (entering) 3561 else 3558), setOf(2287)) {
        bot.position == Position(2552, if (entering) 3558 else 3561)
    }

    /**
     * Crosses the ridge through its native handler; a fall is reported as failed travel.
     */
    suspend fun wildernessRidge(bot: Bot, entering: Boolean): Boolean = traverse(bot,
        Position(2998, if (entering) 3916 else 3932),
        if (entering) setOf(2309) else setOf(2307, 2308)) {
        bot.position == Position(2998, if (entering) 3933 else 3916)
    }

    /**
     * Resolves the actual ladder in a failure pit and waits for the surface transition.
     */
    suspend fun climbOut(bot: Bot, tile: Position): Boolean = withTimeoutOrNull(30.seconds) {
        val ladder = world.locator.findObjectsOnTile(tile) {
            it.state == EntityState.ACTIVE && it.id == 1755
        }.firstOrNull() ?: return@withTimeoutOrNull false
        bot.actionHandler.interactions.interact(1, ladder) &&
            waitFor(20.seconds) { bot.position.y < 6400 && settled(bot) }
    } == true

    /**
     * Approaches an entrance on the required side and confirms its native landing.
     */
    private suspend fun traverse(bot: Bot, entry: Position, ids: Set<Int>, arrived: () -> Boolean): Boolean =
        withTimeoutOrNull(45.seconds) {
            if (!bot.actionHandler.webWalkTo(entry, bank = BankAccess.NONE) || bot.position != entry) {
                return@withTimeoutOrNull false
            }
            val obj = world.locator.findObjects(entry, 5) {
                it.state == EntityState.ACTIVE && it.id in ids && it.position.z == entry.z
            }.minByOrNull { it.position.computeLongestDistance(entry) } ?: return@withTimeoutOrNull false
            bot.actionHandler.interactions.interact(1, obj) && waitFor(25.seconds) { arrived() && settled(bot) }
        } == true

    /**
     * Prevents another interaction while a native traversal still owns the bot.
     */
    private fun settled(bot: Bot) = !bot.isLocked && bot.actions.size(ActionType.STRONG) == 0 && bot.walking.isEmpty
}
