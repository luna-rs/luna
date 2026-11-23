package game.skill.firemaking.lightSource

import api.predef.*
import io.luna.game.event.impl.UseItemEvent.ItemOnItemEvent
import io.luna.game.model.item.Item

/**
 * The glass candle lantern with no candle in it.
 */
val EMPTY_CANDLE_LANTERN = 4527

/**
 * Each candle, and the unlit candle lantern it makes.
 */
val CANDLE_LANTERNS = mapOf(
    36 to 4529, // Candle
    38 to 4532  // Black candle
)

/**
 * The glass lantern lens.
 */
val LENS = 4542

/**
 * A cut sapphire.
 */
val SAPPHIRE = 1607

/**
 * The steel bullseye lantern with no lens.
 */
val BULLSEYE_FRAME = 4544

/**
 * The bullseye lantern with a glass lens and no oil.
 */
val EMPTY_BULLSEYE = 4546

/**
 * The sapphire lantern with no oil.
 */
val EMPTY_SAPPHIRE_LANTERN = 4700

/**
 * Each unlit bullseye lantern with a glass lens, and the sapphire lantern it becomes.
 */
val SAPPHIRE_LANTERNS = mapOf(
    EMPTY_BULLSEYE to EMPTY_SAPPHIRE_LANTERN,
    4548 to 4701
)

/**
 * The Crafting level needed to fit a glass lens.
 */
val LENS_LEVEL = 49

/**
 * The Crafting level needed to fit a sapphire.
 */
val SAPPHIRE_LEVEL = 20

/**
 * The inventory slot holding the item with [id], whichever way round the two items were used.
 */
fun ItemOnItemEvent.slotOf(id: Int) = if (usedItemId == id) usedItemIndex else targetItemIndex

/**
 * Whether the player can fit a sapphire, telling them if not.
 */
fun ItemOnItemEvent.canFitSapphire(): Boolean {
    if (plr.crafting.level < SAPPHIRE_LEVEL) {
        plr.sendMessage("You need a Crafting level of $SAPPHIRE_LEVEL to fit the sapphire.")
        return false
    }
    return true
}

// A candle goes into the empty candle lantern, and stays there.
for ((candle, lantern) in CANDLE_LANTERNS) {
    useItem(candle).onItem(EMPTY_CANDLE_LANTERN) {
        plr.inventory[slotOf(candle)] = null
        plr.inventory[slotOf(EMPTY_CANDLE_LANTERN)] = Item(lantern)
        plr.sendMessage("You put the candle in the lantern.")
    }
}

// A glass lens finishes the bullseye lantern.
useItem(LENS).onItem(BULLSEYE_FRAME) {
    if (plr.crafting.level < LENS_LEVEL) {
        plr.sendMessage("You need a Crafting level of $LENS_LEVEL to fit the lens.")
    } else {
        plr.inventory[slotOf(LENS)] = null
        plr.inventory[slotOf(BULLSEYE_FRAME)] = Item(EMPTY_BULLSEYE)
        plr.sendMessage("You fit the lens into the lantern.")
    }
}

// A cut sapphire can be the lens instead.
useItem(SAPPHIRE).onItem(BULLSEYE_FRAME) {
    if (canFitSapphire()) {
        plr.inventory[slotOf(SAPPHIRE)] = null
        plr.inventory[slotOf(BULLSEYE_FRAME)] = Item(EMPTY_SAPPHIRE_LANTERN)
        plr.sendMessage("You fit the sapphire into the lantern.")
    }
}

// On a finished, unlit bullseye lantern the sapphire takes the place of the glass lens, which the player keeps.
for ((bullseye, sapphireLantern) in SAPPHIRE_LANTERNS) {
    useItem(SAPPHIRE).onItem(bullseye) {
        if (canFitSapphire()) {
            plr.inventory[slotOf(SAPPHIRE)] = Item(LENS)
            plr.inventory[slotOf(bullseye)] = Item(sapphireLantern)
            plr.sendMessage("You swap the lantern's lens for the sapphire.")
        }
    }
}
