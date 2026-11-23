package game.skill.firemaking.lightSource

import api.predef.*
import io.luna.game.event.impl.UseItemEvent.ItemOnItemEvent
import io.luna.game.model.item.Item

/**
 * The glass candle lantern with no candle in it.
 */
val EMPTY_LANTERN = 4527

/**
 * Each lit candle, and the lit candle lantern it makes.
 */
val LIT_CANDLE_LANTERNS = mapOf(
    LightSource.CANDLE.lit to LightSource.CANDLE_LANTERN.lit,
    LightSource.BLACK_CANDLE.lit to LightSource.BLACK_CANDLE_LANTERN.lit
)

/**
 * The glass lantern lens.
 */
val GLASS_LENS = 4542

/**
 * A cut sapphire.
 */
val CUT_SAPPHIRE = 1607

/**
 * Each unlit sapphire lantern, and the bullseye lantern it becomes with a glass lens.
 */
val BULLSEYE_LANTERNS = mapOf(
    4700 to 4546,
    4701 to 4548
)

/**
 * The Crafting level needed to fit a glass lens.
 */
val GLASS_LENS_LEVEL = 49

/**
 * The inventory slot holding the item with [id], whichever way round the two items were used.
 */
fun ItemOnItemEvent.slotFor(id: Int) = if (usedItemId == id) usedItemIndex else targetItemIndex

// A lit candle goes into the empty lantern still burning.
for ((candle, lantern) in LIT_CANDLE_LANTERNS) {
    useItem(candle).onItem(EMPTY_LANTERN) {
        plr.inventory[slotFor(candle)] = null
        plr.inventory[slotFor(EMPTY_LANTERN)] = Item(lantern)
        plr.sendMessage("You put the candle in the lantern.")
    }
}

// A glass lens takes the sapphire's place again, and the player keeps the sapphire.
for ((sapphireLantern, bullseye) in BULLSEYE_LANTERNS) {
    useItem(GLASS_LENS).onItem(sapphireLantern) {
        if (plr.crafting.level < GLASS_LENS_LEVEL) {
            plr.sendMessage("You need a Crafting level of $GLASS_LENS_LEVEL to fit the lens.")
        } else {
            plr.inventory[slotFor(GLASS_LENS)] = Item(CUT_SAPPHIRE)
            plr.inventory[slotFor(sapphireLantern)] = Item(bullseye)
            plr.sendMessage("You swap the lantern's sapphire for the lens.")
        }
    }
}
