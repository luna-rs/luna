package game.skill.magic.lowHighAlch

import game.skill.magic.Rune
import game.skill.magic.RuneRequirement
import game.skill.magic.SpellRequirement

/**
 * Represents the two different alchemy types.
 *
 * @author lare96
 */
enum class AlchemyType(
    val spellId: Int,
    val level: Int,
    val xp: Double,
    val valueMultiplier: Double,
    val requirements: List<SpellRequirement>
) {
    LOW(
        spellId = 1162,
        level = 21,
        xp = 31.0,
        valueMultiplier = 0.4,
        requirements = listOf(
            RuneRequirement(Rune.NATURE, 1),
            RuneRequirement(Rune.FIRE, 3)
        )
    ),

    HIGH(
        spellId = 1178,
        level = 55,
        xp = 65.0,
        valueMultiplier = 0.6,
        requirements = listOf(
            RuneRequirement(Rune.NATURE, 1),
            RuneRequirement(Rune.FIRE, 5)
        )
    )
}