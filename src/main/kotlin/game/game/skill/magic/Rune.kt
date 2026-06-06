package game.skill.magic

/**
 * Represents a standard rune type.
 *
 * Each enum entry stores the item id for that rune. These ids are used when checking inventories, spell costs,
 * rune drops, shop stock, and other magic-related item requirements.
 *
 * @property id The item id for this rune.
 * @author lare96
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
         * Maps rune item ids to their matching [Rune] entry.
         */
        val ID_TO_RUNE = values().associateBy { it.id }
    }

    /**
     * Returns whether this rune is one of the four elemental runes.
     *
     * @return `true` for air, water, earth, and fire runes.
     */
    fun isElemental(): Boolean {
        return when (this) {
            EARTH, FIRE, AIR, WATER -> true
            else -> false
        }
    }

    /**
     * Returns whether this rune is a standard non-elemental utility rune.
     *
     * This includes mind, body, cosmic, chaos, nature, and law runes.
     *
     * @return `true` if this rune belongs to the secondary rune group.
     */
    fun isSecondary(): Boolean {
        return when (this) {
            MIND, BODY, COSMIC, CHAOS, NATURE, LAW -> true
            else -> false
        }
    }

    /**
     * Returns whether this rune is one of the higher-tier standard runes.
     *
     * @return `true` for death, blood, and soul runes.
     */
    fun isAdvanced(): Boolean {
        return when (this) {
            DEATH, BLOOD, SOUL -> true
            else -> false
        }
    }
}