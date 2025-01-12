package world.player.skill.magic.bonesToItems

import world.player.skill.magic.Rune
import world.player.skill.magic.RuneRequirement
import world.player.skill.magic.SpellRequirement

/**
 * An enum representing the two bones to items spells.
 */
enum class BonesToItemsType(val id: Int, val level: Int, val xp: Double, val requirements: List<SpellRequirement>) {
    BANANAS(id = 1963,
            level = 15,
            xp = 25.0,
            requirements = listOf(
                RuneRequirement(Rune.EARTH, 2),
                RuneRequirement(Rune.WATER, 2),
                RuneRequirement(Rune.NATURE, 1)
            )),
    PEACHES(id = 6883,
            level = 60,
            xp = 35.5,
            requirements = listOf(
                RuneRequirement(Rune.EARTH, 4),
                RuneRequirement(Rune.WATER, 4),
                RuneRequirement(Rune.NATURE, 1)
            )),
}