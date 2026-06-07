package engine.bot.gear

/**
 * Defines ring-slot gear groups that bots can use when selecting equipment.
 *
 * These groups are intentionally broad. Some rings are general-purpose defensive or utility items, while others are
 * combat-style upgrades for melee, ranged, or magic. The priority value can be used by equipment selection logic to
 * prefer stronger or more useful ring groups when several matching items are available.
 *
 * Ring of forging is intentionally kept as a low-priority skilling entry. Scripts that specifically smelt iron bars
 * should still request or equip it directly instead of relying on generic skilling gear selection.
 *
 * @param priority The relative priority of this group.
 * @param ids The item ids that belong to this group.
 * @param firstPurpose The first purpose this group satisfies.
 * @param additionalPurposes Any additional purposes this group satisfies.
 * @author lare96
 */
enum class RingBotGear(private val priority: Int,
                       val ids: Set<Int>,
                       firstPurpose: BotGearPurpose,
                       vararg additionalPurposes: BotGearPurpose) : BotGearType {

    RING_OF_FORGING(
        0,
        setOf(
            2568 // Ring of forging
        ),
        BotGearPurpose.SKILLING
    ),

    RING_OF_RECOIL(
        0,
        setOf(
            2550 // Ring of recoil
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    RING_OF_LIFE(
        1,
        setOf(
            2570 // Ring of life
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING,
        BotGearPurpose.SKILLING
    ),

    RING_OF_WEALTH(
        2,
        setOf(
            2572 // Ring of wealth
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC,
        BotGearPurpose.SKILLING
    ),

    WARRIOR_RING(
        3,
        setOf(
            6735 // Warrior ring
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING
    ),

    ARCHERS_RING(
        4,
        setOf(
            6733 // Archers ring
        ),
        BotGearPurpose.RANGED,
        BotGearPurpose.PKING
    ),

    SEERS_RING(
        5,
        setOf(
            6731 // Seers ring
        ),
        BotGearPurpose.MAGIC,
        BotGearPurpose.PKING
    ),

    BERSERKER_RING(
        6,
        setOf(
            6737 // Berserker ring
        ),
        BotGearPurpose.MELEE,
        BotGearPurpose.PKING
    );

    /**
     * The equipment purposes this ring group can satisfy.
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

    override fun ids(): Set<Int> {
        return ids
    }

    override fun purposes(): Set<BotGearPurpose> {
        return purposeSet
    }
}