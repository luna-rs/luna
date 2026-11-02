package game.skill.agility.skullball

import api.predef.*
import api.predef.ext.*
import game.skill.agility.Agility
import game.skill.agility.skullball.Skullball.BALL
import game.skill.agility.skullball.Skullball.BOSS
import game.skill.agility.skullball.Skullball.GRRR
import game.skill.agility.skullball.Skullball.Shot
import game.skill.agility.skullball.Skullball.TRAINER
import io.luna.game.event.impl.LogoutEvent
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.interact.InteractionPolicy

/**
 * The Skullball Boss explains the course.
 */
fun instructions(plr: Player) {
    plr.newDialogue()
        .player("What are the instructions for using the skullball", "course?")
        .npc(BOSS,
            "The skullball comes out of one of these four",
            "spawnholes. Just kick the ball through the middle of",
            "each goal, through the skeleton's feet.")
        .npc(BOSS, "There are 10 goals, which you must complete in", "order, and one final goal.")
        .npc(BOSS,
            "An arrow will point to your ball, just in case lots",
            "of people are using the course at the same time as",
            "yourself.")
        // "agillity" is in Jagex's text.
        .npc(BOSS,
            "The better your time, the more agillity XP you will",
            "be awarded. The timer starts when you score your",
            "first goal.")
        .open()
}

/**
 * The Skullball Boss hands out a ball, if [plr]'s Agility is up to it.
 */
fun takeBall(plr: Player) {
    if (Agility.checkLevel(plr, Skullball.LEVEL)) {
        plr.newDialogue().player("I think I'll give the Skullball Course a go.").then { Skullball.start(it) }.open()
    }
}

npc1(BOSS) {
    when {
        !Skullball.passesAsWerewolf(plr) -> plr.newDialogue().npc(BOSS, GRRR).open()
        Skullball.playing(plr) -> plr.newDialogue().options(
            "What are the instructions for using the skullball course?", { instructions(it) },
            "I seem to have lost my ball - can I have another one?", {
                if (Agility.checkLevel(it, Skullball.LEVEL)) {
                    it.newDialogue()
                        .player("I seem to have lost my ball - can I have another", "one?")
                        .npc(BOSS,
                            "No problem, here's another one. You'll have to start",
                            "from the beginning again, but the timer will be",
                            "restarted too.")
                        .then { p -> Skullball.start(p) }
                        .open()
                }
            },
            "I give up, I can't do it - take my ball away.", {
                it.newDialogue()
                    .player("I give up, I can't do it - take my ball away.")
                    .npc(BOSS, "Oh dear, such a defeatist.")
                    .then { p -> Skullball.giveUp(p) }
                    .open()
            }).open()

        else -> plr.newDialogue().options(
            "I would like to do the skullball course.", { takeBall(it) },
            "What are the instructions for the skullball course?", { instructions(it) }).open()
    }
}

npc1(TRAINER) {
    if (Skullball.passesAsWerewolf(plr)) {
        plr.newDialogue()
            .player("What is this place?")
            .npc(TRAINER, "This is the Skullball Course.")
            .npc(TRAINER, "Go and talk to the boss at the beginning of the", "course if you'd like a go.")
            .open()
    } else {
        plr.newDialogue().npc(TRAINER, GRRR).open()
    }
}

// The ball's Tap, Kick, Shoot and Show-Goal options are its first, third, fourth and fifth.
npc1(BALL, false) { Skullball.kick(plr, targetNpc, Shot.TAP) }
npc2(BALL, false) { Skullball.kick(plr, targetNpc, Shot.KICK) }
npc3(BALL, false) { Skullball.kick(plr, targetNpc, Shot.SHOOT) }
npc4(BALL, InteractionPolicy.UNSPECIFIED_BIF, false) { Skullball.showGoal(plr, targetNpc) }

on(LogoutEvent::class) { Skullball.logout(plr) }

on(ServerLaunchEvent::class) {
    world.schedule(1) { Skullball.process() }
}
