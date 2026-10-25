package engine.combat.prayer

import api.predef.*
import api.predef.ext.*
import engine.combat.status.StatusEffectType
import game.player.Sound
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PrayerIcon
import io.luna.game.model.mob.SkillRestorationAction
import io.luna.game.model.mob.varp.Varp
import java.util.Collections
import java.util.EnumSet

/**
 * A model representing the currently active set of [CombatPrayer] types for a single [player].
 *
 * This class is responsible for:
 * - Toggling prayers on/off (including mutual exclusion via [CombatPrayer.group]).
 * - Driving client state (varps, head icons, sounds/messages) for [Player]s.
 * - Accumulating the combined drain of active prayers, retaining the remainder between timer executions.
 *
 * @author lare96
 */
class CombatPrayerSet(private val player: Player) {

    /**
     * The active prayers, changed only through activation and deactivation so their timer stays in sync.
     */
    private val activePrayers = EnumSet.noneOf(CombatPrayer::class.java)

    /**
     * An unmodifiable view of the currently active prayers.
     */
    val active: Set<CombatPrayer> = Collections.unmodifiableSet(activePrayers)

    /**
     * Accrued drain not yet converted into a whole prayer point. Ordinary toggles preserve this remainder.
     */
    private var drainCounter = 0

    /**
     * Activates [prayer] and ensures any mutually-exclusive prayers in the same [CombatPrayer.group] are deactivated.
     *
     * This also:
     * - Checks level requirements and remaining prayer points.
     * - Updates the client via varps, plays sounds, and sets the head icon (if applicable).
     * - Starts the drain action ([CombatPrayerDrainAction]) if not already running.
     * - Restarts the affected shared recovery clock for rapid restore/heal.
     *
     * If [prayer] is already active, this method toggles it off (non-silently).
     * Requirements are checked before replacing prayers in the same group, so rejected upgrades preserve the active
     * prayer. The dragon scimitar status restricts protection prayers only.
     */
    fun activate(prayer: CombatPrayer) {

        // The dragon scimitar blocks protection prayers only; other prayers remain available.
        if (StatusEffectType.DRAGON_SCIMITAR in player.status &&
            (prayer == CombatPrayer.PROTECT_FROM_MELEE || prayer == CombatPrayer.PROTECT_FROM_MISSILES ||
                    prayer == CombatPrayer.PROTECT_FROM_MAGIC)) {
            player.sendMessage("Your protection prayers have been disabled by a recent attack.")
            return
        }

        // Prayer is already active, deactivate non-silently.
        if (active.contains(prayer)) {
            deactivate(prayer, false)
            return
        }
        // Send varbits, head icons, sounds, relevant messages.
        if (player.prayer.staticLevel < prayer.level) {
            player.playSound(Sound.PRAYER_OFF) // todo@0.5.0 confirm if correct sound
            player.sendMessage("Your Prayer level is not high enough to use this.")
            return
        } else if (player.prayer.level == 0) {
            player.sendVarp(Varp(prayer.varp, 0))
            player.playSound(Sound.PRAYER_OFF) // todo@0.5.0 confirm if correct sound
            player.sendMessage("Use an altar to recharge your Prayer points.")
            return
        }
        if (prayer.group != null) {
            for (otherPrayer in prayer.group.getPrayers()) {
                deactivate(otherPrayer, true)
            }
        }
        player.sendVarp(Varp(prayer.varp, 1))
        player.playSound(prayer.sound)
        if (prayer.icon != PrayerIcon.NONE) {
            player.prayerIcon = prayer.icon
        }

        activePrayers.add(prayer)

        // Start the shared drain timer if needed.
        if (!player.actions.contains(CombatPrayerDrainAction::class)) {
            player.submitAction(CombatPrayerDrainAction(player))
        }

        restartRecovery(prayer)
    }

    /**
     * Restarts only the recovery clock controlled by [prayer]. Active rapid prayers retain a clock even at full
     * stats, so later damage or drains join the existing cadence instead of starting a duplicate timer.
     */
    private fun restartRecovery(prayer: CombatPrayer) {
        if (prayer != CombatPrayer.RAPID_HEAL && prayer != CombatPrayer.RAPID_RESTORE) {
            return
        }
        var restoration = player.actions.first(SkillRestorationAction::class.java)
        if (restoration == null && isActive(prayer)) {
            restoration = SkillRestorationAction(player)
            player.submitAction(restoration)
        }
        restoration?.restartRecovery(prayer)
    }

    /**
     * Activates [prayer] if it is not already active.
     *
     * This is a safe activation helper for scripts and combat logic that want to ensure a prayer is enabled without
     * accidentally toggling it off. If [prayer] is already  active, this method does nothing.
     *
     * @param prayer The prayer to activate.
     */
    fun activateIfOff(prayer: CombatPrayer) {
        if (!active.contains(prayer)) {
            activate(prayer)
        }
    }

    /**
     * Deactivates [prayer].
     *
     * This also clears the prayer varp and (if applicable) removes the head icon. If [silent] is `false`, the standard
     * deactivation sound is played.
     * The affected rapid-prayer recovery clock restarts at the normal interval immediately.
     *
     * @param prayer The prayer to deactivate.
     * @param silent If `true`, no deactivation sound is played (useful for auto-deactivations within a group).
     * @return `true` if the prayer was active and was removed, otherwise `false`.
     */
    fun deactivate(prayer: CombatPrayer, silent: Boolean): Boolean {
        val removed = activePrayers.remove(prayer)
        if (removed) {
            restartRecovery(prayer)
            player.sendVarp(Varp(prayer.varp, 0))
            if (!silent) {
                player.playSound(Sound.CANCEL_PRAYER)
            }
            if (prayer.icon != PrayerIcon.NONE) {
                player.prayerIcon = PrayerIcon.NONE
            }
            if (activePrayers.isEmpty()) {
                player.actions.first(CombatPrayerDrainAction::class.java)?.interrupt()
            }
        }
        return removed
    }

    /**
     * Deactivates all prayers for this set.
     *
     * This also clears all prayer varps, the drain remainder and timer, and any active head icon.
     * Recovery clocks controlled by active rapid prayers restart at their normal intervals.
     */
    fun deactivateAll() {
        CombatPrayer.VALUES.forEach { player.sendVarp(Varp(it.varp, 0)) }
        player.prayerIcon = PrayerIcon.NONE
        val rapidHeal = isActive(CombatPrayer.RAPID_HEAL)
        val rapidRestore = isActive(CombatPrayer.RAPID_RESTORE)
        activePrayers.clear()
        drainCounter = 0
        if (rapidHeal) restartRecovery(CombatPrayer.RAPID_HEAL)
        if (rapidRestore) restartRecovery(CombatPrayer.RAPID_RESTORE)
        player.actions.first(CombatPrayerDrainAction::class.java)?.interrupt()
    }

    /**
     * Returns `true` if [prayer] is currently active in this set.
     */
    fun isActive(prayer: CombatPrayer): Boolean = active.contains(prayer)

    /**
     * Accumulates five ticks of combined prayer drain and returns the whole points to deduct.
     *
     * Matches the shared timer in Lost City's 377 prayer script. The remainder survives ordinary toggles, and
     * resistance is read each time so equipment changes take effect on the next drain. Integer arithmetic avoids
     * rounding each prayer's interval down to a whole number of ticks.
     *
     * @return The number of prayer points consumed by this timer execution.
     */
    internal fun drain(): Int {
        if (activePrayers.isEmpty()) {
            return 0
        }
        drainCounter += activePrayers.sumOf { it.drain } * CombatPrayerDrainAction.DRAIN_INTERVAL
        val resistance = computeResistance()
        val points = drainCounter / resistance
        drainCounter %= resistance
        return points
    }

    /**
     * Computes the prayer drain resistance from the player's current equipment bonus.
     */
    private fun computeResistance() = (CombatPrayer.BASE_RESISTANCE +
            CombatPrayer.PRAYER_BONUS_BOOST * player.equipment.getBonus(EquipmentBonus.PRAYER))
        .coerceAtLeast(CombatPrayer.BASE_RESISTANCE)
}
