package game.skill.agility.course

import api.predef.*
import game.player.Sound
import game.skill.agility.*
import game.skill.agility.Agility.course
import game.skill.agility.AgilityCourse.BARBARIAN_OUTPOST
import game.skill.agility.AgilityObstacle.*
import io.luna.game.model.Position

course(BARBARIAN_OUTPOST) {
    obstacle(2282, BARBARIAN_ROPESWING) { rope ->
        // The player stands on the start tile for two ticks before taking the rope.
        AgilityAction(this, listOf(
            AgilityStep(position, 2, jump = true),
            AgilityStep(Position(2551, 3549), 2, animation = 751, message = "You skillfully swing across.",
                moveStart = 20, moveEnd = 50, sounds = listOf(AgilitySound(Sound.SWING_ACROSS, tick = 1)),
                begin = { rope.animate(Agility.ROPE_SWING_ANIMATION) })
        ), null)
    }
    obstacle(2294, BARBARIAN_LOG_BALANCE) {
        movementAction(this, Position(2541, 3546), 762, "You walk carefully across the slippery log...",
            listOf(AgilitySound(Sound.LOG_BALANCE, tick = 2, loops = 4),
                AgilitySound(Sound.LOG_BALANCE, tick = 6, loops = 2)),
            endMessage = "...You make it safely to the other side.")
    }
    obstacle(2284, BARBARIAN_OBSTACLE_NET) {
        climbAction(this, Position(2537, 3546, 1), "You climb the netting...")
    }
    obstacle(2302, BARBARIAN_BALANCING_LEDGE) {
        AgilityAction(this, listOf(
            AgilityStep(Position(2532, 3547, 1), 4, walkingAnimation = 756,
                sounds = listOf(AgilitySound(Sound.BALANCING_LEDGE, tick = 1, loops = 2, delayCycles = 10),
                    AgilitySound(Sound.BALANCING_LEDGE, tick = 2))),
            AgilityStep(Position(2532, 3546, 1), 1, jump = true,
                sounds = listOf(AgilitySound(Sound.BALANCING_LEDGE, tick = 1)))
        ), "You put your foot on the ledge and try to edge across.", endMessage = "You skillfully edge across the gap.")
    }
    obstacle(3205, BARBARIAN_LADDER) {
        climbAction(this, Position(2532, 3546), null)
    }
    obstacle(1747, BARBARIAN_LADDER) {
        climbAction(this, Position(2532, 3546, 1), "You climb up the ladder.")
    }
    obstacle(1948, BARBARIAN_CRUMBLING_WALL, checkpoint = { it.position == Position(2542, 3553) }) { wall ->
        AgilityAction(this, listOf(AgilityStep(wall.position.translate(1, 0), 3, animation = 840,
            sounds = listOf(AgilitySound(Sound.CLIMB_WALL, delayCycles = 40)))),
            "You climb the low wall...")
    }
}

// The entrance pipe grants XP but is not one of the lap checkpoints. Leaving is always allowed.
object1(2287) {
    AgilityEntrances.barbarianPipe(plr)?.let { plr.submitAction(it) }
}
