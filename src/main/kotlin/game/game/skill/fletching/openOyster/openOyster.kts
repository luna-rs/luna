package game.skill.fletching.openOyster

import api.predef.*

item1(Oyster.ID) { Oyster.open(plr, index) }

useItem(Oyster.CHISEL).onItem(Oyster.ID) {
    Oyster.open(plr, if (usedItemId == Oyster.ID) usedItemIndex else targetItemIndex)
}
