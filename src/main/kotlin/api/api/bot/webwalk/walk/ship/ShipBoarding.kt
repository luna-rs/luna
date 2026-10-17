package api.bot.webwalk.walk.ship

import game.content.sailing.Sailing
import game.content.sailing.ShipRoute

/**
 * How a bot boards the ship of a [ShipRoute]: the dialogue options that it chooses, in order, after talking to the crew.
 * Only the web-walker needs this, so it is kept here rather than on the route. The options are the ones that the sailing
 * scripts show (see `sailors.kts` and `monks.kts`), so a change to the path through a script's dialogue has to be made here
 * too.
 *
 * @property route The route.
 * @property options The dialogue options that are chosen, in order, to board.
 *
 * @author Hydrozoa
 */
enum class ShipBoarding(val route: ShipRoute, val options: List<String>) {
    PORT_SARIM_TO_KARAMJA(ShipRoute.PORT_SARIM_TO_KARAMJA, listOf(Sailing.YES_PLEASE)),
    KARAMJA_TO_PORT_SARIM(ShipRoute.KARAMJA_TO_PORT_SARIM, listOf(Sailing.CAN_I_JOURNEY, Sailing.SEARCH_AWAY, Sailing.OK)),
    ARDOUGNE_TO_BRIMHAVEN(ShipRoute.ARDOUGNE_TO_BRIMHAVEN, listOf(Sailing.YES_PLEASE)),
    BRIMHAVEN_TO_ARDOUGNE(ShipRoute.BRIMHAVEN_TO_ARDOUGNE, listOf(Sailing.CAN_I_JOURNEY, Sailing.SEARCH_AWAY, Sailing.OK)),
    PORT_SARIM_TO_ENTRANA(ShipRoute.PORT_SARIM_TO_ENTRANA, listOf(Sailing.READY_TO_GO)),
    ENTRANA_TO_PORT_SARIM(ShipRoute.ENTRANA_TO_PORT_SARIM, listOf(Sailing.READY_TO_LEAVE));

    companion object {

        /**
         * The boarding of each route.
         */
        private val byRoute: Map<ShipRoute, ShipBoarding> = entries.associateBy { it.route }

        /**
         * @return How the ship of [route] is boarded, or `null` if the web-walker doesn't know.
         */
        fun of(route: ShipRoute): ShipBoarding? = byRoute[route]
    }
}
