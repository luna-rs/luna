package engine.bot.gear

/**
 * Defines boot-slot gear groups that bots can select from.
 *
 * Each enum constant represents one logical group of boots rather than one exact item. This allows the bot gear
 * system to treat visually or functionally similar boots as interchangeable choices for the same role.
 *
 * @param priority The relative priority of this group.
 * @param ids The item ids that belong to this group.
 * @param firstPurpose The first purpose this group satisfies.
 * @param additionalPurposes Any additional purposes this group satisfies.
 * @author lare96
 */
enum class BootsBotGear(private val priority: Int,
                        val ids: Set<Int>,
                        firstPurpose: BotGearPurpose,
                        vararg additionalPurposes: BotGearPurpose) : BotGearType {

    LEATHER_BOOTS(
        0,
        setOf(1061, 6893),
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC,
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING
    ),

    CHROMATIC_BOOTS(
        1,
        setOf(
            2894, // Grey boots
            2934, // Purple boots
            2904, // Red boots
            2924, // Teal boots
            2914, // Yellow boots
            3791  // Fremennik boots
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING
    ),

    GNOME_AND_SLAVE_BOOTS(
        2,
        setOf(
            630,  // Blue boots
            632,  // Cream boots
            628,  // Green boots
            626,  // Pink boots
            634,  // Turquoise boots
            6790, // Shoes
            1846  // Slave boots
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING
    ),

    BRONZE_AND_ROCKSHELL_BOOTS(
        0,
        setOf(
            4119, // Bronze boots
            6145  // Rock-shell boots
        ),
        BotGearPurpose.MELEE
    ),

    IRON_BOOTS(
        1,
        setOf(4121),
        BotGearPurpose.MELEE
    ),

    STEEL_BOOTS(
        2,
        setOf(4123),
        BotGearPurpose.MELEE
    ),

    BLACK_BOOTS(
        3,
        setOf(4125),
        BotGearPurpose.MELEE
    ),

    MITHRIL_BOOTS(
        4,
        setOf(4127),
        BotGearPurpose.MELEE
    ),

    ADAMANT_BOOTS(
        5,
        setOf(4129),
        BotGearPurpose.MELEE
    ),

    RUNE_BOOTS(
        6,
        setOf(4131),
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING
    ),

    SKELETAL_BOOTS(
        0,
        setOf(6147),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    SPLITBARK_GREAVES(
        1,
        setOf(3393),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    WIZARD_BOOTS(
        2,
        setOf(2579),
        BotGearPurpose.MAGIC,
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    ),

    MYSTIC_BOOTS(
        3,
        setOf(
            4097, // Mystic boots
            4107, // Dark mystic boots
            4117  // Light mystic boots
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    INFINITY_BOOTS(
        4,
        setOf(6920),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    SPINED_BOOTS(
        1,
        setOf(6143),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    SNAKESKIN_BOOTS(
        2,
        setOf(6328),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    MISC_SHOW_OFF_BOOTS(
        2,
        setOf(
            3105, // Climbing boots
            1837, // Desert boots
            4310, // HAM boots
            6069, // Mourner boots
            3107  // Spiked boots
        ),
        BotGearPurpose.SHOW_OFF
    ),

    BOOTS_OF_LIGHTNESS(
        3,
        setOf(88, 89),
        BotGearPurpose.SKILLING,
        BotGearPurpose.SHOW_OFF
    ),

    RANGER_BOOTS(
        4,
        setOf(2577),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    );

    /**
     * The purposes this boot group can be selected for.
     */
    val purposes: Set<BotGearPurpose> = setOf(firstPurpose, *additionalPurposes)

    override fun priority(): Int {
        return priority
    }

    override fun containsId(id: Int): Boolean {
        return id in ids
    }

    override fun containsPurpose(purpose: BotGearPurpose): Boolean {
        return purpose in purposes
    }
}