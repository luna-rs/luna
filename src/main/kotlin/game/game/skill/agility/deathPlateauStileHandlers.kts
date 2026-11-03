package game.skill.agility

import api.predef.*

object1(DeathPlateauStile.ID) {
    DeathPlateauStile.action(plr, gameObject)?.let { plr.submitAction(it) }
}
