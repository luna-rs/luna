package game.content.treasureTrails

/**
 * Original trail lengths and reward probabilities for the three supported tiers.
 *
 * @author lare96
 */
enum class TrailTier(val maximumSteps: Int, val rewardRolls: Int, val rareChance: Int) {
    EASY(4, 2, 97), MEDIUM(5, 3, 77), HARD(6, 4, 57)
}
