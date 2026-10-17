package game.skill.fishing.catchFish

import game.skill.fishing.catchFish.Tool.*

/**
 * An enum representing the kinds of fishing spot, and the [Tool] each of their options uses. [firstOption] is the
 * first menu option, and [secondOption] is the one after it.
 */
enum class FishingSpot(val npcs: Set<Int>,
                       val firstOption: Tool,
                       val secondOption: Tool? = null) {

    NET_BAIT(npcs = setOf(316, 319, 320, 323, 325, 326, 327, 330, 332),
             firstOption = SMALL_NET,
             secondOption = FISHING_ROD),
    LURE_BAIT(npcs = setOf(309, 310, 311, 314, 315, 317, 318, 328, 329, 331),
              firstOption = FLY_FISHING_ROD,
              secondOption = PIKE_FISHING_ROD),
    CAGE_HARPOON(npcs = setOf(312, 321, 324, 333),
                 firstOption = LOBSTER_POT,
                 secondOption = HARPOON),
    NET_HARPOON(npcs = setOf(313, 322, 334),
                firstOption = BIG_NET,
                secondOption = SHARK_HARPOON),

    /**
     * The Piscatoris spots, where monkfish are caught.
     */
    // TODO Swan Song: monkfish need the quest finished. During it, the net catches fresh monkfish (7942) instead.
    HARPOON_NET(npcs = setOf(3848),
                firstOption = HARPOON,
                secondOption = MONKFISH_NET),
    KARAMBWANJI(npcs = setOf(1174, 1175),
                firstOption = KARAMBWANJI_NET)
}
