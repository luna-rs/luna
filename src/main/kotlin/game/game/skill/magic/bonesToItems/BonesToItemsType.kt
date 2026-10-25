package game.skill.magic.bonesToItems

import game.skill.magic.ItemRequirement
import game.skill.magic.Rune
import game.skill.magic.RuneRequirement
import game.skill.magic.SpellRequirement

/**
 * The two native bone-to-food spells, including their regular-spellbook buttons and per-cast requirements.
 * Each cast converts all carried normal bones; rune costs and experience apply once to the whole inventory.
 *
 * @author lare96
 */
enum class BonesToItemsType(val id: Int, val level: Int, val xp: Double,
                           val requirements: List<SpellRequirement>, val spellId: Int) {
    BANANAS(spellId = 1159,
            id = 1963,
            level = 15,
            xp = 25.0,
            requirements = listOf(
                ItemRequirement(BonesToItemsAction.BONES, 1),
                RuneRequirement(Rune.EARTH, 2),
                RuneRequirement(Rune.WATER, 2),
                RuneRequirement(Rune.NATURE, 1)
            )),
    PEACHES(spellId = 15877,
            id = 6883,
            level = 60,
            xp = 35.5,
            requirements = listOf(
                ItemRequirement(BonesToItemsAction.BONES, 1),
                RuneRequirement(Rune.EARTH, 4),
                RuneRequirement(Rune.WATER, 4),
                RuneRequirement(Rune.NATURE, 1)
            )),
}
