package game.obj.entrances

import game.obj.entrances.EntranceLanding.Companion.row
import game.obj.entrances.EntranceLanding.Companion.shift
import game.obj.entrances.EntranceLanding.Companion.tile
import io.luna.game.model.Direction
import io.luna.game.model.Position

/**
 * Cave and dungeon entrances and exits that take the player somewhere when clicked. Entrances that need an item, a
 * fee, a skill level or a choice are handled in their own scripts instead.
 *
 * @param id The object.
 * @param landing Where it takes the player.
 * @param message The message sent when it's used, if any.
 * @param delay How many ticks after [message] the player moves.
 * @param arrival The message sent once the player arrives, if any.
 * @param animation The animation played when it's used, if any.
 * @param arriveDelay Whether to wait a tick first if the player has only just walked up to it.
 * @param face The direction to face after arriving, if any.
 * @param position Where the object is, for objects that lead somewhere different from each spot. `null` if it leads
 * to [landing] from everywhere.
 *
 * @author TheLining
 */
enum class Entrance(val id: Int,
                    val landing: EntranceLanding,
                    val message: String? = null,
                    val delay: Int = 0,
                    val arrival: String? = null,
                    val animation: Int? = null,
                    val arriveDelay: Boolean = false,
                    val face: Direction? = null,
                    val position: Position? = null) {

    // Karamja Volcano.
    KARAMJA_VOLCANO_DOWN(id = 492, landing = shift(0, 6400), message = "You climb down through the pot hole.",
                         arriveDelay = true),
    KARAMJA_VOLCANO_UP(id = 1764, landing = tile(2856, 3167), message = "You climb up the hanging rope...",
                       delay = 3, arrival = "You appear on the volcano rim."),

    // TzHaar City, from the Karamja Volcano dungeon.
    TZHAAR_CITY_IN(id = 9358, landing = tile(2480, 5174), message = "You walk through the passage...", delay = 1),
    TZHAAR_CITY_OUT(id = 9359, landing = tile(2862, 9572), message = "You walk through the passage...", delay = 1),

    // Fremennik Slayer Dungeon.
    FREMENNIK_SLAYER_DUNGEON_IN(id = 4499, landing = tile(2808, 10002)),
    FREMENNIK_SLAYER_DUNGEON_OUT(id = 4500, landing = tile(2796, 3615)),

    // Kalphite Lair and the Kalphite Queen's chamber, once a rope is tied to the entrance.
    KALPHITE_LAIR_IN(id = 3828, landing = tile(3483, 9510, 2)),
    KALPHITE_LAIR_OUT(id = 3829, landing = tile(3226, 3108)),
    KALPHITE_QUEEN_IN(id = 3831, landing = tile(3508, 9493)),
    KALPHITE_QUEEN_OUT(id = 3832, landing = tile(3508, 9497, 2)),

    // Lumbridge Swamp Caves: the rope back up, and the tunnels to the Tears of Guthix cave.
    // TODO Once darkness is added, players without a light source get bitten by insects in here.
    LUMBRIDGE_SWAMP_CAVES_UP(id = 5946, landing = shift(0, -6400), delay = 1, animation = 828),
    TEARS_OF_GUTHIX_TUNNEL_IN(id = 6659, landing = tile(3219, 9532, 2), delay = 1),
    TEARS_OF_GUTHIX_TUNNEL_OUT(id = 6658, landing = tile(3226, 9542), delay = 1),

    // The Asgarnian Ice Dungeon's skeletal wyvern cave.
    WYVERN_CAVE_IN(id = 10596, landing = row(9555)),
    WYVERN_CAVE_OUT(id = 10595, landing = row(9562)),

    // Waterbirth Island Dungeon, and the cave back down from Askeladden's hill.
    WATERBIRTH_DUNGEON_IN(id = 8929, landing = tile(2442, 10146)),
    WATERBIRTH_HILL_CAVE(id = 8930, landing = tile(2545, 10143)),

    // Burthorpe, out of the Rogues' Den to the pub.
    ROGUES_DEN_OUT(id = 7258, landing = tile(2906, 3537), message = "You walk through the passage...", delay = 1),

    // Entrana dungeon's magic door, out to the Wilderness.
    ENTRANA_DUNGEON_OUT(id = 2407, landing = tile(3250, 3772), message = "You feel the world around you dissolve...",
                        face = Direction.NORTH),

    // East Ardougne, the cave into the sewers (Hazeel Cult).
    ARDOUGNE_SEWERS_IN(id = 2852, landing = tile(2570, 9682), message = "You enter the cave."),

    // The tunnels between Rellekka, Trollweiss Mountain and the cave outside Keldagrim.
    RELLEKKA_TUNNEL_IN(id = 5008, landing = tile(2773, 10162)),
    RELLEKKA_TUNNEL_OUT(id = 5014, landing = tile(2730, 3713)),
    TROLLWEISS_TUNNEL_IN(id = 5012, landing = tile(2799, 10134)),
    TROLLWEISS_TUNNEL_OUT(id = 5013, landing = tile(2797, 3719)),
    KELDAGRIM_CAVE_IN(id = 5973, landing = tile(2838, 10124)),
    KELDAGRIM_CAVE_OUT(id = 5998, landing = tile(2778, 10161));

    companion object {

        /**
         * The entrances by object id.
         */
        val BY_ID = values().groupBy { it.id }

        /**
         * Returns the entrance for the object [id] on [position], or `null` if there isn't one.
         */
        fun forObject(id: Int, position: Position) =
            BY_ID[id]?.let { list ->
                list.firstOrNull { it.position == position } ?: list.firstOrNull { it.position == null }
            }
    }
}
