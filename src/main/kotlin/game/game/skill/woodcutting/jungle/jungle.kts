package game.skill.woodcutting.jungle

import api.predef.*

KharaziJungle.STUMPS.keys.forEach { id ->
    object1(id) { KharaziJungle.chop(plr, gameObject) }
}
