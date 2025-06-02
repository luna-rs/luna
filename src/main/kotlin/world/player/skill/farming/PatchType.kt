package world.player.skill.farming

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