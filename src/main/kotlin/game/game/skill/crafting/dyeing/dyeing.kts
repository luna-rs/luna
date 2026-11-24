package game.skill.crafting.dyeing

import api.predef.*
import io.luna.game.model.item.Item

// Mixing two primary dyes makes a secondary one.
for (dye in Dye.values()) {
    val (first, second) = dye.mixedFrom ?: continue
    useItem(first).onItem(second) {
        if (plr.inventory.remove(Item(first)) && plr.inventory.remove(Item(second))) {
            plr.inventory.add(Item(dye.id))
            val article = if (dye.colour[0] in "aeiou") "an" else "a"
            plr.sendMessage("You mix the two dyes and make $article ${dye.colour} one.")
        }
    }
}

// Any dye turns a cape of another colour into its own.
for (dye in Dye.values()) {
    for ((cape, colour) in Dye.CAPES) {
        if (cape == dye.cape) continue
        useItem(dye.id).onItem(cape) {
            if (plr.inventory.remove(Item(dye.id)) && plr.inventory.remove(Item(cape))) {
                plr.inventory.add(Item(dye.cape))
                plr.crafting.addExperience(Dye.CAPE_EXPERIENCE)
                plr.sendMessage("You dye the $colour cape ${dye.colour}.")
            }
        }
    }
}
