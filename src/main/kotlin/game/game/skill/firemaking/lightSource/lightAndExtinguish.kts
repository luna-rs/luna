package game.skill.firemaking.lightSource

import api.predef.*
import game.skill.firemaking.Firemaking
import io.luna.game.event.impl.ItemClickEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * The cache option that puts out each lit light.
 */
val EXTINGUISH = "Extinguish"

/**
 * Lights the unlit [light] in inventory slot [index] for [plr].
 */
fun light(plr: Player, light: LightSource, index: Int) {
    if (plr.firemaking.level < light.level) {
        plr.sendMessage("You need a Firemaking level of ${light.level} to light this.")
        return
    }
    plr.inventory[index] = Item(light.lit)
    if (light != LightSource.CANDLE && light != LightSource.BLACK_CANDLE) {
        plr.sendMessage("You light the ${light.messageName}.")
    }
}

/**
 * Puts out the lit [light] that [plr] clicked.
 */
fun extinguish(plr: Player, light: LightSource, event: ItemClickEvent) {
    if (plr.inventory[event.index]?.id == light.lit) {
        plr.inventory[event.index] = Item(light.unlit)
        plr.sendMessage("You extinguish the ${light.messageName}.")
    }
}

for (light in LightSource.entries) {
    useItem(Firemaking.TINDERBOX).onItem(light.unlit) {
        light(plr, light, if (usedItemId == light.unlit) usedItemIndex else targetItemIndex)
    }
    when (itemDef(light.lit).inventoryActions.indexOf(EXTINGUISH)) {
        0 -> item1(light.lit) { extinguish(plr, light, this) }
        1 -> item2(light.lit) { extinguish(plr, light, this) }
        2 -> item3(light.lit) { extinguish(plr, light, this) }
    }
}
