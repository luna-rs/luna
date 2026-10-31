package game.skill.agility.course

import game.player.Sound
import game.skill.agility.*
import game.skill.agility.Agility.course
import game.skill.agility.AgilityCourse.GNOME_STRONGHOLD
import game.skill.agility.AgilityObstacle.*
import io.luna.game.model.Position

course(GNOME_STRONGHOLD) {
    obstacle(2295, GNOME_LOG_BALANCE) {
        movementAction(this, Position(2474, 3429), 762, "You walk carefully across the slippery log...",
            listOf(AgilitySound(Sound.LOG_BALANCE, loops = 4)), endMessage = "...You make it safely to the other side.")
    }
    obstacle(2285, GNOME_OBSTACLE_NET) {
        climbAction(this, position.translate(0, -2, 1), "You climb the netting...")
    }
    obstacle(2313, GNOME_TREE_BRANCH) {
        climbAction(this, Position(2473, 3420, 2), "You climb the tree...", ticks = 1,
            endMessage = "...To the platform above.")
    }
    obstacle(2312, GNOME_BALANCING_ROPE) {
        movementAction(this, Position(2483, 3420, 2), 762, "You carefully cross the tightrope.",
            listOf(AgilitySound(Sound.TIGHTROPE, loops = 5)))
    }
    for (id in listOf(2314, 2315)) {
        obstacle(id, GNOME_TREE_BRANCH_2) {
            climbAction(this, Position(2487, 3420), "You climb down the tree...",
                endMessage = "You land on the ground.")
        }
    }
    obstacle(2286, GNOME_OBSTACLE_NET_2) {
        climbAction(this, position.translate(0, 2), "You climb the netting...")
    }
    for (id in listOf(154, 4058)) {
        obstacle(id, GNOME_OBSTACLE_PIPE) {
            // The pipes cross underground: the west entrance exits at the east pipe and vice versa.
            val exitX = if (x == 2484) 2487 else 2484
            AgilityAction(this, listOf(
                AgilityStep(Position(x, 3433), 4, animation = 749),
                AgilityStep(Position(exitX, 3434), 1, jump = true,
                    sounds = listOf(AgilitySound(Sound.SQUEEZE_IN))),
                AgilityStep(Position(exitX, 3437), 4, animation = 749,
                    sounds = listOf(AgilitySound(Sound.SQUEEZE_IN, tick = 4)))
            ), null)
        }
    }
}
