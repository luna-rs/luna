package game.obj.entrances

import api.predef.*

npc1(ShiloVillageTravel.HAJEDY) { ShiloVillageTravel.offer(plr, targetNpc) }
npc1(ShiloVillageTravel.VIGROY) { ShiloVillageTravel.offer(plr, targetNpc) }

for (cart in listOf(ShiloVillageTravel.BRIMHAVEN_CART, ShiloVillageTravel.SHILO_CART)) {
    object1(cart) { plr.newDialogue().text("A sturdy travelling cart built for long trips through jungle areas.").open() }
    object2(cart) { ShiloVillageTravel.board(plr, gameObject) }
}
