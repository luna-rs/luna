package game.content.sailing

import io.luna.game.model.Position

/**
 * A ship journey from one port to another.
 *
 * @property place The name of the port the ship arrives at.
 * @property destination Where the player lands, on the deck of the ship that arrives.
 * @property journey The route drawn on the ship journey map.
 * @property ticks How long the journey takes.
 * @author TheLining
 */
enum class ShipRoute(val place: String, val destination: Position, val journey: Int, val ticks: Int) {
    PORT_SARIM_TO_KARAMJA(place = "Karamja",
                          destination = Position(2956, 3143, 1),
                          journey = 5,
                          ticks = 8),
    KARAMJA_TO_PORT_SARIM(place = "Port Sarim",
                          destination = Position(3032, 3217, 1),
                          journey = 6,
                          ticks = 8),
    ARDOUGNE_TO_BRIMHAVEN(place = "Brimhaven",
                          destination = Position(2775, 3234, 1),
                          journey = 7,
                          ticks = 8),
    BRIMHAVEN_TO_ARDOUGNE(place = "Ardougne",
                          destination = Position(2683, 3268, 1),
                          journey = 8,
                          ticks = 8),
    PORT_SARIM_TO_ENTRANA(place = "Entrana",
                          destination = Position(2834, 3331, 1),
                          journey = 1,
                          ticks = 14),
    ENTRANA_TO_PORT_SARIM(place = "Port Sarim",
                          destination = Position(3048, 3231, 1),
                          journey = 2,
                          ticks = 15)
}
