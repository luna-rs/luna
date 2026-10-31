package game.skill.agility.course

import api.predef.*
import game.player.Sound
import game.skill.agility.*
import game.skill.agility.Agility.course
import game.skill.agility.AgilityCourse.WILDERNESS
import game.skill.agility.AgilityObstacle.*
import io.luna.game.model.Position

course(WILDERNESS) {
    obstacle(2288, WILDERNESS_OBSTACLE_PIPE) {
        AgilityAction(this, listOf(
            AgilityStep(Position(3004, 3940), 4, animation = 749,
                sounds = listOf(AgilitySound(Sound.SQUEEZE_IN, tick = 4))),
            AgilityStep(Position(3004, 3947), 1, jump = true),
            AgilityStep(Position(3004, 3950), 4, animation = 749,
                sounds = listOf(AgilitySound(Sound.SQUEEZE_IN, tick = 4)))
        ), "You climb through the obstacle pipe.")
    }
    obstacle(2283, WILDERNESS_ROPESWING) { rope ->
        AgilityAction(this, listOf(AgilityStep(Position(3005, 3958), 3, animation = 751,
            sounds = listOf(AgilitySound(Sound.SWING_ACROSS, tick = 1)))),
            "You swing across the pit.", { rope.animate() })
    }
    obstacle(2311, WILDERNESS_STEPPING_STONE) {
        AgilityAction(this, (3001 downTo 2996).map {
            AgilityStep(Position(it, 3960), 2, animation = 741,
                sounds = listOf(AgilitySound(Sound.JUMP_NO_LAND)))
        }, "You carefully start crossing the stepping stones.")
    }
    obstacle(2297, WILDERNESS_LOG_BALANCE) {
        movementAction(this, Position(2994, 3945), 762, "You walk across the log balance.",
            listOf(AgilitySound(Sound.LOG_BALANCE, loops = 3),
                AgilitySound(Sound.LOG_BALANCE, tick = 4, loops = 2),
                AgilitySound(Sound.LOG_BALANCE, tick = 7, loops = 2)))
    }
    obstacle(2328, WILDERNESS_ROCKS) { rocks ->
        AgilityAction(this, listOf(
            AgilityStep(rocks.position, 1, walkingAnimation = 737,
                sounds = listOf(AgilitySound(Sound.CLIMBING_LOOP, loops = 7))),
            AgilityStep(rocks.position.translate(0, -3), 3, walkingAnimation = 737)
        ), "You climb up the rocks.")
    }
}

for (id in listOf(2309, 2307, 2308)) {
    object1(id) {
        AgilityEntrances.wildernessRidge(plr, entering = id == 2309)?.let { plr.submitAction(it) }
    }
}
