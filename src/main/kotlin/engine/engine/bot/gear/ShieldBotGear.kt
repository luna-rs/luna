package engine.bot.gear

/**
 * Defines shield-slot gear groups that bots can use when selecting equipment.
 *
 * These groups are intentionally broad. Some entries represent normal defensive shields, while others represent
 * magic off-hands, utility shields, cosmetic shields, or high-value show-off shields. The priority value can be
 * used by equipment selection logic to prefer stronger or more useful shield groups when several matching items
 * are available.
 *
 * @param priority The relative priority of this group.
 * @param ids The item ids that belong to this group.
 * @param firstPurpose The first purpose this group satisfies.
 * @param additionalPurposes Any additional purposes this group satisfies.
 * @author lare96
 */
enum class ShieldBotGear(private val priority: Int,
                         val ids: Set<Int>,
                         firstPurpose: BotGearPurpose,
                         vararg additionalPurposes: BotGearPurpose) : BotGearType {

    WOODEN(
        0,
        setOf(
            1171, // Wooden shield
            7676  // Wooden shield.
        ),
        BotGearPurpose.MELEE
    ),

    BRONZE(
        1,
        setOf(
            1173, // Bronze sq shield
            1189  // Bronze kiteshield
        ),
        BotGearPurpose.MELEE
    ),

    IRON(
        2,
        setOf(
            1175, // Iron sq shield
            1191  // Iron kiteshield
        ),
        BotGearPurpose.MELEE
    ),

    STEEL(
        3,
        setOf(
            1177, // Steel sq shield
            1193  // Steel kiteshield
        ),
        BotGearPurpose.MELEE
    ),

    BLACK(
        4,
        setOf(
            1179, // Black sq shield
            1195  // Black kiteshield
        ),
        BotGearPurpose.MELEE
    ),

    MITHRIL(
        5,
        setOf(
            1181, // Mithril sq shield
            1197  // Mithril kiteshield
        ),
        BotGearPurpose.MELEE
    ),

    ADAMANT(
        6,
        setOf(
            1183, // Adamant sq shield
            1199, // Adamant kiteshield
            6894  // Adamant kiteshield
        ),
        BotGearPurpose.MELEE
    ),

    RUNE(
        7,
        setOf(
            1185, // Rune sq shield
            1201  // Rune kiteshield
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING
    ),

    DRAGON(
        8,
        setOf(
            1187 // Dragon sq shield
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    ),

    WHITE(
        4,
        setOf(
            6631, // White sq shield
            6633  // White kiteshield
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.SHOW_OFF
    ),

    TRIMMED_KITESHIELDS(
        5,
        setOf(
            2589, // Black kiteshield (t)
            2603, // Adam kiteshield (t)
            2629  // Rune kiteshield (t)
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.MELEE
    ),

    GOLD_KITESHIELDS(
        6,
        setOf(
            2597, // Black kiteshield (g)
            2611, // Adam kiteshield (g)
            2621, // Rune kiteshield (g)
            3488  // Gilded kiteshield
        ),
        BotGearPurpose.SHOW_OFF
    ),

    GOD_KITESHIELDS(
        7,
        setOf(
            2659, // Zamorak kiteshield
            2667, // Saradomin kite
            2675  // Guthix kiteshield
        ),
        BotGearPurpose.SHOW_OFF
    ),

    HERALDIC_KITESHIELDS(
        7,
        setOf(
            7332, // Black kiteshield(h)
            7334, // Adam kiteshield(h)
            7336, // Rune kiteshield(h)
            7338, // Black kiteshield(h)
            7340, // Adam kiteshield(h)
            7342, // Rune kiteshield(h)
            7344, // Black kiteshield(h)
            7346, // Adam kiteshield(h)
            7348, // Rune kiteshield(h)
            7350, // Black kiteshield(h)
            7352, // Adam kiteshield(h)
            7354, // Rune kiteshield(h)
            7356, // Black kiteshield(h)
            7358, // Adam kiteshield(h)
            7360  // Rune kiteshield(h)
        ),
        BotGearPurpose.SHOW_OFF
    ),

    DECORATIVE_SHIELDS(
        4,
        setOf(
            4072, // Decorative shield
            4507, // Decorative shield
            4512  // Decorative shield
        ),
        BotGearPurpose.SHOW_OFF
    ),

    FREMENNIK_SHIELD(
        3,
        setOf(
            3758 // Fremennik shield
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING
    ),

    GRANITE_SHIELD(
        7,
        setOf(
            3122 // Granite shield
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING
    ),

    TOKTZ_KET_XIL(
        8,
        setOf(
            6524 // Toktz-ket-xil
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING
    ),

    ANTI_DRAGON_SHIELD(
        1,
        setOf(
            1540 // Anti-dragon shield
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    ELEMENTAL_SHIELD(
        2,
        setOf(
            2890 // Elemental shield
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING
    ),

    MIRROR_SHIELD(
        2,
        setOf(
            4156 // Mirror shield
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC
    ),

    CRYSTAL_SHIELD(
        9,
        setOf(
            4224, // New crystal shield
            4225, // Crystal shield full
            4226, // Crystal shield 9/10
            4227, // Crystal shield 8/10
            4228, // Crystal shield 7/10
            4229, // Crystal shield 6/10
            4230, // Crystal shield 5/10
            4231, // Crystal shield 4/10
            4232, // Crystal shield 3/10
            4233, // Crystal shield 2/10
            4234  // Crystal shield 1/10
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    ),

    GOD_BOOK(
        3,
        setOf(
            3840, // Holy book
            3842, // Unholy book
            3844  // Book of balance
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    ),

    DAMAGED_GOD_BOOK(
        1,
        setOf(
            3839, // Damaged book, Saradomin
            3841, // Damaged book, Zamorak
            3843  // Damaged book, Guthix
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.SHOW_OFF
    ),

    MAGES_BOOK(
        5,
        setOf(
            6889 // Mage's book
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    ),

    BROODOO_SHIELD(
        3,
        setOf(
            6215, // Broodoo shield (10)
            6217, // Broodoo shield (9)
            6219, // Broodoo shield (8)
            6221, // Broodoo shield (7)
            6223, // Broodoo shield (6)
            6225, // Broodoo shield (5)
            6227, // Broodoo shield (4)
            6229, // Broodoo shield (3)
            6231, // Broodoo shield (2)
            6233, // Broodoo shield (1)
            6235, // Broodoo shield
            6237, // Broodoo shield (10)
            6239, // Broodoo shield (9)
            6241, // Broodoo shield (8)
            6243, // Broodoo shield (7)
            6245, // Broodoo shield (6)
            6247, // Broodoo shield (5)
            6249, // Broodoo shield (4)
            6251, // Broodoo shield (3)
            6253, // Broodoo shield (2)
            6255, // Broodoo shield (1)
            6257, // Broodoo shield
            6259, // Broodoo shield (10)
            6261, // Broodoo shield (9)
            6263, // Broodoo shield (8)
            6265, // Broodoo shield (7)
            6267, // Broodoo shield (6)
            6269, // Broodoo shield (5)
            6271, // Broodoo shield (4)
            6273, // Broodoo shield (3)
            6275, // Broodoo shield (2)
            6277, // Broodoo shield (1)
            6279  // Broodoo shield
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING,
        BotGearPurpose.SHOW_OFF
    );

    /**
     * The equipment purposes this shield group can satisfy.
     */
    val purposeSet: Set<BotGearPurpose> = setOf(firstPurpose, *additionalPurposes)

    override fun priority(): Int {
        return priority
    }

    override fun containsId(id: Int): Boolean {
        return id in ids
    }

    override fun containsPurpose(purpose: BotGearPurpose): Boolean {
        return purpose in purposeSet
    }
}