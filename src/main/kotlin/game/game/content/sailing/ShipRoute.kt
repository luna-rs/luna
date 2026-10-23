package game.content.sailing

import io.luna.game.model.Position
import io.luna.game.model.mob.Npc

/**
 * A ship journey from one port to another.
 *
 * @property place The name of the port the ship arrives at.
 * @property destination Where the player lands, on the deck of the ship that arrives.
 * @property journey The route drawn on the ship journey map.
 * @property ticks How long the journey takes.
 * @property departure A tile on the dock that the ship leaves from, by its crew.
 * @property crew The NPCs that are talked to for the journey.
 * @property fare What the journey costs, in coins.
 * @property search What the crew searches for before letting a player board.
 * @author TheLining
 */
enum class ShipRoute(val place: String,
                     val destination: Position,
                     val journey: Int,
                     val ticks: Int,
                     val departure: Position,
                     val crew: Set<Int>,
                     val fare: Int,
                     val search: ShipSearch) {
    PORT_SARIM_TO_KARAMJA(place = "Karamja",
                          destination = Position(2956, 3143, 1),
                          journey = 5,
                          ticks = 8,
                          departure = Position(3028, 3218),
                          crew = Sailing.PORT_SARIM_SAILORS,
                          fare = Sailing.FARE,
                          search = ShipSearch.NONE),
    KARAMJA_TO_PORT_SARIM(place = "Port Sarim",
                          destination = Position(3032, 3217, 1),
                          journey = 6,
                          ticks = 8,
                          departure = Position(2953, 3146),
                          crew = setOf(Sailing.CUSTOMS_OFFICER),
                          fare = Sailing.FARE,
                          search = ShipSearch.RUM),
    ARDOUGNE_TO_BRIMHAVEN(place = "Brimhaven",
                          destination = Position(2775, 3234, 1),
                          journey = 7,
                          ticks = 8,
                          departure = Position(2680, 3275),
                          crew = setOf(Sailing.CAPTAIN_BARNABY),
                          fare = Sailing.FARE,
                          search = ShipSearch.NONE),
    BRIMHAVEN_TO_ARDOUGNE(place = "Ardougne",
                          destination = Position(2683, 3268, 1),
                          journey = 8,
                          ticks = 8,
                          departure = Position(2771, 3228),
                          crew = setOf(Sailing.CUSTOMS_OFFICER),
                          fare = Sailing.FARE,
                          search = ShipSearch.RUM),
    PORT_SARIM_TO_ENTRANA(place = "Entrana",
                          destination = Position(2834, 3331, 1),
                          journey = 1,
                          ticks = 14,
                          departure = Position(3047, 3235),
                          crew = Sailing.PORT_SARIM_MONKS,
                          fare = 0,
                          search = ShipSearch.ENTRANA),
    ENTRANA_TO_PORT_SARIM(place = "Port Sarim",
                          destination = Position(3048, 3231, 1),
                          journey = 2,
                          ticks = 15,
                          departure = Position(2834, 3334),
                          crew = Sailing.ENTRANA_MONKS,
                          fare = 0,
                          search = ShipSearch.NONE);

    companion object {

        /**
         * Finds the route that [npc] crews from where it stands: of the routes it is crew of, the one whose [departure] is
         * nearest. Returns `null` if it crews none.
         */
        fun departingNear(npc: Npc): ShipRoute? = departingNear(npc.id, npc.position)

        /**
         * Finds the route that an NPC with [id] crews from [position]: of the routes it is crew of, the one whose
         * [departure] is nearest. Returns `null` if it crews none.
         */
        fun departingNear(id: Int, position: Position): ShipRoute? =
            entries.filter { id in it.crew }.minByOrNull { it.departure.computeLongestDistance(position) }
    }
}
