package game.skill.magic

import io.luna.game.model.item.Item

/**
 * What casting a spell takes from a player, or why they can't cast it, as [Magic.spellCost] finds it.
 *
 * @author Hydrozoa
 */
sealed class SpellCost {

    /**
     * The spell can be cast.
     *
     * @property items The items that casting it uses up: its runes, the combination runes that stand in for some of them,
     * and the other items it needs.
     */
    data class Castable(val items: List<Item>) : SpellCost()

    /**
     * The player's Magic level is too low.
     */
    object LowLevel : SpellCost()

    /**
     * The player's inventory doesn't have enough of an item that the spell needs.
     *
     * @property id The id of the item.
     */
    data class MissingItem(val id: Int) : SpellCost()

    /**
     * The player doesn't have an item equipped that the spell needs.
     *
     * @property id The id of the item.
     */
    data class MissingEquipment(val id: Int) : SpellCost()

    /**
     * The player doesn't have enough of a rune, even with their staff and combination runes.
     *
     * @property rune The rune.
     */
    data class MissingRune(val rune: Rune) : SpellCost()
}
