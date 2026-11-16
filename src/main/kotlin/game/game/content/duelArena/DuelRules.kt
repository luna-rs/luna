/*
 * Rules and interface identifiers adapted from LostCityRS/Content, commit 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
 */
package game.content.duelArena

/**
 * The original Arena's selectable rules and native interface buttons.
 *
 * @author lare96
 */
enum class DuelRules(val bit: Int, val button: Int, val label: String) {
    NO_FORFEIT(0, 6721, "No forfeiting"), NO_MOVEMENT(1, 6722, "No movement"),
    NO_RANGED(4, 6725, "No ranged"), NO_MELEE(5, 6726, "No melee"),
    NO_MAGIC(6, 6727, "No magic"), NO_POTIONS(7, 6728, "No drinks"),
    NO_FOOD(8, 6729, "No food"), NO_PRAYER(9, 6730, "No prayer"),
    OBSTACLES(10, 6732, "Obstacles"), FLOWERS(12, 670, "Flowers only"),
    NO_SPECIAL(13, 7816, "No special attacks");

    fun enabled(mask: Int): Boolean = mask and (1 shl bit) != 0

    companion object {
        fun valid(mask: Int): Boolean = !(NO_MELEE.enabled(mask) &&
            (NO_FORFEIT.enabled(mask) || NO_MOVEMENT.enabled(mask) || FLOWERS.enabled(mask) ||
                NO_RANGED.enabled(mask) && NO_MAGIC.enabled(mask)))
    }
}
