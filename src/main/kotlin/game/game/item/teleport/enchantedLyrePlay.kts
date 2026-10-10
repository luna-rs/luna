package game.item.teleport

import api.predef.*
import game.item.teleport.EnchantedLyre.LYRE
import game.item.teleport.EnchantedLyre.MAX_WILDERNESS_LEVEL
import game.item.teleport.EnchantedLyre.NEXT_CHARGE
import game.item.teleport.EnchantedLyre.RELLEKKA
import game.item.teleport.EnchantedLyre.RELLEKKA_RADIUS
import game.item.teleport.EnchantedLyre.TELEPORT
import game.skill.magic.Magic.findLandingTile
import game.skill.magic.Magic.teleport
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * Plays the enchanted lyre [id] in inventory [slot], teleporting the player to Rellekka. The charge is used once
 * the player lands.
 */
fun play(plr: Player, id: Int, slot: Int) {
    val landing = findLandingTile(RELLEKKA, RELLEKKA_RADIUS)
    plr.teleport(landing, TELEPORT, MAX_WILDERNESS_LEVEL, onLand = {
        plr.sendMessage("Your lyre teleports you to Rellekka.")
        if (plr.inventory[slot]?.id == id) {
            plr.inventory[slot] = Item(NEXT_CHARGE.getValue(id))
        }
    })
}

for (id in NEXT_CHARGE.keys) {
    item1(id) { play(plr, id, index) }
}

item1(LYRE) {
    plr.newDialogue()
        .player(Expression.SAD, "I really wouldn't know where to begin playing anything", "on this...")
        .open()
}
