package game.skill.agility

import api.predef.*

TrollheimMountainRocks.IDS.forEach { id ->
    object1(id) { TrollheimMountainRocks.climb(plr, gameObject) }
}
