package game.skill.magic.lowHighAlch

import game.skill.magic.Rune
import game.skill.magic.RuneRequirement
import game.skill.magic.SpellRequirement

/**
 * Represents the two different alchemy types.
 *
 * @author lare96
 */
enum class AlchemyType(val level: Int, val xp: Double, val requirements: List<SpellRequirement>) {
    LOW(level = 21,
        xp = 31.0,
        requirements = listOf(
            RuneRequirement(Rune.NATURE, 1),
            RuneRequirement(Rune.FIRE, 3)
        )),
    HIGH(level = 55,
         xp = 65.0,
         requirements = listOf(
             RuneRequirement(Rune.NATURE, 1),
             RuneRequirement(Rune.FIRE, 5)
         ))
}