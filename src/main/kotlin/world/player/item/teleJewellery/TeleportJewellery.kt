package world.player.item.teleJewellery

import io.luna.game.model.*

/**
 * An enum representing jewellery that can be used to teleport.
 */
enum class TeleportJewellery(val itemIDs: IntArray,
    val destinations: Array<Destination>,
    val rubMessage : String,
    val crumbleMessage : String,
    val disappear: Boolean) {

    GAMES_NECKLACE(
        intArrayOf(3853, 3855, 3857, 3859, 3861, 3863, 3865, 3867),
        arrayOf(
            Destination(Position(2898, 3546), "Burthorpe."),
            Destination(Position(2536, 3565), "Barbarian Outpost.")
        ),
        "You rub the necklace...",
        "Your Games necklace crumbles to dust.",
        true
    ),
    DUELING_RING(
        intArrayOf(2552, 2554, 2556, 2558, 2560, 2562, 2564, 2566),
        arrayOf(
            Destination(Position(3316, 3235), "Al Kharid Duel Arena."),
            Destination(Position(2441, 3089), "Castle Wars Arena.")
        ),
        "You rub the ring...",
        "Your Ring of Dueling crumbles to dust.",
        true
    ),
    AMULET_OF_GLORY(
        intArrayOf(1712, 1710, 1708, 1706, 1704),
        arrayOf(
            Destination(Position(3087, 3504), "Edgeville."),
            Destination(Position(2912, 3169), "Karamja."),
            Destination(Position(3105, 3264), "Draynor Village."),
            Destination(Position(3290, 3181), "Al Kharid.")
        ),
        "You rub the amulet...",
        "Your Amulet of Glory needs to be recharged.",
        false
    ),
    ;
}

data class Destination(val destination: Position, val destinationName: String)