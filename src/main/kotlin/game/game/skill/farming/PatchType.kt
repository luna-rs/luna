package game.skill.farming

/**
 *
 * @author lare96
 */
enum class PatchType(val cycleMinutes: Int = 1) {
    ALLOTMENT(cycleMinutes = 10),
    FLOWER,
    HERB, HOPS, BUSH,
    TREE, FRUIT_TREE,
    MUSHROOM,
    BELLADONNA,
    CALQUAT,
    SPIRIT_TREE,
    CACTUS
}