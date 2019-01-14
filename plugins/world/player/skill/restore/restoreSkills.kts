import api.attr.Attr
import api.predef.*
import io.luna.game.event.impl.SkillChangeEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.SkillSet
import io.luna.game.task.Task
import java.util.*

/**
 * The "restoring_skills" attribute.
 */
var Player.restoringSkills by Attr<Boolean>("restoring_skills")

/**
 * A [Task] that will restore boosted and depleted skills every 50 ticks.
 */
class RestoreSkills(val plr: Player) : Task(false, 50) {

    /**
     * The restore counter. Determines when skills will be restored.
     */
    val restore = BitSet(SkillSet.size())

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
        val id = skill.id

        /* if (plr.prayers.contains(Prayer.RAPID_RESTORE) && id != SKILL_HITPOINTS ||
                 plr.prayers.contains(Prayer.RAPID_RESTORE) && id == SKILL_HITPOINTS ) {
             counter[id] = true
         }*/

        if (restore[id]) {
            // Skill is ready to be restored!
            finished = false
            when {
                skill.level < skill.staticLevel -> skill.level += 1
                skill.level > skill.staticLevel -> skill.level -= 1
            }
        }
        restore.flip(id)
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

on(SkillChangeEvent::class)
    .condition { mob is Player }
    .then { restore(mob as Player, id) }