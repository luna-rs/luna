package engine.combat.prayer

import api.predef.*
import game.player.Sounds
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.mob.Player
import java.util.*

/**
 * An [Action] that handles prayer-point drain while any [CombatPrayer] is active.
 *
 * This action runs once per game tick and:
 * - Checks each possible prayer in [CombatPrayer.VALUES].
 * - Uses the per-prayer counters stored in [CombatPrayerSet.active] to determine when a drain should occur.
 * - Deducts prayer points and re-schedules the next drain cycle for prayers that remain active.
 *
 * If the player reaches 0 prayer points, it plays the appropriate sound/message and deactivates all prayers.
 *
 * @author lare96
 */
class CombatPrayerDrainAction(private val plr: Player) : Action<Player>(plr, ActionType.SOFT, false, 1) {

    override fun run(): Boolean {
        val prayerSet = plr.combat.prayers
        if (prayerSet.active.isEmpty()) {
            // No more prayers to process.
            return true
        } else {
            for (prayer in CombatPrayer.VALUES) {
                // This prayer is ready to deduct a prayer point.
                if (prayerSet.drain(prayer)) {
                    if (--plr.prayer.level < 1) {
                        // We've run out of prayer points.
                        plr.prayer.level = 0
                        plr.playSound(Sounds.RAN_OUT_OF_PRAYER)
                        plr.sendMessage("You've run out of prayer points.")
                        prayerSet.deactivateAll()
                        return true
                    } else {
                        prayerSet.active.setCount(prayer, prayerSet.computeDrainTicks(prayer))
                    }
                }
            }
        }
        return false
    }
}