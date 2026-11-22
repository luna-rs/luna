package game.content.canoes

import api.predef.*
import io.luna.game.event.impl.LoginEvent

for (station in CanoeStation.values()) {
    button(station.mapButton) { Canoes.pickDestination(plr, station) }
    val tree = station.tree ?: continue
    object1(tree.id) { Canoes.click(plr, station, gameObject) }
}

for (type in CanoeType.values()) {
    button(type.button) { Canoes.pickCanoe(plr, type) }
}

on(LoginEvent::class) { Canoes.reset(plr) }
