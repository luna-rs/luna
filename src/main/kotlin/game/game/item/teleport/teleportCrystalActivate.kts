package game.item.teleport

import api.predef.*
import game.skill.magic.Magic.teleport
import game.skill.magic.teleportSpells.TeleportStyle
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Teleports [plr] to Lletya with the crystal [id] in inventory [slot]. The charge is only used once the player lands.
 */
fun activateTeleportCrystal(plr: Player, slot: Int, id: Int) {
    plr.teleport(TeleportCrystal.LLETYA, TeleportStyle.REGULAR, onLand = { useTeleportCrystalCharge(plr, slot, id) })
}

/**
 * Replaces the crystal [id] in inventory [slot] with its next stage, if it is still there.
 */
fun useTeleportCrystalCharge(plr: Player, slot: Int, id: Int) {
    if (plr.inventory[slot]?.id != id) {
        return
    }
    plr.inventory[slot] = Item(TeleportCrystal.nextStage(id))
}

for (crystal in TeleportCrystal.CHARGED) {
    item1(crystal) { activateTeleportCrystal(plr, index, crystal) }
}
