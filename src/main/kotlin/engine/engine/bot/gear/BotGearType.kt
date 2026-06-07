package engine.bot.gear

/**
 * Represents a logical gear group that a bot can evaluate when selecting equipment.
 *
 * A gear type may represent a full set, a partial set, a fallback group, a combat style group, or any other reusable
 * collection of item ids that share one or more equipment purposes.
 *
 * @author lare96
 */
interface BotGearType {

    /**
     * Returns the selection priority for this gear type.
     *
     * Higher values should generally be preferred before lower values. Duplicate priority values are allowed, so callers
     * should avoid storing sorted gear types in a collection that drops equal comparator values, such as a `TreeSet`
     * without a tie-breaker.
     *
     * @return The priority of this gear type.
     */
    fun priority(): Int

    /**
     * Returns whether this gear type contains the supplied item id.
     *
     * @param id The item id to check.
     *
     * @return `true` if this gear type contains [id], otherwise `false`.
     */
    fun containsId(id: Int): Boolean

    /**
     * Returns whether this gear type supports the supplied gear purpose.
     *
     * @param purpose The gear purpose to check.
     *
     * @return `true` if this gear type supports [purpose], otherwise `false`.
     */
    fun containsPurpose(purpose: BotGearPurpose): Boolean

    /**
     * Returns the purposes this gear type can satisfy.
     *
     * A gear type may support multiple purposes. For example, a rune platebody could be part of melee, tank, wilderness,
     * or general fallback gear depending on how the implementing type is defined.
     *
     * @return The supported purposes for this gear type.
     */
    fun purposes(): Set<BotGearPurpose>

    /**
     * Returns the item ids that belong to this gear type.
     *
     * @return The item ids included in this gear type.
     */
    fun ids(): Set<Int>
}