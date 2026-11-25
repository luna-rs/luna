package game.npc.spawn.rellekka

import api.predef.*

/*
 * Sigli the Huntsman and Peer the Seer make armour when talked to. Skulgrimen offers it from his own dialogue.
 */
npc1(FremennikArmour.SPINED.npc) { FremennikArmour.openMenu(plr, FremennikArmour.SPINED) }
npc1(FremennikArmour.SKELETAL.npc) { FremennikArmour.openMenu(plr, FremennikArmour.SKELETAL) }

/*
 * Using a hide or a part on any of the three craftsmen opens the same menu.
 */
for (maker in FremennikArmour.values()) {
    val materials = setOf(FremennikArmour.DAGANNOTH_HIDE) + maker.pieces.map { it.part }
    for (material in materials) {
        useItem(material).onNpc(maker.npc) { FremennikArmour.openMenu(plr, maker) }
    }
}
