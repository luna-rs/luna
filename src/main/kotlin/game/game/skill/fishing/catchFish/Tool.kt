package game.skill.fishing.catchFish

import game.skill.fishing.catchFish.Fish.*

/**
 * An enum representing the ways [Fish] can be caught. Each entry is one fishing spot option, so the same tool item
 * can appear more than once.
 *
 * @author lare96
 */
enum class Tool(val id: Int,
                val level: Int,
                val bait: Int? = null,
                val fish: List<Fish>,
                val firstRoll: Int,
                val delay: Int,
                val animation: Int,
                val castAnimation: Int = animation,
                val animateFirstRoll: Boolean = true,
                val castMessages: List<String>,
                val levelMessage: String = "",
                val toolMessage: String,
                val fullMessage: String = "You can't carry any more fish.",
                val checkSpaceOnCast: Boolean = true,
                val catchMessages: Map<Fish, String> = emptyMap()) {

    SMALL_NET(id = 303,
              level = 1,
              fish = listOf(ANCHOVY, SHRIMP),
              firstRoll = 4,
              delay = 6,
              animation = 621,
              animateFirstRoll = false,
              castMessages = listOf("You cast out your net..."),
              toolMessage = "You need a net to catch these fish."),
    // TODO Karam/bwan/bwanji fishing and cooking
    KARAMBWANJI_NET(id = 303,
                    level = 5,
                    fish = listOf(KARAMBWANJI, SHRIMP),
                    firstRoll = 4,
                    delay = 6,
                    animation = 621,
                    animateFirstRoll = false,
                    castMessages = listOf("You cast out your net..."),
                    levelMessage = "You need at least 5 Fishing to fish here.",
                    toolMessage = "You need a net to catch these fish.",
                    catchMessages = mapOf(SHRIMP to "You catch some shrimp.")),
    FISHING_ROD(id = 307,
                level = 5,
                bait = 313,
                fish = listOf(HERRING, SARDINE),
                firstRoll = 5,
                delay = 5,
                animation = 623,
                castAnimation = 622,
                castMessages = listOf("You cast out your line...", "You attempt to catch a fish."),
                levelMessage = "You need at least 5 Fishing to catch these fish.",
                toolMessage = "You need a Fishing Rod to bait these fish."),
    BIG_NET(id = 305,
            level = 16,
            fish = listOf(LEATHER_BOOTS, SEAWEED, LEATHER_GLOVES, MACKEREL, OYSTER, CASKET, COD, BASS),
            firstRoll = 4,
            delay = 6,
            animation = 620,
            animateFirstRoll = false,
            castMessages = listOf("You cast out your net..."),
            levelMessage = "You need at least 16 Fishing to catch these fish.",
            toolMessage = "You need a big net to catch these fish."),
    FLY_FISHING_ROD(id = 309,
                    level = 20,
                    bait = 314,
                    fish = listOf(SALMON, TROUT),
                    firstRoll = 4,
                    delay = 5,
                    animation = 623,
                    castAnimation = 622,
                    castMessages = listOf("You cast out your line...", "You attempt to catch a fish."),
                    levelMessage = "You need at least 20 Fishing to lure these fish.",
                    toolMessage = "You need a fly fishing rod to lure these fish.",
                    checkSpaceOnCast = false),
    PIKE_FISHING_ROD(id = 307,
                     level = 25,
                     bait = 313,
                     fish = listOf(PIKE),
                     firstRoll = 4,
                     delay = 5,
                     animation = 623,
                     castAnimation = 622,
                     castMessages = listOf("You cast out your line...", "You attempt to catch a fish."),
                     levelMessage = "You need at least 25 Fishing to bait these fish.",
                     toolMessage = "You need a Fishing Rod to bait these fish.",
                     checkSpaceOnCast = false),
    SLIMY_EEL_FISHING_ROD(id = 307,
                          level = 28,
                          bait = 313,
                          fish = listOf(SLIMY_EEL),
                          firstRoll = 5,
                          delay = 5,
                          animation = 623,
                          castAnimation = 622,
                          castMessages = listOf("You cast out your line...", "You attempt to catch a fish."),
                          levelMessage = "You need at least 28 Fishing to catch these fish.",
                          toolMessage = "You need a Fishing Rod to bait these fish."),
    CAVE_EEL_FISHING_ROD(id = 307,
                         level = 28,
                         bait = 313,
                         fish = listOf(CAVE_EEL, SLIMY_EEL),
                         firstRoll = 5,
                         delay = 5,
                         animation = 623,
                         castAnimation = 622,
                         castMessages = listOf("You cast out your line...", "You attempt to catch a fish."),
                         levelMessage = "You need at least 28 Fishing to catch these fish.",
                         toolMessage = "You need a Fishing Rod to bait these fish."),
    HARPOON(id = 311,
            level = 35,
            fish = listOf(SWORDFISH, TUNA),
            firstRoll = 5,
            delay = 6,
            animation = 618,
            castMessages = listOf("You start Harpooning fish."),
            levelMessage = "You need at least 35 Fishing to harpoon these fish.",
            toolMessage = "You need a harpoon to catch these fish."),
    LOBSTER_POT(id = 301,
                level = 40,
                fish = listOf(LOBSTER),
                firstRoll = 4,
                delay = 6,
                animation = 619,
                castMessages = listOf("You attempt to catch a Lobster."),
                levelMessage = "You need at least 40 Fishing to catch Lobsters.",
                toolMessage = "You need a lobster pot to catch lobsters.",
                fullMessage = "You can't carry any more lobsters."),
    LAVA_EEL_FISHING_ROD(id = 1585,
                         level = 53,
                         bait = 313,
                         fish = listOf(LAVA_EEL),
                         firstRoll = 5,
                         delay = 5,
                         animation = 623,
                         castAnimation = 622,
                         castMessages = listOf("You cast out your line...", "You attempt to catch an eel."),
                         levelMessage = "You need to be at least level 53 Fishing to bait these eels.",
                         toolMessage = "You need a flame-proof fishing rod to bait these fish.",
                         fullMessage = "You can't carry any more eels."),
    MONKFISH_NET(id = 303,
                 level = 62,
                 fish = listOf(MONKFISH),
                 firstRoll = 4,
                 delay = 6,
                 animation = 621,
                 animateFirstRoll = false,
                 castMessages = listOf("You cast out your net..."),
                 levelMessage = "You need a Fishing level of 62 to fish this.",
                 toolMessage = "You need a net to catch these fish."),

    /**
     * A Karambwan vessel loaded with a karambwanji. Lowering it uses up the karambwanji.
     */
    KARAMBWAN_VESSEL(id = 3159,
                     level = 65,
                     fish = listOf(KARAMBWAN),
                     firstRoll = 6,
                     delay = 6,
                     animation = 1193,
                     castMessages = listOf("You lower the Karambwan vessel."),
                     levelMessage = "You need to be at least level 65 Fishing to catch Karambwan.",
                     toolMessage = "You need a Karambwan vessel to fish here.",
                     fullMessage = "You can't carry any more Karambwan."),
    SHARK_HARPOON(id = 311,
                  level = 76,
                  fish = listOf(SHARK),
                  firstRoll = 5,
                  delay = 6,
                  animation = 618,
                  castMessages = listOf("You start Harpooning fish."),
                  levelMessage = "You need at least 76 Fishing to Harpoon these fish.",
                  toolMessage = "You need a harpoon to catch these fish.");

    /**
     * The message shown when the player runs out of [bait].
     */
    val baitMessage = when (bait) {
        313 -> "You don't have any fishing bait left."
        314 -> "You don't have any feathers left."
        else -> ""
    }

    companion object {

        /**
         * An empty Karambwan vessel. Using a raw karambwanji on it loads it.
         */
        const val EMPTY_KARAMBWAN_VESSEL = 3157

        /**
         * The tool items bots start with. Bots don't fish lava eels, and start with an empty Karambwan vessel.
         */
        val ALL_IDS: Set<Int> = (entries - LAVA_EEL_FISHING_ROD - KARAMBWAN_VESSEL).map { it.id }.toSet() +
                EMPTY_KARAMBWAN_VESSEL
    }
}
