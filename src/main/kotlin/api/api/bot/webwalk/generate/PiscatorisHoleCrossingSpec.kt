package api.bot.webwalk.generate

import api.bot.webwalk.walk.crossing.AgilityShortcutCrossing
import game.content.piscatoris.PiscatorisHole

/**
 * Both native mouths of the cliff tunnel, each clicked from the side where it is visible.
 *
 * @author lare96
 */
object PiscatorisHoleCrossingSpec {

    val SPEC = CrossingSpec(AgilityShortcutCrossing.ID, setOf(PiscatorisHole.ID), cost = 8, bidirectional = false,
        placements = listOf(
            CrossingPlacement(PiscatorisHole.ID, PiscatorisHole.SOUTH_OBJECT, PiscatorisHole.SOUTH, PiscatorisHole.NORTH),
            CrossingPlacement(PiscatorisHole.ID, PiscatorisHole.NORTH_OBJECT, PiscatorisHole.NORTH, PiscatorisHole.SOUTH)))
}
