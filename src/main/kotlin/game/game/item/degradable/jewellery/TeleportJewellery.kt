package game.item.degradable.jewellery

import com.google.common.collect.ImmutableList
import io.luna.game.model.Position

/**
 * An enum representing jewellery that can be used to teleport.
 *
 * @property items The charged item ids, from the most charges to the last charge.
 * @property emptyId The item left once the last charge is used, or `null` if the jewellery crumbles.
 * @property rub The message sent when the jewellery is rubbed.
 * @property menuDelay The ticks between rubbing the jewellery and the destination menu opening.
 * @property title The destination menu title.
 * @property destinations The destinations, in menu order.
 * @property maxWildernessLevel The deepest Wilderness level the jewellery works from.
 * @author hydrozoa
 */
enum class TeleportJewellery(val items: ImmutableList<Int>,
                             val emptyId: Int?,
                             val rub: String,
                             val menuDelay: Int,
                             val title: String,
                             val destinations: ImmutableList<JewelleryDestination>,
                             val maxWildernessLevel: Int) {

    GAMES_NECKLACE(items = ImmutableList.of(3853, 3855, 3857, 3859, 3861, 3863, 3865, 3867),
                   emptyId = null,
                   rub = "You rub the necklace...",
                   menuDelay = 2,
                   title = "Where would you like to teleport to?",
                   destinations = ImmutableList.of(
                       JewelleryDestination("Burthorpe Games Rooms.", Position(2207, 4940), 2)
                   ),
                   maxWildernessLevel = 20) {
        override fun landMessage(chargesLeft: Int) = usesLeft("Games Necklace", chargesLeft)
    },
    DUELING_RING(items = ImmutableList.of(2552, 2554, 2556, 2558, 2560, 2562, 2564, 2566),
                 emptyId = null,
                 rub = "You rub the ring...",
                 menuDelay = 2,
                 title = "Where would you like to teleport to?",
                 destinations = ImmutableList.of(
                     JewelleryDestination("Al Kharid Duel Arena.", Position(3315, 3235), 2),
                     JewelleryDestination("Castle Wars Arena.", Position(2440, 3090), 2)
                 ),
                 maxWildernessLevel = 20) {
        override fun landMessage(chargesLeft: Int) = usesLeft("Ring of Dueling", chargesLeft)
    },
    AMULET_OF_GLORY(items = ImmutableList.of(1712, 1710, 1708, 1706),
                    emptyId = 1704,
                    rub = "You rub the amulet...",
                    menuDelay = 0,
                    // The space before the question mark matches the original menu.
                    title = "Where would you like to teleport to ?",
                    destinations = ImmutableList.of(
                        JewelleryDestination("Edgeville.", Position(3087, 3496), 0),
                        JewelleryDestination("Karamja.", Position(2918, 3176), 0),
                        JewelleryDestination("Draynor Village.", Position(3105, 3251), 0),
                        JewelleryDestination("Al Kharid.", Position(3293, 3163), 0)
                    ),
                    maxWildernessLevel = 30) {
        override val nowhere = "You remain where you were."
        override val empty = "The amulet has lost its charge."
        override fun castMessage(chargesLeft: Int) =
            when (chargesLeft) {
                3 -> "Your amulet has three charges left."
                2 -> "Your amulet has two charges left."
                1 -> "Your amulet has one charge left."
                else -> "You use your amulet's last charge."
            }
    };

    /**
     * A teleport destination.
     *
     * @property option The menu option text.
     * @property centre The destination tile.
     * @property radius How far from [centre] the player may land.
     */
    class JewelleryDestination(val option: String, val centre: Position, val radius: Int)

    /**
     * The message sent when "Nowhere." is picked, if any.
     */
    open val nowhere: String? = null

    /**
     * The message sent when the [emptyId] item is rubbed, if any.
     */
    open val empty: String? = null

    /**
     * The message sent as the teleport starts, given the charges that will be left.
     */
    open fun castMessage(chargesLeft: Int): String? = null

    /**
     * The message sent after landing, given the charges that are left.
     */
    open fun landMessage(chargesLeft: Int): String? = null

    /**
     * Builds the charge message sent after a ring or necklace teleport.
     */
    protected fun usesLeft(name: String, chargesLeft: Int) =
        when (chargesLeft) {
            0 -> "Your $name crumbles to dust."
            1 -> "Your $name has 1 use left."
            else -> "Your $name has $chargesLeft uses left."
        }
}
