package engine.bot.gear

/**
 * Defines head-slot gear groups that bots can use when selecting equipment.
 *
 * These groups include holiday rares, decorative hats, and combat headgear. Their priority stays at `0` because
 * [BotGearSelector.ALL_GEAR] gives an item id its highest declared priority for every purpose, so a higher value would
 * also promote decorative hats in combat layouts. [BotGearSelector.find] prefers the first eligible owned item in
 * declaration order for show-off layouts, keeping holiday rares ahead of the other hats.
 *
 * @param priority The relative priority of this group.
 * @param ids The item ids that belong to this group.
 * @param firstPurpose The first purpose this group satisfies.
 * @param additionalPurposes Any additional purposes this group satisfies.
 * @author TheLining
 */
enum class HeadBotGear(private val priority: Int,
                       val ids: Set<Int>,
                       firstPurpose: BotGearPurpose,
                       vararg additionalPurposes: BotGearPurpose) : BotGearType {

    PARTYHAT(
        0,
        setOf(
            1038, // Red partyhat
            1040, // Yellow partyhat
            1042, // Blue partyhat
            1044, // Green partyhat
            1046, // Purple partyhat
            1048  // White partyhat
        ),
        BotGearPurpose.SHOW_OFF
    ),

    HALLOWEEN_MASK(
        0,
        setOf(
            1053, // Green h'ween mask
            1055, // Blue h'ween mask
            1057  // Red h'ween mask
        ),
        BotGearPurpose.SHOW_OFF
    ),

    SANTA_HAT(
        0,
        setOf(
            1050 // Santa hat
        ),
        BotGearPurpose.SHOW_OFF
    ),

    ROBIN_HOOD_HAT(
        0,
        setOf(
            2581 // Robin hood hat
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING,
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    CAVALIER(
        0,
        setOf(
            2639, // Tan cavalier
            2641, // Dark cavalier
            2643  // Black cavalier
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING
    ),

    BERET(
        0,
        setOf(
            2633, // Blue beret
            2635, // Black beret
            2637  // White beret
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING
    ),

    BOATER(
        0,
        setOf(
            7319, // Red boater
            7321, // Orange boater
            7323, // Green boater
            7325, // Blue boater
            7327  // Black boater
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING
    ),

    PIRATE_HAT(
        0,
        setOf(
            2651 // Pirate's hat
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING
    ),

    HEADBAND(
        0,
        setOf(
            2645, // Red headband
            2647, // Black headband
            2649  // Brown headband
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING
    ),

    TRIBAL_MASK(
        0,
        setOf(
            6335, // Tribal mask
            6337, // Tribal mask
            6339  // Tribal mask
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING
    ),

    VILLAGER_HAT(
        0,
        setOf(
            6345, // Villager hat
            6355, // Villager hat
            6365, // Villager hat
            6375  // Villager hat
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING
    ),

    FEZ(
        0,
        setOf(
            6382 // Fez
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING
    ),

    LEDERHOSEN_HAT(
        0,
        setOf(
            6182 // Lederhosen hat
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING
    ),

    FROG_MASK(
        0,
        setOf(
            6188 // Frog mask
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING
    ),

    GNOME_HAT(
        0,
        setOf(
            656, // Hat
            658, // Hat
            660, // Hat
            662, // Hat
            664  // Hat
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING
    ),

    WIZARD_HAT(
        0,
        setOf(
            7394, // Wizard hat (g)
            7396, // Wizard hat (t)
            579,  // Wizard hat
            1017  // Wizard hat
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING,
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    INITIATE_HELM(
        0,
        setOf(
            5574 // Initiate helm
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING,
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING
    );

    /**
     * The equipment purposes this head group can satisfy.
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
