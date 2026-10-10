package game.item.teleport

import api.predef.*
import game.item.teleport.EctophialTeleport.DESTINATION
import game.item.teleport.EctophialTeleport.ECTOFUNTUS
import game.item.teleport.EctophialTeleport.EMPTY
import game.item.teleport.EctophialTeleport.FULL
import game.player.Sound
import game.skill.magic.Magic.teleport
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Empties the full Ectophial in inventory slot [index], teleporting the player to the Ectofuntus. The Ectophial is
 * only emptied once the player lands.
 */
fun emptyEctophial(plr: Player, index: Int) {
    plr.teleport(DESTINATION, EctophialTeleport, onLand = {
        plr.sendMessage("...and the world changes around you.")
        if (plr.inventory[index]?.id == FULL) {
            plr.inventory[index] = Item(EMPTY)
        }
    }) {
        plr.sendMessage("You empty the ectoplasm onto the ground around your feet...")
    }
}

item1(FULL) { emptyEctophial(plr, index) }

useItem(EMPTY).onObject(ECTOFUNTUS) {
    if (plr.inventory[usedItemIndex]?.id != usedItemId) {
        return@onObject
    }
    plr.inventory[usedItemIndex] = Item(FULL)
    plr.playSound(Sound.FILL_ECTOPLASM)
    plr.sendMessage("You refill the ectophial from the Ectofuntus.")
}
