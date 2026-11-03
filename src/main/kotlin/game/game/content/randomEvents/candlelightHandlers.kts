package game.content.randomEvents

import api.predef.*

// Register source permanent puzzle attributes before player files are loaded.
val candlelight = CandlelightRandomEvent

npc1(CandlelightRandomEvent.PETE) {
    (RandomEvents.encounter(plr) as? CandlelightRandomEvent)?.talk(plr)
}

for (id in listOf(CandlelightRandomEvent.UP, CandlelightRandomEvent.DOWN, CandlelightRandomEvent.LEFT,
    CandlelightRandomEvent.RIGHT, CandlelightRandomEvent.LIGHT, CandlelightRandomEvent.CLOSE)) {
    button(id) { (RandomEvents.encounter(plr) as? CandlelightRandomEvent)?.button(id) }
}

object1(11381) { plr.sendMessage("You can't open this door.") }
