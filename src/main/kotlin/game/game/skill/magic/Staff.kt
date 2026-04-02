package game.skill.magic

/**
 * An enumeration of staves that provide unlimited rune representation for one or more [Rune] types.
 *
 * @author lare96
 */
enum class Staff(val ids: Set<Int>, val represents: Set<Rune>) {

    /**
     * Staves that provide unlimited water runes.
     */
    WATER(ids = setOf(1383, 1395, 1403),
        represents = setOf(Rune.WATER)),

    /**
     * Staves that provide unlimited air runes.
     */
    AIR(ids = setOf(1381, 1397, 1405),
        represents = setOf(Rune.AIR)),

    /**
     * Staves that provide unlimited earth runes.
     */
    EARTH(ids = setOf(1385, 1399, 1407),
        represents = setOf(Rune.EARTH)),

    /**
     * Staves that provide unlimited fire runes.
     */
    FIRE(ids = setOf(1387, 1401, 1393),
        represents = setOf(Rune.FIRE)),

    /**
     * Staves that provide unlimited fire and earth runes.
     */
    LAVA(ids = setOf(3053, 3054),
        represents = setOf(Rune.FIRE, Rune.EARTH)),

    /**
     * Staves that provide unlimited fire and earth runes.
     */
    MUD(ids = setOf(3053, 3054),
         represents = setOf(Rune.WATER, Rune.EARTH));

    companion object {

        /**
         * The staff item ids that can autocast ancient spells.
         */
        val AUTOCAST_ANCIENTS = setOf(4675, 4710, 6914)

        /**
         * An immutable mapping of all staff item ids to their corresponding [Staff] instance.
         */
        val ID_TO_STAFF: Map<Int, Staff> = values().run {
            val map = HashMap<Int, Staff>()
            for (staff in this) {
                for (id in staff.ids) {
                    map[id] = staff
                }
            }
            return@run map
        }
    }
}