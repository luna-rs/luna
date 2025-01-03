package world.player.advanceLevel

import api.predef.*
import io.luna.game.event.impl.SkillChangeEvent
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag
import io.luna.net.msg.out.SkillUpdateMessageWriter

/**
 * Graphic played when a player advances a level.
 */
val fireworksGraphic = Graphic(199)

/**
 * A table holding data for the [LevelUpInterface].
 */
val levelUpTable = listOf(
        LevelUpData(6248, 6249, 6247),
        LevelUpData(6254, 6255, 6253),
        LevelUpData(6207, 6208, 6206),
        LevelUpData(6217, 6218, 6216),
        LevelUpData(5453, 5454, 4443),
        LevelUpData(6243, 6244, 6242),
        LevelUpData(6212, 6213, 6211),
        LevelUpData(6227, 6228, 6226),
        LevelUpData(4273, 4274, 4272),
        LevelUpData(6232, 6233, 6231),
        LevelUpData(6259, 6260, 6258),
        LevelUpData(4283, 4284, 4282),
        LevelUpData(6264, 6265, 6263),
        LevelUpData(6222, 6223, 6221),
        LevelUpData(4417, 4438, 4416),
        LevelUpData(6238, 6239, 6237),
        LevelUpData(4278, 4279, 4277),
        LevelUpData(4263, 4264, 4261),
        LevelUpData(12123, 12124, 12122),
        LevelUpData(4889, 4890, 4887),
        LevelUpData(4268, 4269, 4267))

/**
 * Determine if a player has advanced a level. If they have, send congratulatory messages.
 */
fun advanceLevel(plr: Player, skillId: Int, oldLevel: Int) {
    val skill = plr.skill(skillId)
    val newLevel = skill.staticLevel
    if (oldLevel < newLevel) {
        skill.level = when (skillId) {
            SKILL_HITPOINTS -> skill.level + 1
            else -> newLevel
        }

        val levelUpData = levelUpTable[skillId]
        plr.interfaces.open(LevelUpInterface(skillId, newLevel, levelUpData))
        plr.graphic(fireworksGraphic)

        if (Skill.isCombatSkill(skillId)) {
            plr.skills.resetCombatLevel()
            plr.flags.flag(UpdateFlag.APPEARANCE)
        }
    }
}

// Check if they've advanced a level on skill change.
on(SkillChangeEvent::class) {
    val plr = mob as? Player
    if (plr != null) {
        plr.queue(SkillUpdateMessageWriter(id))
        if (oldStaticLvl < 99) {
            advanceLevel(plr, id, oldStaticLvl)
        }
    }
}