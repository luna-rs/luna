package game.skill.agility

import api.predef.*

listOf(TrollSlopeRocks.TOP, TrollSlopeRocks.BOTTOM).forEach { id ->
    object1(id) { TrollSlopeRocks.climb(plr, gameObject) }
}
