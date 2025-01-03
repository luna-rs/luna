package world.player.skill.crafting.jewelleryMaking

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.StandardInterface
import io.luna.net.msg.out.WidgetItemModelMessageWriter

class GoldJewelleryInterface : StandardInterface(4161) {

    override fun onOpen(plr: Player) {
        /*

		/* Rings */
			{1635, 1637, 1639, 1641, 1643, 1645, 6575},
		/* Neclece */
			{1654, 1656, 1658, 1660, 1662, 1664, 6577},
		/* amulet */
			{1673, 1675, 1677, 1679, 1681, 1683, 6579}

        placeholders for all possible items
         */
        plr.queue(WidgetItemModelMessageWriter(4229, 100, 4151)) // works
       // plr.queue(WidgetIndexedItemsMessageWriter(1635, IndexedItem(1, Item(4151, 1))))
    }
}