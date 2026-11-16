package api.bot.action.trading

import io.luna.game.model.def.ItemDefinition
import java.util.Collections

/**
 * Keeps one owned working copy of reviewed, non-consuming utility items without creating purchase intentions.
 * Charged variants, consumables and tiered equipment retain their separate activity and equipment policies.
 *
 * @author lare96
 */
object BotReusableItems {
    private val ids = Collections.unmodifiableSet(linkedSetOf(
        983, 1590, // Brass and dusty keys.
        590, 946, 2347, 1755, 1733, 233, 1785, 4, // Portable production tools.
        1592, 1597, 1595, 1599, 2976, 5523, 6747, 1594, 4200, 7649, // Native jewellery moulds.
        301, 303, 305, 307, 309, 311, // Ordinary fishing tools, excluding bait and loaded vessels.
        952, 5341, 5343, 5325, 5329 // Durable farming hand tools.
    ))

    /**
     * Returns canonical ids without depending on loaded item definitions, including for offline maintenance.
     */
    @JvmStatic
    fun itemIds(): Set<Int> = ids

    /**
     * Recognizes the reviewed item or its noted representation when definitions are available.
     */
    @JvmStatic
    fun isReusable(id: Int): Boolean = id in ids ||
        id in 0..0xffff && ItemDefinition.ALL.get(id).map { it.unnotedId.orElse(id) in ids }.orElse(false)

    /**
     * Supplies an ownership quota, rather than a request to acquire an absent item.
     */
    @JvmStatic
    fun retained(id: Int): Int = if (isReusable(id)) 1 else 0

    /**
     * Avoids consumable stockpiles for ordinary reusable requests while preserving a larger explicit minimum.
     */
    @JvmStatic
    fun personalTarget(id: Int, minimum: Int, proposed: Int): Int =
        maxOf(minimum, if (isReusable(id)) 1 else proposed)
}
