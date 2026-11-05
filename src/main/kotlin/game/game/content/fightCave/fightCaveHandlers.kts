package game.content.fightCave

import api.combat.death.DeathHookHandler
import api.predef.*
import api.predef.ext.*
import game.content.fightCave.FightCave.fightCaveWave
import game.player.Animations
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.mob.block.Animation.AnimationPriority

object1(FightCave.ENTRANCE) {
    FightCave.enter(plr)
}

object1(FightCave.EXIT) {
    FightCave.leave(plr)
}

// A run saved between waves picks up again in a new cave.
on(LoginEvent::class) {
    if (plr.fightCaveWave > 0) FightCave.enter(plr, plr.fightCaveWave)
}

// Dying in the cave is safe: the player keeps everything and comes out to the city.
DeathHookHandler.addPlayerHook(FightCaveController::class.java) {
    preDeath { victim.animation(Animations.DEATH, AnimationPriority.IMMUTABLE) }
    death {
        victim.sendMessage("You have been defeated!")
        (victim.controllers.primary as? FightCaveController)?.dying()
    }
    postDeath { (victim.controllers.primary as? FightCaveController)?.defeated() }
}
