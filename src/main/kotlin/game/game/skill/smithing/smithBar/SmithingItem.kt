package game.skill.smithing.smithBar;

import game.skill.smithing.BarType
import io.luna.game.model.item.Item

/**
 * Represents a single item as part of a [SmithingTable].
 *
 * @author lare96
 */
class SmithingItem(val level: Int, val item: Item, val barType: BarType)