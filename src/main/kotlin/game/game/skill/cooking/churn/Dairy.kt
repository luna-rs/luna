package game.skill.cooking.churn

/**
 * What a dairy churn makes, in churning order: each product can be churned on into the ones after it.
 */
enum class Dairy(val id: Int, val lvl: Int) {
    MILK(id = 1927, lvl = 1),
    CREAM(id = 2130, lvl = 21),
    BUTTER(id = 6697, lvl = 38),
    CHEESE(id = 1985, lvl = 48);

    /**
     * The experience for churning [from] into this product.
     */
    fun exp(from: Dairy) = EXP.getValue(from to this)

    companion object {
        const val EMPTY_BUCKET = 1925

        /**
         * The ticks one churning stage takes; churning milk straight to cheese takes three.
         */
        const val STAGE_TICKS = 10

        val CHURNS = setOf(10093, 10094)

        val INGREDIENTS = listOf(MILK, CREAM, BUTTER)

        private val EXP = mapOf(
            (MILK to CREAM) to 18.0,
            (MILK to BUTTER) to 40.5,
            (MILK to CHEESE) to 64.0,
            (CREAM to BUTTER) to 22.5,
            (CREAM to CHEESE) to 46.0,
            (BUTTER to CHEESE) to 23.5)
    }
}
