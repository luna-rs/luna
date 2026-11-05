package game.content.fightPit

import api.predef.*
import io.luna.game.event.impl.LoginEvent

object1(FightPit.NORTH_DOOR) { FightPitDoors.pass(plr, gameObject) }

object1(FightPit.SOUTH_DOOR) { FightPitDoors.pass(plr, gameObject) }

// Nobody comes back into the Fight Pit from a save, even one made before a crash.
on(LoginEvent::class) {
    if (FightPit.inside(plr.position)) plr.move(FightPit.OUTSIDE)
}
