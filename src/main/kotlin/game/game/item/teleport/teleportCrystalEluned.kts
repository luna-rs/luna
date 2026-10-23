package game.item.teleport

import api.predef.*
import game.item.teleport.TeleportCrystal.COINS
import game.item.teleport.TeleportCrystal.ELUNED_ISAFDAR
import game.item.teleport.TeleportCrystal.ELUNED_LLETYA
import game.item.teleport.TeleportCrystal.ELUNED_LLETYA_VISIBLE
import game.item.teleport.TeleportCrystal.RECHARGED
import game.item.teleport.TeleportCrystal.TINY_ELF_CRYSTAL
import game.item.teleport.TeleportCrystal.teleportCrystalRechargePrice
import game.item.teleport.TeleportCrystal.teleportCrystalRecharges
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Asks whether [plr] wants their Tiny elf crystal re-enchanted. [eluned] is the NPC id shown in the chathead.
 */
fun offerCrystalRecharge(plr: Player, eluned: Int) {
    plr.newDialogue()
        .npc(eluned, "Hey ${plr.username}, do you need me to re-enchant your", "teleport crystal?")
        .options("Yes please.", { askCrystalRechargePrice(it, eluned) },
                 "No thanks.", { it.resetDialogues() })
        .open()
}

/**
 * Tells [plr] what the recharge costs and asks them to confirm.
 */
fun askCrystalRechargePrice(plr: Player, eluned: Int) {
    val price = plr.teleportCrystalRechargePrice
    plr.newDialogue()
        .player("Yes please.")
        .npc(eluned, "I ask $price coins to cover my costs.")
        .options("Yes - $price coins.", { rechargeCrystal(it, eluned, price) },
                 "No thanks.", { it.resetDialogues() })
        .title("Enchant your teleport crystal?")
        .open()
}

/**
 * Takes [price] coins from [plr] and turns one Tiny elf crystal into a Teleport crystal (3).
 */
fun rechargeCrystal(plr: Player, eluned: Int, price: Int) {
    val slot = plr.inventory.computeIndexForId(TINY_ELF_CRYSTAL)
    if (slot == -1) {
        plr.resetDialogues()
        return
    }
    if (plr.inventory.computeAmountForId(COINS) < price || !plr.inventory.remove(Item(COINS, price))) {
        plr.newDialogue()
            .npc(eluned, "You do not have enough coins to cover that cost, sorry.")
            .open()
        return
    }
    plr.inventory[slot] = Item(RECHARGED)
    plr.teleportCrystalRecharges++
    val chanted = TeleportCrystalChantDialogue()
    chanted.setContinueAction { it.newDialogue().player("Thank you.").open() }
    plr.resetDialogues()
    plr.overlays.open(chanted)
}

for (eluned in listOf(ELUNED_ISAFDAR, ELUNED_LLETYA)) {
    npc1(eluned, false) {
        // TODO Roving Elves and Mourning's End Part I: Eluned only re-enchants once Roving Elves is finished and
        // Mourning's End Part I is started.
        if (TINY_ELF_CRYSTAL !in plr.inventory) {
            // Eluned's other dialogue isn't written yet.
            plr.sendMessage("Nothing interesting happens.")
            return@npc1
        }
        targetNpc.interact(plr)
        offerCrystalRecharge(plr, if (eluned == ELUNED_LLETYA) ELUNED_LLETYA_VISIBLE else eluned)
    }
}
