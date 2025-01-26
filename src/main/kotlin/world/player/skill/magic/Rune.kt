package world.player.skill.magic

/**
 * An enum class representing all standard runes.
 */
enum class Rune(val id: Int) {
    AIR(556),
    MIND(558),
    WATER(555),
    EARTH(557),
    FIRE(554),
    BODY(559),
    COSMIC(564),
    CHAOS(562),
    NATURE(561),
    LAW(563),
    DEATH(560),
    BLOOD(565),
    SOUL(566);

    companion object {

        /**
         * An immutable map of identifiers to rune instances.
         */
        val ID_TO_RUNE = values().associateBy { it.id }
    }
}