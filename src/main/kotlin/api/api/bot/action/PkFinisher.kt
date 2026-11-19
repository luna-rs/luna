package api.bot.action

import api.combat.specialAttack.SpecialAttackHandler
import kotlin.random.Random

/**
 * Chooses a carried melee finisher using the native dagger's energy cost. The whip needs no energy.
 *
 * @author lare96
 */
internal object PkFinisher {
    const val WHIP = 4151
    const val DRAGON_DAGGER_S = 5698

    fun canSpec(weapon: Int, energy: Int): Boolean =
        weapon in SpecialAttackHandler.getAllWeaponIds() && energy >= SpecialAttackHandler.getDrain(weapon)

    fun choose(whipAvailable: Boolean, daggerAvailable: Boolean, random: Random = Random.Default): Int? = when {
        whipAvailable && daggerAvailable -> if (random.nextBoolean()) DRAGON_DAGGER_S else WHIP
        daggerAvailable -> DRAGON_DAGGER_S
        whipAvailable -> WHIP
        else -> null
    }
}
