package engine.bot.gear

/**
 * Defines head-slot gear groups that bots can use when selecting equipment.
 *
 * These groups are the holiday rares. Their priority stays at `0` because [BotGearSelector.ALL_GEAR] gives an item id
 * its highest declared priority for every purpose, so a higher value would also rank these hats above real helmets in
 * combat layouts. [BotGearSelector.find] puts them on the head in show-off layouts instead, and a bot that owns several
 * wears the first one in declaration order.
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
