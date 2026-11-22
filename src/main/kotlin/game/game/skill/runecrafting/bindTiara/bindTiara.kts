package game.skill.runecrafting.bindTiara

import api.predef.*
import game.skill.runecrafting.craftRune.CraftableRune
import game.skill.runecrafting.enterAltar.Altar
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * The plain silver tiara.
 */
val TIARA = 5525

/**
 * The experience for binding each altar's tiara.
 */
val BIND_EXPERIENCE = mapOf(
    Altar.AIR to 25.0,
    Altar.MIND to 27.5,
    Altar.WATER to 30.0,
    Altar.EARTH to 32.5,
    Altar.FIRE to 35.0,
    Altar.BODY to 37.5,
    Altar.COSMIC to 40.0,
    Altar.CHAOS to 42.5,
    Altar.NATURE to 45.0,
    Altar.LAW to 47.5,
    Altar.DEATH to 50.0
)

/**
 * Binds a plain tiara and [altar]'s talisman into the altar's tiara. The new tiara takes the slot of the item that was
 * used on the altar.
 */
fun bind(plr: Player, usedId: Int, usedIndex: Int, altar: Altar) {
    val inv = plr.inventory
    if (inv[usedIndex]?.id != usedId) {
        return
    }
    val otherId = if (usedId == TIARA) altar.talisman else TIARA
    if (!inv.contains(otherId)) {
        if (usedId == TIARA) {
            val talisman = itemName(altar.talisman)
            val article = if (talisman.first().lowercaseChar() in "aeiou") "an" else "a"
            plr.sendMessage("You need $article $talisman to bind a ${itemName(TIARA)} here.")
        } else {
            plr.sendMessage("You need a ${itemName(TIARA)} to bind your talisman.")
        }
        return
    }
    if (inv.remove(Item(otherId))) {
        inv.set(usedIndex, Item(altar.tiara))
        plr.runecrafting.addExperience(BIND_EXPERIENCE.getValue(altar))
        plr.sendMessage("You bind the power of the talisman into your ${itemName(TIARA)}.")
    }
}

CraftableRune.entries.forEach { rune ->
    val altar = rune.altar
    useItem(TIARA).onObject(rune.insideAltarObject) {
        bind(plr, usedItemId, usedItemIndex, altar)
    }
    useItem(altar.talisman).onObject(rune.insideAltarObject) {
        bind(plr, usedItemId, usedItemIndex, altar)
    }
}
