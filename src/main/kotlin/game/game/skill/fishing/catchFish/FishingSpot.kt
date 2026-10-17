package game.skill.fishing.catchFish

import game.skill.fishing.catchFish.Tool.*

/**
 * An enum representing the kinds of fishing spot, and the [Tool] each of their options uses. [firstOption] is the
 * first menu option, and [secondOption] is the one after it.
 */
enum class FishingSpot(val npcs: Set<Int>,
                       val objects: Set<Int> = emptySet(),
                       val firstOption: Tool?,
                       val secondOption: Tool? = null) {

    NET_BAIT(npcs = setOf(316, 319, 320, 323, 325, 326, 327, 330, 332, 1331),
             firstOption = SMALL_NET,
             secondOption = FISHING_ROD),
    LURE_BAIT(npcs = setOf(309, 310, 311, 314, 315, 317, 318, 328, 329, 331, 927, 1189, 1190, 3019),
              objects = setOf(2027),
              firstOption = FLY_FISHING_ROD,
              secondOption = PIKE_FISHING_ROD),
    CAGE_HARPOON(npcs = setOf(312, 321, 324, 333, 1332, 3804),
                 firstOption = LOBSTER_POT,
                 secondOption = HARPOON),
    NET_HARPOON(npcs = setOf(313, 322, 334, 1191, 1333, 1405, 1406, 3574, 3575),
                firstOption = BIG_NET,
                secondOption = SHARK_HARPOON),

    /**
     * The Miscellania spots. The catch goes to Fisherman Frodi.
     */
    MISCELLANIA(npcs = setOf(1399),
                firstOption = LOBSTER_POT,
                secondOption = HARPOON),

    /**
     * The Piscatoris spots, where monkfish are caught.
     */
    // TODO Swan Song: monkfish need the quest finished. During it, the net catches fresh monkfish (7942) instead.
    HARPOON_NET(npcs = setOf(3848),
                firstOption = HARPOON,
                secondOption = MONKFISH_NET),

    /**
     * The Lumbridge Swamp Caves spots.
     */
    CAVE_EEL(npcs = setOf(2067, 2068),
             // TODO Frog spawn fishing on the Net option.
             firstOption = null,
             secondOption = CAVE_EEL_FISHING_ROD),
    SLIMY_EEL(npcs = setOf(1236, 1237, 1238),
              firstOption = SLIMY_EEL_FISHING_ROD),
    LAVA_EEL(npcs = setOf(800),
             objects = setOf(2630, 14428),
             firstOption = LAVA_EEL_FISHING_ROD),
    KARAMBWANJI(npcs = setOf(1174, 1175),
                firstOption = KARAMBWANJI_NET),

    /**
     * The Karambwan spots. 1178 is Lubufu's spot in Brimhaven.
     */
    KARAMBWAN(npcs = setOf(1176, 1177, 1178),
              firstOption = KARAMBWAN_VESSEL)
}
