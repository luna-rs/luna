package world.player.skill.magic

/**
 * An interface representing a generic spell requirement.
 *
 * @author lare96
 */
interface SpellRequirement

/**
 * A [SpellRequirement] requirement implementation for generic items.
 */
class ItemRequirement(val id: Int, val amount: Int = 1) : SpellRequirement

/**
 * A [SpellRequirement] requirement implementation for runes.
 */
class RuneRequirement(val rune: Rune, val amount: Int) : SpellRequirement