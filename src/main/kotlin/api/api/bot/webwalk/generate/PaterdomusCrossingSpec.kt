package api.bot.webwalk.generate

import api.bot.webwalk.walk.crossing.PaterdomusGateCrossing
import game.obj.entrances.PaterdomusGates

/**
 * The two real basement metal gates on the mainland-to-Morytania path.
 * Their quest checks follow the same deferred-quest policy as the native Paterdomus entrance scripts.
 *
 * @author lare96
 */
object PaterdomusCrossingSpec {
    val GATES = CrossingSpec(PaterdomusGateCrossing.ID, PaterdomusGates.IDS, cost = 4)
}
