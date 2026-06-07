package engine.bot.gear

import com.google.common.collect.ImmutableSet
import java.util.*

/**
 * Defines amulet-slot gear groups that bots can use when selecting equipment.
 * <p>
 * These groups are intentionally broad. Some entries represent direct combat upgrades, while others represent style,
 * utility, teleport, skilling, or personality-driven choices. The priority value can be used by equipment selection
 * logic to prefer stronger or more useful amulet groups when several matching items are available.
 *
 * @param priority The relative priority of this group.
 * @param ids The item ids that belong to this group.
 * @param firstPurpose The first purpose this group satisfies.
 * @param additionalPurposes Any additional purposes this group satisfies.
 * @author lare96
 */
enum class AmuletBotGear(val priority: Int,
                         val ids: Set<Int>,
                         private val firstPurpose: BotGearPurpose,
                         vararg additionalPurposes: BotGearPurpose) : BotGearType {

    /**
     * Teleport jewellery useful for skilling routes, minigame travel, or general utility movement.
     */
    GAMES(
        0,
        ImmutableSet.of(
            3853, // Games necklace(8).
            3855, // Games necklace(7).
            3857, // Games necklace(6).
            3859, // Games necklace(5).
            3861, // Games necklace(4).
            3863, // Games necklace(3).
            3865, // Games necklace(2).
            3867  // Games necklace(1).
        ),
        BotGearPurpose.SKILLING,
        BotGearPurpose.SHOW_OFF
    ),

    /**
     * Cosmetic, quest, novelty, or personality-driven amulets and necklaces.
     */
    STYLE(
        0,
        ImmutableSet.of(
            552, 4250, // Ghostspeak amulet.
            1662,      // Diamond necklace.
            1692,      // Gold amulet.
            1654,      // Gold necklace.
            4021,      // M'speak amulet.
            6208,      // Man speak amulet.
            6707       // Camulet.
        ),
        BotGearPurpose.SHOW_OFF
    ),

    /**
     * Expensive cosmetic amulets and necklaces.
     */
    EXPENSIVE_STYLE(
        1,
        ImmutableSet.of(
            7803,      // Yin yang amulet.
            1664,      // Dragon necklace.
            6577,      // Onyx necklace.
        ),
        BotGearPurpose.SHOW_OFF
    ),

    /**
     * Low-level accuracy amulet used as an early all-style attack bonus option.
     */
    ACCURACY(
        2,
        ImmutableSet.of(
            1478 // Amulet of accuracy.
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC
    ),

    /**
     * Defensive amulet used when survivability is preferred over damage output.
     */
    DEFENCE(
        3,
        ImmutableSet.of(
            1729 // Amulet of defence.
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC
    ),

    /**
     * Balanced combat amulet used as a general-purpose upgrade for melee, ranged, magic, and PKing setups.
     */
    POWER(
        4,
        ImmutableSet.of(
            1731 // Amulet of power.
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE,
        BotGearPurpose.RANGED,
        BotGearPurpose.MAGIC
    ),

    /**
     * Magic-focused amulet used by mage bots and PKing bots that want magic attack bonuses.
     */
    MAGIC(
        5,
        ImmutableSet.of(
            1727 // Amulet of magic.
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MAGIC
    ),

    /**
     * Strength-focused amulet used by melee bots and PKing bots that prefer max-hit bonuses.
     */
    STRENGTH(
        6,
        ImmutableSet.of(
            1725 // Amulet of strength.
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE
    ),

    /**
     * High-utility combat and skilling amulet with teleport charges.
     * <p>
     * All wearable charged variants are included so bots can use any glory they own instead of requiring a fully charged
     * one.
     */
    GLORY(
        7,
        ImmutableSet.of(
            1704, // Amulet of glory.
            1706, // Amulet of glory(1).
            1708, // Amulet of glory(2).
            1710, // Amulet of glory(3).
            1712  // Amulet of glory(4).
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE,
        BotGearPurpose.MAGIC,
        BotGearPurpose.RANGED,
        BotGearPurpose.SKILLING
    ),

    /**
     * Strong late-game combat amulet used when a bot has access to high-value gear.
     */
    FURY(
        8,
        ImmutableSet.of(
            6585 // Amulet of fury.
        ),
        BotGearPurpose.PKING,
        BotGearPurpose.MELEE,
        BotGearPurpose.MAGIC,
        BotGearPurpose.RANGED
    );

    /**
     * The bot equipment purposes this amulet group can satisfy.
     */
    val purposeSet = ImmutableSet.copyOf(EnumSet.of(firstPurpose, *additionalPurposes))

    override fun priority(): Int {
        return priority
    }

    override fun containsId(id: Int): Boolean {
        return ids.contains(id)
    }

    override fun containsPurpose(purpose: BotGearPurpose): Boolean {
        return purposeSet.contains(purpose)
    }

    override fun ids(): Set<Int> {
        return ids
    }

    override fun purposes(): Set<BotGearPurpose> {
        return purposeSet
    }
}