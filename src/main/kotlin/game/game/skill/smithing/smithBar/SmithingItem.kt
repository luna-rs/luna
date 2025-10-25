package game.skill.smithing.smithBar;

import io.luna.game.model.item.Item
import game.skill.smithing.BarType

/**
 * Represents a single item as part of a [SmithingTable].
 *
 * @author lare96
 */
class SmithingItem(val level: Int, val item: Item, val barType: BarType)