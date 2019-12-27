package world.player.skill.restoreSkills

import api.attr.Attr
import api.predef.*
import io.luna.game.event.impl.SkillChangeEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.task.Task

/**
 * If skills are being restored.
 */
var Player.restoringSkills by Attr.boolean()

/**
 * A [Task] that will restore boosted and depleted skills every 100 ticks.
 */
class RestoreSkills(val plr: Player) : Task(false, 100) {

    /**
     * If all skills have been restored.
     */
    var finished = true

    override fun onSchedule(): Boolean {
        plr.restoringSkills = true
        return true
    }

    override fun execute() {
        // Reset the "finished" flag.
        finished = true

        // Try to restore skills, if one is restored "finished" will be unflagged.
        plr.skills.stream()
            .filter { it.id != SKILL_PRAYER }
            .filter { it.level != it.staticLevel }
            .forEach(this::doRestore)

        // If we didn't restore any skills, cancel the task.
        if (finished) {
            cancel()
        }
    }

    override fun onCancel() {
        plr.restoringSkills = false
    }

    fun doRestore(skill: Skill) {
        // TODO Prayers for increased restoration.

        // Skill is ready to be restored!
        finished = false
        when {
            skill.level < skill.staticLevel -> skill.level += 1
            skill.level > skill.staticLevel -> skill.level -= 1
        }
    }
}

/**
 * Starts the restore task.
 */
fun restore(plr: Player, id: Int) {
    if (!plr.restoringSkills) {
        val skill = plr.skill(id)
        if (skill.level != skill.staticLevel) {
            world.schedule(RestoreSkills(plr))
        }
    }
}

// Start restore task when skills change.
on(SkillChangeEvent::class)
    .filter { mob is Player }
    .then { restore(mob as Player, id) }