package world.player.item.jewelleryTeleport

import com.google.common.collect.*
import io.luna.game.model.*

/**
 * An enum representing jewellery that can be used to teleport.
 *
 * @param itemIDs               ID of item used to teleport in order of descending charges
 * @param lastChargeMessage     chatbox message sent when the last charge is used
 * @param disappear             true if the item disappears on last charge use
 */
enum class TeleportJewellery(val itemIDs: IntArray,
                             val destinations: ImmutableList<Pair<Position, String>>,
                             val rubMessage : String,
                             val lastChargeMessage : String,
                             val disappear: Boolean) {

    GAMES_NECKLACE(
        intArrayOf(3853, 3855, 3857, 3859, 3861, 3863, 3865, 3867),
        ImmutableList.of(
            Position(2897, 3554) to "Burthorpe.",
            Position(2536, 3565) to "Barbarian Outpost."),
        "You rub the necklace...",
        "Your Games necklace crumbles to dust.",
        true
    ),
    DUELING_RING(
        intArrayOf(2552, 2554, 2556, 2558, 2560, 2562, 2564, 2566),
        ImmutableList.of(
            Position(3316, 3237) to "Al Kharid Duel Arena.",
            Position(2441, 3088) to "Castle Wars Arena."),
        "You rub the ring...",
        "Your Ring of Dueling crumbles to dust.",
        true
    ),
    AMULET_OF_GLORY(
        intArrayOf(1712, 1710, 1708, 1706, 1704),
        ImmutableList.of(
            Position(3087, 3496) to "Edgeville.",
            Position(2918, 3176) to "Karamja.",
            Position(3105, 3251) to "Draynor Village.",
            Position(3293, 3163) to "Al Kharid."),
        "You rub the amulet...",
        "Your Amulet of Glory needs to be recharged.",
        false
    ),
    ;
}