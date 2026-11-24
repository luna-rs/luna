package game.skill.crafting.dyeing

/**
 * The six dyes and the cape each one dyes a cape into. Red, yellow and blue come from Aggie; the other three are mixed
 * from two of them.
 *
 * @property id The dye's item id.
 * @property colour The colour's name, as it appears in messages.
 * @property cape The cape this dye turns a cape into.
 * @property mixedFrom The two dyes mixed to make this one, or `null` for a primary dye.
 */
enum class Dye(val id: Int, val colour: String, val cape: Int, val mixedFrom: Pair<Int, Int>? = null) {
    RED(id = 1763, colour = "red", cape = 1007),
    YELLOW(id = 1765, colour = "yellow", cape = 1023),
    BLUE(id = 1767, colour = "blue", cape = 1021),
    ORANGE(id = 1769, colour = "orange", cape = 1031, mixedFrom = RED.id to YELLOW.id),
    GREEN(id = 1771, colour = "green", cape = 1027, mixedFrom = BLUE.id to YELLOW.id),
    PURPLE(id = 1773, colour = "purple", cape = 1029, mixedFrom = RED.id to BLUE.id);

    companion object {

        /**
         * The black cape. No dye makes one, but it can be dyed any other colour.
         */
        const val BLACK_CAPE = 1019

        /**
         * Crafting experience for dyeing a cape.
         */
        const val CAPE_EXPERIENCE = 2.5

        /**
         * Every cape that can be dyed, with its colour's name.
         */
        val CAPES = values().associate { it.cape to it.colour } + (BLACK_CAPE to "black")
    }
}
