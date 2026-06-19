package game.skill.farming

/**
 *
 * @author lare96
 */
@Deprecated("Use instead subclasses of FarmingPatch")
enum class PatchType(val cycleMinutes: Int = 1) {
    ALLOTMENT(cycleMinutes = 10),
    FLOWER,
    HERB,
    HOPS,
    BUSH,
    TREE,
    FRUIT_TREE,
    MUSHROOM,
    BELLADONNA,
    CALQUAT,
    SPIRIT_TREE,
    CACTUS
}