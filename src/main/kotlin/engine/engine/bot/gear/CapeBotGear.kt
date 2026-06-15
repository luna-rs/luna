package engine.bot.gear

/**
 * Defines cape-slot gear groups that bots can use when selecting equipment.
 *
 * These groups are intentionally broad. Some entries represent direct combat upgrades, while others represent style,
 * skilling, utility, or personality-driven choices. The priority value can be used by equipment selection logic to
 * prefer stronger, rarer, or more useful cape groups when several matching items are available.
 *
 * @param priority The relative priority of this group.
 * @param ids The item ids that belong to this group.
 * @param firstPurpose The first purpose this group satisfies.
 * @param additionalPurposes Any additional purposes this group satisfies.
 * @author lare96
 */
enum class CapeBotGear(private val priority: Int,
                       val ids: Set<Int>,
                       firstPurpose: BotGearPurpose,
                       vararg additionalPurposes: BotGearPurpose) : BotGearType {

    REGULAR(
        0,
        setOf(
            1019, // Black cape
            1021, // Blue cape
            1027, // Green cape
            1031, // Orange cape
            1029, // Purple cape
            1007, // Red cape
            1023  // Yellow cape
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.MELEE,
        BotGearPurpose.MAGIC,
        BotGearPurpose.RANGED,
        BotGearPurpose.SKILLING,
        BotGearPurpose.PKING
    ),

    CASTLE_WARS_CLOAK(
        1,
        setOf(
            4514, // Castlewars cloak, Saradomin
            4516  // Castlewars cloak, Zamorak
        ),
        BotGearPurpose.SKILLING,
        BotGearPurpose.SHOW_OFF
    ),

    FREMENNIK_CLOAK(
        2,
        setOf(
            3759, // Fremennik cloak
            3761, // Fremennik cloak
            3763, // Fremennik cloak
            3765, // Fremennik cloak
            3777, // Fremennik cloak
            3779, // Fremennik cloak
            3781, // Fremennik cloak
            3783, // Fremennik cloak
            3785, // Fremennik cloak
            3787, // Fremennik cloak
            3789  // Fremennik cloak
        ),
        BotGearPurpose.SKILLING,
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.MELEE,
        BotGearPurpose.MAGIC,
        BotGearPurpose.RANGED
    ),

    LEGENDS(
        3,
        setOf(
            1052 // Cape of legends
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING,
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    GOD(
        4,
        setOf(
            2412, // Saradomin cape
            2413, // Guthix cape
            2414  // Zamorak cape
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING,
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    OBSIDIAN(
        5,
        setOf(
            6568 // Obsidian cape
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING,
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    FIRE(
        6,
        setOf(
            6570 // Fire cape
        ),
        BotGearPurpose.SHOW_OFF,
        BotGearPurpose.SKILLING,
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    );

    /**
     * The equipment purposes this cape group can satisfy.
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