package world.player.item.groundItem

import api.predef.*
import io.luna.game.model.item.GroundItem
import io.luna.game.task.Task
import java.util.*

/**
 * A [Task] that will handle expiration for [GroundItem]s.
 *
 * @author lare96 <http://github.com/lare96>
 */
class GroundItemTask : Task(100) {

    companion object {

        /**
         * The amount of minutes it takes for a tradeable item to become global.
         */
        const val TRADEABLE_LOCAL_MINUTES = 1

        /**
         * The amount of minutes it takes for an untradeable item to expire.
         */
        const val UNTRADEABLE_LOCAL_MINUTES = 3

        /**
         * The amount of minutes it takes for a global item to expire.
         */
        const val GLOBAL_MINUTES = 3
    }

    /**
     * A queue of items awaiting registration.
     */
    private val registerQueue = ArrayDeque<GroundItem>()

    /**
     * A queue of items awaiting unregistration.
     */
    private val unregisterQueue = ArrayDeque<GroundItem>()

    override fun execute() {
        processItems()
        processUnregistrations()
        processRegistrations()
    }

    /**
     * Process expiration timers for all perishable items.
     */
    private fun processItems() {
        val it = world.items.iterator()
        for (item in it) {
            if (!item.isExpire) {
                continue
            }
            item.expireMinutes++

            val tradeable = item.def().isTradeable
            val mins = item.expireMinutes
            when {
                // Local tradeable item becomes a global item.
                mins == TRADEABLE_LOCAL_MINUTES &&
                        tradeable &&
                        item.isLocal -> {
                    unregisterQueue += item
                    registerQueue += GroundItem(item.context, item.id, item.amount, item.position,
                                                Optional.empty())
                }

                // Local untradeable item expires.
                mins == UNTRADEABLE_LOCAL_MINUTES &&
                        !tradeable &&
                        item.isLocal -> unregisterQueue += item

                // Global item expires.
                mins == GLOBAL_MINUTES &&
                        item.isGlobal -> unregisterQueue += item
            }
        }
    }

    /**
     * Handle any new unregistrations from expiration timer processing.
     */
    private fun processUnregistrations() {
        while (true) {
            val item = unregisterQueue.poll() ?: break
            world.items.unregister(item)
        }
    }

    /**
     * Handle any new registrations from expiration timer processing.
     */
    private fun processRegistrations() {
        while (true) {
            val item = registerQueue.poll() ?: break
            world.items.register(item)
        }
    }
}