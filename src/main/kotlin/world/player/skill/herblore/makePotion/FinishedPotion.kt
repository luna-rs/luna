package world.player.skill.herblore.makePotion

import api.predef.*
import com.google.common.collect.ImmutableSet
import io.luna.game.model.item.Item

/**
 * An enum representing a drinkable potion.
 */
enum class FinishedPotion(val id: Int,
                          val unf: Int,
                          val secondary: Int,
                          val level: Int,
                          val exp: Double) {

    ATTACK_POTION(id = 221,
                  unf = 91,
                  secondary = 121,
                  level = 3,
                  exp = 25.0),
    ANTIPOISON(id = 175,
               unf = 91,
               secondary = 235,
               level = 5,
               exp = 37.5),
    STRENGTH_POTION(id = 115,
                    unf = 95,
                    secondary = 225,
                    level = 8,
                    exp = 50.0),
    SERUM_207(id = 3410,
              unf = 97,
              secondary = 592,
              level = 15,
              exp = 50.0),
    RESTORE_POTION(id = 127,
                   unf = 97,
                   secondary = 223,
                   level = 22,
                   exp = 62.5),
    BLAMISH_OIL(id = 1582,
                unf = 97,
                secondary = 1581,
                level = 25,
                exp = 80.0),
    ENERGY_POTION(id = 3010,
                  unf = 97,
                  secondary = 1975,
                  level = 26,
                  exp = 67.5),
    DEFENCE_POTION(id = 133,
                   unf = 99,
                   secondary = 239,
                   level = 30,
                   exp = 75.0),
    AGILITY_POTION(id = 3034,
                   unf = 3002,
                   secondary = 2152,
                   level = 34,
                   exp = 80.0),
    PRAYER_POTION(id = 139,
                  unf = 99,
                  secondary = 231,
                  level = 38,
                  exp = 87.5),
    SUPER_ATTACK(id = 145,
                 unf = 101,
                 secondary = 221,
                 level = 45,
                 exp = 100.0),
    SUPER_ANTIPOISON(id = 181,
                     unf = 101,
                     secondary = 235,
                     level = 48,
                     exp = 106.3),
    FISHING_POTION(id = 151,
                   unf = 103,
                   secondary = 231,
                   level = 50,
                   exp = 112.5),
    SUPER_ENERGY(id = 3018,
                 unf = 103,
                 secondary = 2970,
                 level = 52,
                 exp = 117.5),
    SUPER_STRENGTH(id = 157,
                   unf = 105,
                   secondary = 225,
                   level = 55,
                   exp = 125.0),
    SUPER_RESTORE(id = 3026,
                  unf = 3004,
                  secondary = 223,
                  level = 63,
                  exp = 142.5),
    SUPER_DEFENCE(id = 163,
                  unf = 107,
                  secondary = 239,
                  level = 66,
                  exp = 150.0),
    ANTIFIRE_POTION(id = 2454,
                    unf = 2483,
                    secondary = 243,
                    level = 69,
                    exp = 157.5),
    RANGING_POTION(id = 169,
                   unf = 109,
                   secondary = 245,
                   level = 72,
                   exp = 162.5),
    MAGIC_POTION(id = 3042,
                 unf = 2483,
                 secondary = 3138,
                 level = 76,
                 exp = 172.5),
    ZAMORAK_BREW(id = 169,
                 unf = 75,
                 secondary = 247,
                 level = 78,
                 exp = 175.0),
    SARADOMIN_BREW(id = 169,
                   unf = 3002,
                   secondary = 6693,
                   level = 81,
                   exp = 180.0);

    companion object {

        /**
         * Immutable copy of [values].
         */
        val ALL  = ImmutableSet.copyOf(values())
    }

    /**
     * The potion item.
     */
    val idItem = Item(id)

    /**
     * The unf. potion item.
     */
    val unfItem = Item(unf)

    /**
     * The secondary ingredient item.
     */
    val secondaryItem = Item(secondary)
}