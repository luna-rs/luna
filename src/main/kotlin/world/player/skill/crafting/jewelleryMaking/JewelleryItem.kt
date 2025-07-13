package world.player.skill.crafting.jewelleryMaking

import api.predef.*
import io.luna.game.model.item.Item

/**
 * Represents a craftable jewellery item.
 */
class JewelleryItem(name: String, val level: Int, val xp: Double, requiredName: String? = null) {

    /**
     * The crafted jewellery item.
     */
    val item: Item = item(name)

    /**
     * The additional required item (other than a silver/gold bar).
     */
    val requiredItem: Item? = if (requiredName != null) item(requiredName) else null
}