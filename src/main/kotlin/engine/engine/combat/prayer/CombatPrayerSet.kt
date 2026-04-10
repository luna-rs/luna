package engine.combat.prayer

import api.predef.*
import api.predef.ext.*
import com.google.common.collect.HashMultiset
import engine.combat.prayer.PrayerRestorationAction.RapidHealAction
import engine.combat.prayer.PrayerRestorationAction.RapidRestoreAction
import game.player.Sound
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PrayerIcon
import io.luna.game.model.mob.varp.Varp

/**
 * A model representing the currently active set of [CombatPrayer] types for a single [player].
 *
 * This class is responsible for:
 * - Toggling prayers on/off (including mutual exclusion via [CombatPrayer.group]).
 * - Driving client state (varps, head icons, sounds/messages) for [Player]s.
 * - Tracking per-prayer drain timing using [active] so flicking-like behavior still drains consistently.
 *
 * @author lare96
 */
class CombatPrayerSet(private val player: Player) {

    /**
     * The multiset of active [CombatPrayer]s.
     *
     * The multiset *count* represents the number of ticks remaining until a prayer point should be deducted for that
     * prayer. We use counts instead of a simple boolean so that repeatedly toggling (e.g., flicking) does not reset the
     * drain schedule in a way that would avoid prayer drain.
     *
     * Convention:
     * - When a prayer is ready to deduct a point, its count will be `1`.
     * - When a prayer is not present in this set, it is considered inactive.
     */
    val active: HashMultiset<CombatPrayer> = HashMultiset.create()

    /**
     * Activates [prayer] and ensures any mutually-exclusive prayers in the same [CombatPrayer.group] are deactivated.
     *
     * This also:
     * - Checks level requirements and remaining prayer points.
     * - Updates the client via varps, plays sounds, and sets the head icon (if applicable).
     * - Starts the drain action ([CombatPrayerDrainAction]) if not already running.
     * - Starts restoration actions for prayers that have periodic effects (rapid restore/heal).
     *
     * If [prayer] is already active, this method toggles it off (non-silently).
     */
    fun activate(prayer: CombatPrayer) {

        // Prayer is already active, deactivate non-silently.
        if (active.contains(prayer)) {
            deactivate(prayer, false)
            return
        }
        if (prayer.group != null) {
            // Deactivate similar prayers if needed, silently.
            for (otherPrayer in prayer.group.getPrayers()) {
                deactivate(otherPrayer, true)
            }
        }

        // Send varbits, head icons, sounds, relevant messages.
        if (player.prayer.staticLevel < prayer.level) {
            player.playSound(Sound.PRAYER_OFF) // todo confirm if correct
            player.sendMessage("Your Prayer level is not high enough to use this.")
            return
        } else if (player.prayer.level == 0) {
            player.sendVarp(Varp(prayer.varp, 0))
            player.playSound(Sound.PRAYER_OFF) // todo confirm if correct
            player.sendMessage("Use an altar to recharge your Prayer points.")
            return
        }
        player.sendVarp(Varp(prayer.varp, 1))
        player.playSound(prayer.sound)
        if (prayer.icon != PrayerIcon.NONE) {
            player.prayerIcon = prayer.icon
        }

        // Start the prayer draining action if needed.
        if (!player.actions.contains(CombatPrayerDrainAction::class)) {
            player.submitAction(CombatPrayerDrainAction(player))
        }

        active.add(prayer, computeDrainTicks(prayer))

        // Start actions for restoring skills or hitpoints if needed.
        if (prayer == CombatPrayer.RAPID_RESTORE && !player.actions.contains(RapidRestoreAction::class)) {
            player.submitAction(RapidRestoreAction(player))
        } else if (prayer == CombatPrayer.RAPID_HEAL && !player.actions.contains(RapidHealAction::class)) {
            player.submitAction(RapidHealAction(player))
        }
    }

    /**
     * Deactivates [prayer].
     *
     * This also clears the prayer varp and (if applicable) removes the head icon. If [silent] is `false`, the standard
     * deactivation sound is played.
     *
     * @param prayer The prayer to deactivate.
     * @param silent If `true`, no deactivation sound is played (useful for auto-deactivations within a group).
     * @return `true` if the prayer was active and was removed, otherwise `false`.
     */
    fun deactivate(prayer: CombatPrayer, silent: Boolean): Boolean {
        val amount = active.setCount(prayer, 0)
        if (amount > 0) {
            player.sendVarp(Varp(prayer.varp, 0))
            if (!silent) {
                player.playSound(Sound.CANCEL_PRAYER)
            }
            if (prayer.icon != PrayerIcon.NONE) {
                player.prayerIcon = PrayerIcon.NONE
            }
        }
        return amount > 0
    }

    /**
     * Deactivates all prayers for this set.
     *
     * This also clears all prayer varps and removes any active head icon.
     */
    fun deactivateAll() {
        CombatPrayer.VALUES.forEach { player.sendVarp(Varp(it.varp, 0)) }
        player.prayerIcon = PrayerIcon.NONE
        active.clear()
    }

    /**
     * Returns `true` if [prayer] is currently active in this set.
     */
    fun isActive(prayer: CombatPrayer): Boolean = active.contains(prayer)

    /**
     * Decrements the drain counter for [prayer] by 1 tick.
     *
     * @return `true` if the prayer had a counter available to decrement (i.e., it was present and one unit was removed).
     */
    fun drain(prayer: CombatPrayer): Boolean {
        return active.remove(prayer, 1) == 1
    }

    /**
     * Computes how many ticks should elapse between prayer point deductions for [prayer], after applying prayer
     * resistance from equipment.
     *
     * @param prayer The prayer to compute drain ticks for.
     * @param resistance The effective resistance value to use. Defaults to [computeResistance].
     * @return The number of ticks per drain cycle.
     */
    fun computeDrainTicks(prayer: CombatPrayer, resistance: Int = computeResistance()) =
        (((0.6 * (resistance / prayer.drain)) * 1000) / 600).toInt()

    /**
     * Computes the player's prayer drain resistance used by [computeDrainTicks].
     *
     * Resistance is derived from a base value plus a boost from the player's equipment prayer bonus
     * ([EquipmentBonus.PRAYER]).
     *
     * @return The effective resistance value.
     */
    private fun computeResistance() = CombatPrayer.BASE_RESISTANCE +
            (CombatPrayer.PRAYER_BONUS_BOOST * player.equipment.getBonus(EquipmentBonus.PRAYER))
}