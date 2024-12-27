package world.player.skill.smithing.smithBar;

import io.luna.game.model.item.Item
import world.player.skill.smithing.BarType

/**
 * Represents a single item as part of a [SmithingTable].
 */
class SmithingItem(val level: Int, val item: Item, val barType: BarType)