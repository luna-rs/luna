package game.item.teleport

import api.predef.*
import game.item.teleport.Camulet.BUCKET
import game.item.teleport.Camulet.CAMULET
import game.item.teleport.Camulet.MAX_CHARGES
import game.item.teleport.Camulet.MAX_WILDERNESS_LEVEL
import game.item.teleport.Camulet.TEMPLE
import game.item.teleport.Camulet.TEMPLE_RADIUS
import game.item.teleport.Camulet.UGTHANKI_DUNG
import game.item.teleport.Camulet.camuletCharges
import game.skill.magic.Magic.findLandingTile
import game.skill.magic.Magic.teleport
import game.skill.magic.teleportSpells.TeleportStyle
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Rubs the Camulet, teleporting [plr] to Enakhra's Temple if it has a charge. The charge is used once the player
 * lands.
 */
fun rubCamulet(plr: Player) {
    if (plr.camuletCharges <= 0) {
        plr.newDialogue()
            .text("Your Camulet has run out of teleport charges. You can renew them", "by applying camel dung.")
            .open()
        return
    }
    plr.sendMessage("You rub the amulet...")
    val landing = findLandingTile(TEMPLE, TEMPLE_RADIUS)
    plr.teleport(landing, TeleportStyle.REGULAR, MAX_WILDERNESS_LEVEL, onLand = {
        plr.camuletCharges = (plr.camuletCharges - 1).coerceAtLeast(0)
    })
}

/**
 * Tells [plr] how many charges their Camulet has left.
 */
fun checkCamuletCharges(plr: Player) {
    when (val charges = plr.camuletCharges) {
        0 -> {
            plr.sendMessage("Your Camulet has no charges left.")
            plr.sendMessage("You can recharge it by applying camel dung.")
        }

        1 -> plr.sendMessage("Your Camulet has 1 charge left.")
        else -> plr.sendMessage("Your Camulet has $charges charges left.")
    }
}

item4(CAMULET) { rubCamulet(plr) }

item3(CAMULET) { checkCamuletCharges(plr) }

// This fires both ways round, but only dung used on the Camulet recharges it. The dung is used up even at full charge.
useItem(UGTHANKI_DUNG).onItem(CAMULET) {
    if (usedItemId != UGTHANKI_DUNG) {
        plr.sendMessage("Nothing interesting happens.")
        return@onItem
    }
    plr.inventory[usedItemIndex] = Item(BUCKET)
    plr.camuletCharges = MAX_CHARGES
    plr.sendMessage("You recharge the Camulet using camel dung. Yuck!")
}
