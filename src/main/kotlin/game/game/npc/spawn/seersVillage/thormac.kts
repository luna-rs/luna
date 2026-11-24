package game.npc.spawn.seersVillage

import api.predef.*
import game.npc.spawn.seersVillage.ThormacInterface.Staff
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * Thormac the sorcerer, in his tower south of Seers' Village.
 */
val THORMAC = 389

/**
 * The coins Thormac is paid with.
 */
val COINS = 995

/**
 * What Thormac charges to enchant a battlestaff.
 */
val ENCHANT_PRICE = 40_000

npc1(THORMAC) {
    // TODO Scorpion Catcher: Thormac only enchants battlestaves for players who have found his scorpions. Before the
    //  quest he asks for help, and while it's started he asks how it goes. Until the quest exists every player has
    //  finished it.
    plr.newDialogue()
        .npc(THORMAC, Expression.HAPPY, "Thank you for rescuing my scorpions.")
        .options("That's ok.", {
            it.newDialogue().player(Expression.HAPPY, "That's ok.").open()
        }, "You said you'd enchant my battlestaff for me.", {
            it.newDialogue()
                .player(Expression.QUIZZICAL, "You said you'd enchant my battlestaff for me.")
                .npc(THORMAC, "Yes, it'll cost you 40,000 coins for the materials needed",
                     "though. Which sort of staff did you want enchanting?")
                .then { plr -> plr.overlays.open(ThormacInterface()) }
                .open()
        })
        .open()
}

for (staff in Staff.values()) {
    button(staff.button) {
        if (plr.overlays.has(ThormacInterface::class.java)) enchant(plr, staff)
    }
}

/**
 * Thormac enchants [plr]'s battlestaff into a mystic staff, if they carry one and his fee.
 */
fun enchant(plr: Player, staff: Staff) {
    val name = itemName(staff.battlestaff).lowercase()
    when {
        !plr.inventory.contains(staff.battlestaff) -> plr.sendMessage("You don't have ${addArticle(name)}.")
        plr.inventory.computeAmountForId(COINS) < ENCHANT_PRICE ->
            plr.newDialogue().text("You need 40,000 coins to pay Thormac.").open()
        plr.inventory.remove(Item(COINS, ENCHANT_PRICE)) -> {
            plr.inventory.remove(Item(staff.battlestaff))
            plr.inventory.add(Item(staff.mysticStaff))
            plr.sendMessage("Thormac enchants your $name.")
        }
    }
}
