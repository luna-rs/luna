package game.skill.agility

import api.predef.*

object1(Stile.ID) {
    Stile.action(plr, gameObject)?.let { plr.submitAction(it) }
}
