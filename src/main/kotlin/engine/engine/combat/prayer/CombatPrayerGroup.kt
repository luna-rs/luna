package engine.combat.prayer

import com.google.common.collect.ImmutableSet

/**
 * An enum representing a group of [CombatPrayer] types. This is used to ensure no two prayers of the same type are
 * active at the same time.
 *
 * @author lare96
 */
enum class CombatPrayerGroup {
    ATTACK,
    STRENGTH,
    DEFENCE,
    OVERHEAD;

    /**
     * Returns an [ImmutableSet] representing the prayers within this group.
     */
    fun getPrayers(): ImmutableSet<CombatPrayer> = when (this) {
        ATTACK -> CombatPrayer.ATTACK_PRAYERS
        STRENGTH -> CombatPrayer.STRENGTH_PRAYERS
        DEFENCE -> CombatPrayer.DEFENCE_PRAYERS
        OVERHEAD -> CombatPrayer.OVERHEAD_PRAYERS
    }
}