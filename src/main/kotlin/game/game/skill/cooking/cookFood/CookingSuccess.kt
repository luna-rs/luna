package game.skill.cooking.cookFood

import game.skill.Skills
import io.luna.game.model.Position

/**
 * Shares Luna's current-level cooking probability between native actions and production estimates.
 * Planning without a chosen source can use a fire with no equipment benefit as a conservative estimate.
 *
 * @author lare96
 */
object CookingSuccess {

    private val RANGE_O_MATIC_POSITION = Position(3212, 3215)

    /**
     * Returns the probability for the supplied current level, source and worn cooking gauntlets.
     * Existing recipe curves, burn stops and source benefits are retained; ranges add five points out of 256.
     */
    fun successRate(food: Food,
                    currentLevel: Int,
                    usingFire: Boolean,
                    cookingGauntlets: Boolean = false,
                    sourcePosition: Position? = null): Double {
        if (currentLevel < food.lvl) return 0.0
        if (food == Food.KARAMBWAN) return KarambwanCooking.successRate(currentLevel, usingFire)
        var burnLevel = food.burnStopLvl
        if (cookingGauntlets) {
            burnLevel = when (food) {
                Food.LOBSTER -> 64
                Food.SWORDFISH -> 80
                Food.MONKFISH -> 86
                Food.SHARK -> 94
                else -> burnLevel
            }
        }
        if (!usingFire && sourcePosition == RANGE_O_MATIC_POSITION) {
            burnLevel = when (food) {
                Food.BREAD -> 34
                Food.BEEF -> 31
                Food.BEAR_MEAT -> 31
                Food.CHICKEN -> 31
                Food.RAT_MEAT -> 31
                Food.SHRIMP -> 31
                Food.ANCHOVIES -> 31
                Food.SARDINE -> 34
                Food.HERRING -> 38
                Food.MACKEREL -> 41
                Food.REDBERRY_PIE -> 42
                Food.TROUT -> 45
                Food.COD -> 46
                Food.PIKE -> 49
                Food.MEAT_PIE -> 49
                Food.SALMON -> 55
                else -> burnLevel
            }
        }
        if (currentLevel >= burnLevel) return 1.0
        val rate = Skills.successRate(food.chance.first, food.chance.second, currentLevel)
        return (rate + if (usingFire) 0.0 else 5.0 / 256.0).coerceIn(0.0, 1.0)
    }
}
