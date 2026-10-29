package game.skill.agility

import api.predef.*

object1(AgilityEntrances.TAVERLEY_PIPE) {
    AgilityEntrances.taverleyPipe(plr)?.let { plr.submitAction(it) }
}

object1(AgilityEntrances.TAVERLEY_FLOOR) {
    AgilityEntrances.taverleyStrangeFloor(plr)?.let { plr.submitAction(it) }
}
