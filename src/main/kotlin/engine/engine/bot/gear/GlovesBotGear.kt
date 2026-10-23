package engine.bot.gear

/**
 * Defines hand-slot gear groups that bots can use when selecting equipment.
 *
 * These groups include true gloves, gauntlets, vambraces, and other hand-slot equipment. Some entries are combat
 * upgrades, while others are cosmetic, skilling, utility, or personality-driven choices.
 *
 * The priority value is used by equipment selection logic to prefer stronger, rarer, or more useful hand-slot gear
 * when several matching items are available.
 *
 * @param priority The relative priority of this glove group.
 * @param ids The item ids that belong to this group.
 * @param firstPurpose The first purpose this group satisfies.
 * @param additionalPurposes Any additional purposes this group satisfies.
 * @author lare96
 */
enum class GlovesBotGear(
    private val priority: Int,
    val ids: Set<Int>,
    firstPurpose: BotGearPurpose,
    vararg additionalPurposes: BotGearPurpose
) : BotGearType {

    /**
     * Basic leather gloves.
     *
     * These are low-tier hand-slot items mostly used by early-game bots, casual bots, or bots that simply need
     * something equipped.
     */
    LEATHER(
        0,
        setOf(
            1059 // Leather gloves
        ),
        BotGearPurpose.SKILLING,
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED
    ),

    /**
     * Cosmetic coloured gloves.
     *
     * These are mostly used for personality, variety, and show-off outfits
     * rather than serious combat upgrades.
     */
    COLOURED(
        1,
        setOf(
            2902, // Grey gloves
            2912, // Red gloves
            2922, // Yellow gloves
            2932, // Teal gloves
            2942, // Purple gloves
            6629  // White gloves
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING
    ),

    /**
     * Low-tier ranged hand-slot equipment.
     *
     * These are useful for early ranged bots before dragonhide vambraces become
     * available.
     */
    BASIC_VAMBRACES(
        2,
        setOf(
            1063, // Leather vambraces
            7453  // Hardleather gloves
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    /**
     * Green dragonhide vambraces.
     */
    GREEN_DHIDE_VAMBRACES(
        3,
        setOf(
            1065 // Green d'hide vambraces
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    /**
     * Blue dragonhide vambraces.
     */
    BLUE_DHIDE_VAMBRACES(
        4,
        setOf(
            2487 // Blue d'hide vambraces
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    /**
     * Red dragonhide vambraces.
     */
    RED_DHIDE_VAMBRACES(
        5,
        setOf(
            2489 // Red d'hide vambraces
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    /**
     * Black dragonhide vambraces.
     */
    BLACK_DHIDE_VAMBRACES(
        6,
        setOf(
            2491 // Black d'hide vambraces
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    /**
     * Utility gauntlets.
     *
     * These are useful for skilling or special-purpose behavior rather than
     * being direct general combat upgrades.
     */
    UTILITY_GAUNTLETS(
        7,
        setOf(
            775,  // Cooking gauntlets
            776,  // Goldsmith gauntlets
            777,  // Chaos gauntlets
            1580, // Ice gloves
        ),
        BotGearPurpose.SKILLING,
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.MAGIC
    ),

    /**
     * Splitbark gauntlets.
     *
     * Mid-tier magic hand-slot equipment.
     */
    SPLITBARK(
        8,
        setOf(
            3391 // Splitbark gauntlets
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    /**
     * Mystic gloves.
     *
     * Includes the main mystic glove variants.
     */
    MYSTIC(
        9,
        setOf(
            4095, // Mystic gloves
            4105, // Mystic gloves
            4115  // Mystic gloves
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    /**
     * Skeletal gloves.
     *
     * Higher-style magic hand-slot gear used by magic-focused bots.
     */
    SKELETAL(
        10,
        setOf(
            6153 // Skeletal gloves
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    ),

    /**
     * Infinity gloves.
     *
     * Stronger magic hand-slot gear for richer or higher-priority magic bots.
     */
    INFINITY(
        11,
        setOf(
            6922 // Infinity gloves
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    /**
     * Bronze gloves from Recipe for Disaster.
     */
    BRONZE(
        12,
        setOf(
            7454 // Bronze gloves
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    /**
     * Iron gloves from Recipe for Disaster.
     */
    IRON(
        13,
        setOf(
            7455 // Iron gloves
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    /**
     * Steel gloves from Recipe for Disaster.
     */
    STEEL(
        14,
        setOf(
            7456 // Steel gloves
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    /**
     * Black gloves from Recipe for Disaster.
     */
    BLACK(
        15,
        setOf(
            7457 // Black gloves
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    /**
     * Mithril gloves from Recipe for Disaster.
     */
    MITHRIL(
        16,
        setOf(
            7458 // Mithril gloves
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    /**
     * Adamant gloves from Recipe for Disaster.
     */
    ADAMANT(
        17,
        setOf(
            7459 // Adamant gloves
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    /**
     * Rune gloves from Recipe for Disaster.
     */
    RUNE(
        18,
        setOf(
            7460 // Rune gloves
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    /**
     * Dragon gloves from Recipe for Disaster.
     */
    DRAGON(
        19,
        setOf(
            7461 // Dragon gloves
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    /**
     * Barrows gloves from Recipe for Disaster.
     *
     * These are the strongest general-purpose glove option in this set and
     * should usually win when a bot has access to them.
     */
    BARROWS(
        20,
        setOf(
            7462 // Barrows gloves
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    );

    /**
     * The equipment purposes this glove group can satisfy.
     */
    val purposeSet: Set<BotGearPurpose> = setOf(firstPurpose, *additionalPurposes)

    override fun priority(): Int {
        return priority
    }

    override fun ids(): Set<Int> {
        return ids
    }

    override fun purposes(): Set<BotGearPurpose> {
        return purposeSet
    }
}