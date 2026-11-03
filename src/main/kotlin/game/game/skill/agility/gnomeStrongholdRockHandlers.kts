package game.skill.agility

import api.predef.*

for (passage in GnomeStrongholdRocks.PASSAGES) {
    object1(passage.id) { GnomeStrongholdRocks.climb(plr, gameObject) }
}
