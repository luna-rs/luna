package game.skill.agility.course

import api.predef.*
import game.skill.agility.*
import game.skill.agility.Agility.course
import game.skill.agility.AgilityCourse.BARBARIAN_OUTPOST
import game.skill.agility.AgilityObstacle.*
import io.luna.game.model.Position

course(BARBARIAN_OUTPOST) {
    obstacle(2282, BARBARIAN_ROPESWING) { rope ->
        AgilityAction(this, listOf(AgilityStep(Position(2551, 3549), 3, animation = 751)),
            "You swing across the pit.", { rope.animate() })
    }
    obstacle(2294, BARBARIAN_LOG_BALANCE) {
        movementAction(this, Position(2541, 3546), 762, "You walk across the log balance.")
    }
    obstacle(2284, BARBARIAN_OBSTACLE_NET) {
        climbAction(this, Position(2537, 3546, 1), "You climb the obstacle net.")
    }
    obstacle(2302, BARBARIAN_BALANCING_LEDGE) {
        AgilityAction(this, listOf(
            AgilityStep(Position(2532, 3547, 1), 4, walkingAnimation = 756),
            AgilityStep(Position(2532, 3546, 1), 1, jump = true)
        ), "You walk across the balancing ledge.")
    }
    obstacle(3205, BARBARIAN_LADDER) {
        climbAction(this, Position(2532, 3546), "You climb down the ladder.")
    }
    obstacle(1747, BARBARIAN_LADDER) {
        climbAction(this, Position(2532, 3546, 1), "You climb up the ladder.")
    }
    obstacle(1948, BARBARIAN_CRUMBLING_WALL, checkpoint = { it.position == Position(2542, 3553) }) { wall ->
        AgilityAction(this, listOf(AgilityStep(wall.position.translate(1, 0), 3, animation = 840)),
            "You climb over the wall.")
    }
}

// The entrance pipe grants XP but is not one of the lap checkpoints. Leaving is always allowed.
object1(2287) {
    AgilityEntrances.barbarianPipe(plr)?.let { plr.submitAction(it) }
}
