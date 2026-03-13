package game.item.experienceLamp

import api.predef.*
import io.luna.game.model.mob.overlay.StandardInterface

/**
 * Handles the interface where the player selects which skill to gain experience in,
 * from a lamp.
 */
class ExperienceLampInterface : StandardInterface(2808) {

    enum class InterfaceSkill(val varpValue: Int, val button: Int) {
        ATTACK(1, 2812),
        STRENGTH(2, 2813),
        RANGED(3, 2814),
        MAGIC(4, 2815),
        DEFENCE(5, 2816),
        HITPOINTS(6, 2817),
        PRAYER(7, 2818),
        AGILITY(8, 2819),
        HERBLORE(9, 2820),
        THIEVING(10, 2821),
        CRAFTING(11, 2822),
        RUNECRAFTING(12, 2823),
        MINING(13, 2824),
        SMITHING(14, 2825),
        FISHING(15, 2826),
        COOKING(16, 2827),
        FIREMAKING(17, 2828),
        WOODCUTTING(18, 2829),
        FLETCHING(19, 2830),
        SLAYER(20, 12034),
        FARMING(21, 13914),
        ;

        companion object {
            /**
             * Returns the [skill] ID associated with a specific [varpValue].
             * Returns null if no match is found.
             */
            fun getSkillByVarp(value: Int): Int {
                when (value) {
                    1 -> return SKILL_ATTACK
                    2 -> return SKILL_STRENGTH
                    3 -> return SKILL_RANGED
                    4 -> return SKILL_MAGIC
                    5 -> return SKILL_DEFENCE
                    6 -> return SKILL_HITPOINTS
                    7 -> return SKILL_PRAYER
                    8 -> return SKILL_AGILITY
                    9 -> return SKILL_HERBLORE
                    10 -> return SKILL_THIEVING
                    11 -> return SKILL_CRAFTING
                    12 -> return SKILL_RUNECRAFTING
                    13 -> return SKILL_MINING
                    14 -> return SKILL_SMITHING
                    15 -> return SKILL_FISHING
                    16 -> return SKILL_COOKING
                    17 -> return SKILL_FIREMAKING
                    18 -> return SKILL_WOODCUTTING
                    19 -> return SKILL_FLETCHING
                    20 -> return SKILL_SLAYER
                    21 -> return SKILL_FARMING
                    else -> return SKILL_HITPOINTS
                }
            }
        }
    }
}