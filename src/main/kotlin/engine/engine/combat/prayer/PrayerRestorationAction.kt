package engine.combat.prayer

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill

/**
 * Base [Action] for prayers that periodically restore stats (e.g., rapid heal/restore).
 *
 * This action runs every game tick but only performs its restoration effect every 100 executions (see [run]). It
 * automatically stops when the associated [prayer] is no longer active.
 *
 * @param player The mob whose stats may be restored.
 * @param prayer The prayer that must remain active for this action to keep running.
 */
abstract class PrayerRestorationAction(private val player: Player, private val prayer: CombatPrayer) :
    Action<Player>(player, ActionType.SOFT, false, 1) {

    /**
     * Periodic hitpoints restoration for [CombatPrayer.RAPID_HEAL].
     *
     * Every time [execute] triggers, restores 1 hitpoint level up to the mob's static level.
     */
    class RapidHealAction(player: Player) : PrayerRestorationAction(player, CombatPrayer.RAPID_HEAL) {

        override fun execute() {
            val hp = mob.hitpoints
            if (hp.level < hp.staticLevel) {
                hp.adjustLevel(1)
            }
        }
    }

    /**
     * Periodic skill restoration for [CombatPrayer.RAPID_RESTORE].
     *
     * Every time [execute] triggers, restores non-prayer, non-hitpoints skills by 1 level up to their static level.
     */
    class RapidRestoreAction(player: Player) : PrayerRestorationAction(player, CombatPrayer.RAPID_RESTORE) {

        override fun execute() {
            for (skill in mob.skills) {
                /*
                 * Prayer and hitpoints are excluded:
                 * - Prayer has its own drain/restore mechanics elsewhere.
                 * - Hitpoints restoration is handled by RapidHealAction.
                 */
                if (skill.id == Skill.PRAYER || skill.id == Skill.HITPOINTS) {
                    continue
                }
                val level = skill.level
                val staticLevel = skill.staticLevel
                if (level < staticLevel) {
                    skill.adjustLevel(1)
                }
            }
        }
    }

    override fun run(): Boolean {
        if (executions > 0 && executions % 100 == 0) {
            // Run restoration effect every 100 ticks.
            execute()
        }
        // Stop if prayer off.
        return !mob.combat.prayers.isActive(prayer)
    }

    /**
     * Performs the specific restoration effect for this prayer action.
     */
    abstract fun execute()
}