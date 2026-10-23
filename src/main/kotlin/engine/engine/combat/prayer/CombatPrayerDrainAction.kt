package engine.combat.prayer

import api.predef.*
import game.player.Sound
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.mob.Player

/**
 * An [Action] that handles prayer-point drain while any [CombatPrayer] is active.
 *
 * Uses the five-tick normal timer from Lost City's 377 prayer script, converting combined drain into whole points
 * while [CombatPrayerSet] retains the remainder. Turning off the last prayer cancels this timer immediately.
 *
 * If the player reaches 0 prayer points, it plays the appropriate sound/message and deactivates all prayers.
 *
 * @author lare96
 */
class CombatPrayerDrainAction(private val plr: Player) : Action<Player>(plr, ActionType.NORMAL, true, 1) {

    /**
     * Prayer timer constants.
     *
     * @author lare96
     */
    companion object {

        /**
         * The interval between prayer drain executions, in game ticks.
         */
        const val DRAIN_INTERVAL = 5
    }

    /**
     * The next due world tick. A normal timer becomes due while an interface is open, but cannot execute until
     * it closes. Rescheduling from the execution tick prevents charging for missed timer executions.
     */
    private var nextDrain = world.currentTick + DRAIN_INTERVAL

    override fun run(): Boolean {
        val prayerSet = plr.combat.prayers
        if (prayerSet.active.isEmpty()) {
            return true
        }
        if (world.currentTick < nextDrain) {
            return false
        }
        nextDrain = world.currentTick + DRAIN_INTERVAL
        val points = prayerSet.drain()
        if (points > 0) {
            plr.prayer.level = (plr.prayer.level - points).coerceAtLeast(0)
        }
        if (plr.prayer.level == 0) {
            plr.playSound(Sound.PRAYER_DRAIN)
            plr.sendMessage("You've run out of Prayer points.")
            prayerSet.deactivateAll()
            return true
        }
        return false
    }
}
