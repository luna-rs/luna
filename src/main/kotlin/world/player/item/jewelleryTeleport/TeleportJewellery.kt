package world.player.item.jewelleryTeleport

import com.google.common.collect.*
import io.luna.game.model.*

/**
 * An enum representing jewellery that can be used to teleport.
 */
enum class TeleportJewellery(val itemIDs: IntArray,
                             val destinations: ImmutableList<Pair<Position, String>>,
                             val rubMessage : String,
                             val lastChargeMessage : String,
                             val disappear: Boolean) {

    GAMES_NECKLACE(
        intArrayOf(3853, 3855, 3857, 3859, 3861, 3863, 3865, 3867),
        ImmutableList.of(
            Pair(Position(2897, 3554), "Burthorpe."),
            Pair(Position(2536, 3565), "Barbarian Outpost.")),
        "You rub the necklace...",
        "Your Games necklace crumbles to dust.",
        true
    ),
    DUELING_RING(
        intArrayOf(2552, 2554, 2556, 2558, 2560, 2562, 2564, 2566),
        ImmutableList.of(
            Pair(Position(3316, 3237), "Al Kharid Duel Arena."),
            Pair(Position(2441, 3088), "Castle Wars Arena.")),
        "You rub the ring...",
        "Your Ring of Dueling crumbles to dust.",
        true
    ),
    AMULET_OF_GLORY(
        intArrayOf(1712, 1710, 1708, 1706, 1704),
        ImmutableList.of(
            Pair(Position(3087, 3496), "Edgeville."),
            Pair(Position(2918, 3176), "Karamja."),
            Pair(Position(3105, 3251), "Draynor Village."),
            Pair(Position(3293, 3163), "Al Kharid.")),
        "You rub the amulet...",
        "Your Amulet of Glory needs to be recharged.",
        false
    ),
    ;
}