package world.player.skill.crafting.potteryCrafting

/**
 * An enum representing all unfired materials that can be shaped and fired with [PotteryWheelActionItem]/[PotteryOvenActionItem].
 */
enum class Unfired(val unfiredId: Int, val firedId: Int, val level: Int, val shapingExp: Double, val firingExp: Double) {
    POT(unfiredId = 1787,
        firedId = 1931,
        level = 1,
        shapingExp = 6.3,
        firingExp = 6.3),
    PIE_DISH(unfiredId = 1789,
             firedId = 2313,
             level = 7,
             shapingExp = 15.0,
             firingExp = 10.0),
    BOWL(unfiredId = 1791,
         firedId = 1923,
         level = 8,
         shapingExp = 18.0,
         firingExp = 15.0),
    EMPTY_PLANT_POT(unfiredId = 5352,
                    firedId = 5354,
                    level = 19,
                    shapingExp = 20.0,
                    firingExp = 17.5),
    POT_LID(unfiredId = 4438,
            firedId = 4440,
            level = 25,
            shapingExp = 20.0,
            firingExp = 20.0);

    companion object {

        /**
         * A map of [unfiredId] -> [Unfired].
         */
        val UNFIRED_ID_MAP = values().associateBy { it.unfiredId }

        /**
         * A map of [firedId] -> [Unfired].
         */
        val FIRED_ID_MAP = values().associateBy { it.firedId }

        /**
         * An array of all [unfiredId] integers.
         */
        val UNFIRED_ID_ARRAY = values().map { it.unfiredId }.toIntArray()

        /**
         * An array of all [firedId] integers.
         */
        val FIRED_ID_ARRAY = values().map { it.firedId }.toIntArray()
    }
}