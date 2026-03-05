package engine.combat.prayer;

import com.google.common.collect.ImmutableSet
import engine.combat.prayer.CombatPrayer.Companion.BASE_RESISTANCE
import game.player.Sounds
import io.luna.game.model.mob.PrayerIcon

/**
 * An enum representing combat prayers.
 *
 * @author lare96
 */
enum class CombatPrayer(val level: Int,
                        val button: Int,
                        val drain: Int,
                        val icon: PrayerIcon = PrayerIcon.NONE,
                        val varp: Int,
                        val sound: Sounds = Sounds.DEACTIVATE_PRAYER,
                        val group: CombatPrayerGroup? = null) {
    THICK_SKIN(level = 1,
               button = 5609,
               drain = 1,
               varp = 83,
               sound = Sounds.THICK_SKIN,
               group = CombatPrayerGroup.DEFENCE),
    BURST_OF_STRENGTH(level = 4,
                      button = 5610,
                      drain = 1,
                      varp = 84,
                      sound = Sounds.BURST_OF_STRENGTH,
                      group = CombatPrayerGroup.STRENGTH),
    CLARITY_OF_THOUGHT(level = 7,
                       button = 5611,
                       drain = 1,
                       varp = 85,
                       sound = Sounds.CLARITY_OF_THOUGHT,
                       group = CombatPrayerGroup.ATTACK),
    ROCK_SKIN(level = 10,
              button = 5612,
              drain = 6,
              varp = 86,
              sound = Sounds.ROCK_SKIN,
              group = CombatPrayerGroup.DEFENCE),
    SUPERHUMAN_STRENGTH(level = 13,
                        button = 5613,
                        drain = 6,
                        varp = 87,
                        sound = Sounds.SUPERHUMAN_STRENGTH,
                        group = CombatPrayerGroup.STRENGTH),
    IMPROVED_REFLEXES(level = 16,
                      button = 5614,
                      drain = 6,
                      varp = 88,
                      sound = Sounds.IMPROVED_REFLEXES,
                      group = CombatPrayerGroup.ATTACK),
    RAPID_RESTORE(level = 19,
                  button = 5615,
                  drain = 1,
                  sound = Sounds.RAPID_RESTORE,
                  varp = 89),
    RAPID_HEAL(level = 22,
               button = 5616,
               drain = 2,
               sound = Sounds.RAPID_HEAL,
               varp = 90),
    PROTECT_ITEM(level = 25,
                 button = 5617,
                 drain = 2,
                 sound = Sounds.PROTECT_ITEM,
                 varp = 91),
    STEEL_SKIN(level = 28,
               button = 5618,
               drain = 12,
               varp = 92,
               sound = Sounds.STEEL_SKIN,
               group = CombatPrayerGroup.DEFENCE),
    ULTIMATE_STRENGTH(level = 31,
                      button = 5619,
                      drain = 12,
                      varp = 93,
                      sound = Sounds.ULTIMATE_STRENGTH,
                      group = CombatPrayerGroup.STRENGTH),
    INCREDIBLE_REFLEXES(level = 34,
                        button = 5620,
                        drain = 12,
                        varp = 94,
                        sound = Sounds.INCREDIBLE_REFLEXES,
                        group = CombatPrayerGroup.ATTACK),
    PROTECT_FROM_MAGIC(level = 37,
                       button = 5621,
                       drain = 12,
                       icon = PrayerIcon.PROTECT_FROM_MAGIC,
                       varp = 95,
                       sound = Sounds.PROTECT_FROM_MAGIC,
                       group = CombatPrayerGroup.OVERHEAD),
    PROTECT_FROM_MISSILES(level = 40,
                          button = 5622,
                          drain = 12,
                          icon = PrayerIcon.PROTECT_FROM_MISSILES,
                          varp = 96,
                          sound = Sounds.PROTECT_FROM_RANGE,
                          group = CombatPrayerGroup.OVERHEAD),
    PROTECT_FROM_MELEE(level = 43,
                       button = 5623,
                       drain = 12,
                       icon = PrayerIcon.PROTECT_FROM_MELEE,
                       varp = 97,
                       sound = Sounds.PROTECT_FROM_MELEE,
                       group = CombatPrayerGroup.OVERHEAD),
    RETRIBUTION(level = 46,
                button = 683,
                drain = 3,
                icon = PrayerIcon.RETRIBUTION,
                varp = 98,
                sound = Sounds.RETRIBUTION,
                group = CombatPrayerGroup.OVERHEAD),
    REDEMPTION(level = 49,
               button = 684,
               drain = 6,
               icon = PrayerIcon.REDEMPTION,
               varp = 99,
               sound = Sounds.REDEMPTION,
               group = CombatPrayerGroup.OVERHEAD),
    SMITE(level = 52,
          button = 685,
          drain = 18,
          icon = PrayerIcon.SMITE,
          varp = 100,
          sound = Sounds.SMITE,
          group = CombatPrayerGroup.OVERHEAD);

    companion object {

        /**
         * The cached values.
         */
        val VALUES = ImmutableSet.copyOf(values())

        /**
         * The overhead prayers group.
         */
        val OVERHEAD_PRAYERS = ImmutableSet.of(PROTECT_FROM_MELEE, PROTECT_FROM_MAGIC, PROTECT_FROM_MISSILES, SMITE,
                                               RETRIBUTION, REDEMPTION)

        /**
         * The strength prayers group.
         */
        val STRENGTH_PRAYERS = ImmutableSet.of(BURST_OF_STRENGTH, SUPERHUMAN_STRENGTH, ULTIMATE_STRENGTH)

        /**
         * The defence prayers group.
         */
        val DEFENCE_PRAYERS = ImmutableSet.of(THICK_SKIN, ROCK_SKIN, STEEL_SKIN)

        /**
         * The attack prayers group.
         */
        val ATTACK_PRAYERS = ImmutableSet.of(CLARITY_OF_THOUGHT, IMPROVED_REFLEXES, INCREDIBLE_REFLEXES)

        /**
         * The prayer bonus boost. Multiplied by the prayer bonus you actually have and added to [BASE_RESISTANCE]. OSRS
         * timing requires this to be `2`.
         *
         * Higher number = Prayer bonus is more impactful.
         */
        const val PRAYER_BONUS_BOOST = 2

        /**
         * The base resistance. Determines the base rate for how fast prayer drains without prayer bonuses.
         *
         * Higher number = Prayer drains more slowly by default. OSRS timing requires this to be `60`.
         */
        const val BASE_RESISTANCE = 60
    }
}
