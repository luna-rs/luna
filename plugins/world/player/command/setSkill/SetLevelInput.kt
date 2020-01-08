package world.player.command.setSkill

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.SkillSet
import io.luna.game.model.mob.inter.AmountInputInterface
import io.luna.net.msg.out.SkillUpdateMessageWriter

/**
 * An [AmountInputInterface] used to enter the desired level.
 */
class SetLevelInput(private val skillId: Int) : AmountInputInterface() {

    override fun onAmountInput(plr: Player, level: Int) {
        if (level < 1 || level > 99) {
            plr.sendMessage("Level must be above or equal to 1 and below or equal to 99.")
        } else {
            val skill = plr.skill(skillId)
            val name = Skill.getName(skillId)
            val exp = SkillSet.experienceForLevel(level).toDouble()

            // Stop firing events so no congratulatory messages are sent.
            plr.skills.isFiringEvents = false
            try {
                skill.level = level
                skill.experience = exp
            } finally {
                plr.skills.isFiringEvents = true
            }

            plr.queue(SkillUpdateMessageWriter(skillId))
            plr.sendMessage("You set your $name level to $level.")
        }
    }
}