package game.skill.cooking.cookFood

/**
 * Shared Cooking object definitions.
 *
 * Contains the object ids used by cooking scripts when looking for valid cooking sources in the world.
 *
 * @author lare96
 */
object Cooking {

    /**
     * Fire object ids.
     *
     * These are valid cooking sources, but usually have a higher burn chance than ranges.
     */
    val FIRES = setOf(
        2732
    )

    /**
     * Range object ids.
     *
     * These are valid cooking sources used by the cooking system. Ranges are generally preferred over fires when
     * available.
     */
    val RANGES = setOf(
        114,
        2728,
        2729,
        2730,
        2731,
        2859,
        3039,
        4172,
        8750
    )

    /**
     * All object ids that can be used as cooking sources.
     */
    val COOKING_OBJECTS = FIRES + RANGES
}