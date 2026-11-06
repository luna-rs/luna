package game.content.fightPit

import api.combat.death.DeathHookHandler
import api.predef.*
import api.predef.ext.*
import game.player.Animations
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.mob.block.Animation.AnimationPriority

on(ServerLaunchEvent::class) { FightPitRound.launch() }

object1(FightPit.NORTH_DOOR) { FightPitDoors.pass(plr, gameObject) }

object1(FightPit.SOUTH_DOOR) { FightPitDoors.pass(plr, gameObject) }

object1(FightPitOrb.ORB) { (plr.controllers.primary as? FightPitController)?.lookInto() }

for (id in FightPitOrb.VIEWS.keys) {
    button(id) { (plr.controllers.primary as? FightPitController)?.view(id) }
}

button(FightPitOrb.STOP_VIEWING) { (plr.controllers.primary as? FightPitController)?.stopViewing() }

// Nobody comes back into the Fight Pit from a save, even one made before a crash. A champion who logged out is
// paid now.
on(LoginEvent::class) {
    if (FightPit.inside(plr.position)) plr.move(FightPit.OUTSIDE)
    FightPitRound.payOut(plr)
}

// Dying anywhere in the Fight Pit is safe: the player keeps everything and is put back in the waiting room.
DeathHookHandler.addPlayerHook(FightPitController::class.java) {
    preDeath {
        victim.animation(Animations.DEATH, AnimationPriority.IMMUTABLE)
        FightPitRound.died(victim)
    }
    death { }
    postDeath { FightPitRound.eject(victim) }
}
