package game.skill.agility

import api.predef.*
import game.skill.Skills
import io.luna.game.model.Position
import io.luna.game.model.mob.Player

/**
 * Entrance traversals that award XP separately from the course checkpoints.
 *
 * @author lare96
 */
object AgilityEntrances {

    /**
     * Only entering the Barbarian course requires level 35; a drained player can still leave.
     */
    fun barbarianPipe(plr: Player): AgilityAction? {
        if (plr.isLocked || plr.z != 0 || plr.x != 2552 || plr.y !in setOf(3558, 3561)) return null
        val entering = plr.y == 3561
        if (entering && !Agility.checkLevel(plr, 35)) return null
        return AgilityAction(plr,
            listOf(AgilityStep(Position(2552, if (entering) 3558 else 3561), 4, animation = 749)),
            "You squeeze through the pipe.").also {
            it.onSuccess = { plr.agility.addExperience(10.0) }
        }
    }

    /**
     * The Wilderness ridge requires level 52 on entry. Falling lands in the wolf pit without
     * damage or XP, as in Lost City's fail_ridge label; returning across the ridge has no level gate.
     */
    fun wildernessRidge(plr: Player, entering: Boolean,
                        roll: () -> Double = { rand().nextDouble() }): AgilityAction? {
        if (plr.isLocked || plr.z != 0 || plr.x !in 2997..2998) return null
        if (entering && plr.y !in 3916..3917 || !entering && plr.y !in 3931..3932) return null
        if (entering && !Agility.checkLevel(plr, 52)) return null
        val middle = Position(2998, if (entering) 3925 else 3924)
        val failed = roll() >= Skills.successRate(200, 250, minOf(plr.agility.level, 99))
        val steps = mutableListOf(
            AgilityStep(Position(2998, if (entering) 3918 else 3930), 1, jump = true),
            AgilityStep(middle, if (entering) 7 else 6, walkingAnimation = 762)
        )
        if (failed) {
            steps += AgilityStep(middle, 1, animation = 764, jump = true)
            steps += AgilityStep(middle.translate(3, -1), 2, walkingAnimation = 768)
        } else {
            steps += AgilityStep(Position(2998, if (entering) 3931 else 3918), 6, walkingAnimation = 762)
            steps += AgilityStep(Position(2998, if (entering) 3933 else 3916), 1, jump = true)
        }
        return AgilityAction(plr, steps, "You try to edge over the ridge...").also {
            it.onSuccess = {
                if (failed) plr.sendMessage("You lose your footing and fall into the wolf pit.")
                else {
                    plr.sendMessage("You skillfully balance across the ridge.")
                    plr.agility.addExperience(15.0)
                }
            }
        }
    }
}
