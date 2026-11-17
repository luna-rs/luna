package game.skill.cooking.cookFood

import game.skill.Skills

/**
 * Shares the current Luna karambwan burn policy between native cooking and account-specific production estimates.
 * Other foods retain their existing burn rules, including their existing source and equipment behavior.
 *
 * @author lare96
 */
object KarambwanCooking {

    /**
     * Returns the current-level success probability, with five extra points on the 256-point curve for a range.
     * The explicit burn-stop level remains above natural level 99; no cooking-gauntlet benefit is applied.
     */
    fun successRate(level: Int, usingFire: Boolean): Double {
        val food = Food.KARAMBWAN
        if (level < food.lvl) return 0.0
        if (level >= food.burnStopLvl) return 1.0
        val rate = Skills.successRate(food.chance.first, food.chance.second, level)
        return (rate + if (usingFire) 0.0 else 5.0 / 256.0).coerceIn(0.0, 1.0)
    }
}
