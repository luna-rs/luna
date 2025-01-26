package world.player.skill.crafting.jewelleryMaking

import io.luna.game.model.item.Item

/**
 * Represents a craftable jewellery item.
 */
class JewelleryItem(name: String, val level: Int, val xp: Double, requiredName: String? = null) {

    /**
     * The crafted jewellery item.
     */
    val item: Item = Item.byName(name)

    /**
     * The additional required item (other than a silver/gold bar).
     */
    val requiredItem: Item? = if(requiredName != null) Item.byName(requiredName) else null
}