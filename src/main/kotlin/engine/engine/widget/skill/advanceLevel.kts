package engine.widget.skill

import api.predef.*
import game.player.Jingles.*
import io.luna.game.event.EventPriority
import io.luna.game.event.impl.SkillChangeEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.block.Graphic
import io.luna.game.model.mob.block.UpdateFlagSet.UpdateFlag
import io.luna.net.msg.out.JingleMessageWriter
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
 * Milestone jingle levels.
 */
val MILESTONE_LEVELS = setOf(15, 25, 50, 60, 70, 75, 80, 85, 90, 92, 95, 99)

/**
 * Music sent when the player reaches one of [MILESTONE_LEVELS]. In real RS, sent when the player has
 * unlocked something new when they level up.
 */
val MILESTONE_JINGLES = mapOf(
    SKILL_ATTACK to ADVANCE_ATTACK_2,
    SKILL_DEFENCE to ADVANCE_DEFENSE_2,
    SKILL_STRENGTH to ADVANCE_STRENGTH_2,
    SKILL_HITPOINTS to ADVANCE_HITPOINTS_2,
    SKILL_RANGED to ADVANCE_RANGED_2,
    SKILL_PRAYER to ADVANCE_PRAYER_2,
    SKILL_MAGIC to ADVANCE_MAGIC_2,
    SKILL_COOKING to ADVANCE_COOKING_2,
    SKILL_WOODCUTTING to ADVANCE_WOODCUTTING_2,
    SKILL_FLETCHING to ADVANCE_FLETCHING_2,
    SKILL_FISHING to ADVANCE_FISHING_2,
    SKILL_FIREMAKING to ADVANCE_FIREMAKING_2,
    SKILL_CRAFTING to ADVANCE_CRAFTING_2,
    SKILL_SMITHING to ADVANCE_SMITHING_2,
    SKILL_MINING to ADVANCE_MINING_2,
    SKILL_HERBLORE to ADVANCE_HERBLAW_2,
    SKILL_THIEVING to ADVANCE_THIEVING_2,
    SKILL_RUNECRAFTING to ADVANCE_RUNECRAFT_2
)

/**
 * Music sent when the player reaches a non-milestone level.
 */
val LEVEL_UP_JINGLES = mapOf(
    SKILL_ATTACK to ADVANCE_ATTACK,
    SKILL_DEFENCE to ADVANCE_DEFENSE,
    SKILL_STRENGTH to ADVANCE_STRENGTH,
    SKILL_HITPOINTS to ADVANCE_HITPOINTS,
    SKILL_RANGED to ADVANCE_RANGED,
    SKILL_PRAYER to ADVANCE_PRAYER,
    SKILL_MAGIC to ADVANCE_MAGIC,
    SKILL_COOKING to ADVANCE_COOKING,
    SKILL_WOODCUTTING to ADVANCE_WOODCUTTING,
    SKILL_FLETCHING to ADVANCE_FLETCHING,
    SKILL_FISHING to ADVANCE_FISHING,
    SKILL_FIREMAKING to ADVANCE_FIREMAKING,
    SKILL_CRAFTING to ADVANCE_CRAFTING,
    SKILL_SMITHING to ADVANCE_SMITHING,
    SKILL_MINING to ADVANCE_MINING,
    SKILL_HERBLORE to ADVANCE_HERBLAW,
    SKILL_AGILITY to ADVANCE_AGILITY,
    SKILL_THIEVING to ADVANCE_THIEVING,
    SKILL_RUNECRAFTING to ADVANCE_RUNECRAFT
)

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

        // Open level up widget.
        val levelUpData = levelUpTable[skillId]
        plr.overlays.open(LevelUpInterface(skillId, newLevel, levelUpData))

        // Play jingle.
        val jingle = if (MILESTONE_LEVELS.contains(newLevel))
            MILESTONE_JINGLES[skillId] else LEVEL_UP_JINGLES[skillId]
        if (jingle != null) {
            plr.queue(JingleMessageWriter(jingle))
        }

        plr.graphic(fireworksGraphic)
        if (Skill.isCombatSkill(skillId)) {
            val oldCombatLevel = plr.skills.combatLevel
            plr.skills.resetCombatLevel()
            if (oldCombatLevel != plr.skills.combatLevel) {
                // Only flag appearance block if combat level changed.
                plr.flags.flag(UpdateFlag.APPEARANCE)
            }
        }
    }
}

// Check if they've advanced a level on skill change.
on(SkillChangeEvent::class, EventPriority.HIGH) {
    val plr = mob as? Player
    if (plr != null) {
        plr.queue(SkillUpdateMessageWriter(id))
        if (oldStaticLvl < 99) {
            advanceLevel(plr, id, oldStaticLvl)
        }
    }
}