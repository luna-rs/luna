package world.player.skill.magic.enchantJewellery

import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Graphic

/**
 * Represents an enchanted item.
 */
class EnchantItem(val id: Int, val animation: Animation, val graphic: Graphic, val sound: Int = -1)
// todo sound = -1 temporary, need ring sound ids