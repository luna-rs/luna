package world.player.item.jewelleryTeleport

import com.google.common.collect.ImmutableList
import io.luna.game.model.Position
import io.luna.game.model.item.Item

/**
 * An enum representing jewellery that can be used to teleport.
 */
enum class TeleportJewellery(val items: ImmutableList<Int>,
                             val destinations: ImmutableList<Pair<String, Position>>,
                             val rub: String,
                             val lastCharge: String,
                             val crumbles: Boolean) {

    GAMES_NECKLACE(items = ImmutableList.of(3853, 3855, 3857, 3859, 3861, 3863, 3865, 3867),
                   destinations = ImmutableList.of(
                       "Burthorpe" to Position(2897, 3554),
                       "Barbarian Outpost" to Position(2536, 3565)
                   ),
                   rub = "You rub the necklace...",
                   lastCharge = "Your Games necklace crumbles to dust.",
                   crumbles = true),
    DUELING_RING(items = ImmutableList.of(2552, 2554, 2556, 2558, 2560, 2562, 2564, 2566),
                 ImmutableList.of(
                     "Al Kharid Duel Arena" to Position(3316, 3237),
                     "Castle Wars Arena" to Position(2441, 3088)
                 ),
                 rub = "You rub the ring...",
                 lastCharge = "Your Ring of Dueling crumbles to dust.",
                 crumbles = true),
    AMULET_OF_GLORY(items = ImmutableList.of(1712, 1710, 1708, 1706, 1704),
                    destinations = ImmutableList.of(
                        "Edgeville" to Position(3087, 3496),
                        "Karamja" to Position(2918, 3176),
                        "Draynor Village" to Position(3105, 3251),
                        "Al Kharid" to Position(3293, 3163)
                    ),
                    rub = "You rub the amulet...",
                    lastCharge = "Your Amulet of Glory needs to be recharged.",
                    crumbles = false);

    companion object {

        /**
         * An immutable copy of all this enum's values.
         */
        val VALUES = ImmutableList.copyOf(values())
        val CHARGED_AMULETS_OF_GLORY = listOf(Item(1712), Item(1710), Item(1708), Item(1706))
    }
}