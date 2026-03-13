package game.item.experienceLamp

import api.predef.*
import io.luna.game.model.mob.overlay.StandardInterface

/**
 * Handles the interface where the player selects which skill to gain experience in,
 * from a lamp.
 * @author hydrozoa
 */
class ExperienceLampInterface : StandardInterface(2808) {

    enum class InterfaceSkill(val varpValue: Int, val button: Int, val skill: Int) {
        ATTACK(1, 2812, SKILL_ATTACK),
        STRENGTH(2, 2813, SKILL_STRENGTH),
        RANGED(3, 2814, SKILL_RANGED),
        MAGIC(4, 2815, SKILL_MAGIC),
        DEFENCE(5, 2816, SKILL_DEFENCE),
        HITPOINTS(6, 2817, SKILL_HITPOINTS),
        PRAYER(7, 2818, SKILL_PRAYER),
        AGILITY(8, 2819, SKILL_AGILITY),
        HERBLORE(9, 2820, SKILL_HERBLORE),
        THIEVING(10, 2821, SKILL_THIEVING),
        CRAFTING(11, 2822, SKILL_CRAFTING),
        RUNECRAFTING(12, 2823, SKILL_RUNECRAFTING),
        MINING(13, 2824, SKILL_MINING),
        SMITHING(14, 2825, SKILL_SMITHING),
        FISHING(15, 2826, SKILL_FISHING),
        COOKING(16, 2827, SKILL_COOKING),
        FIREMAKING(17, 2828, SKILL_FIREMAKING),
        WOODCUTTING(18, 2829, SKILL_WOODCUTTING),
        FLETCHING(19, 2830, SKILL_FLETCHING),
        SLAYER(20, 12034, SKILL_SLAYER),
        FARMING(21, 13914, SKILL_FARMING),
        ;

        companion object {
            /**
             * Returns the [skill] ID associated with a specific [varpValue].
             * Returns null if no match is found.
             */
            fun getSkillByVarp(value: Int): Int {
                val map = InterfaceSkill.values().associateBy({i -> i.varpValue})
                return map.get(value)?.skill ?: SKILL_HITPOINTS
            }
        }
    }
}