package api.bot.webwalk.generate

import api.bot.webwalk.walk.crossing.ConditionalDoorCrossing
import game.obj.doors.TrollArenaGates

/**
 * Native three-tick arena gate passages on Troll Stronghold's mountain path.
 * Quest admission remains deferred with the native handler until the Dad encounter is implemented.
 *
 * @author lare96
 */
object TrollArenaGateSpec {
    val SPEC = CrossingSpec(ConditionalDoorCrossing.ID, TrollArenaGates.GATES.map { it.id }.toSet(), cost = 4)
}
