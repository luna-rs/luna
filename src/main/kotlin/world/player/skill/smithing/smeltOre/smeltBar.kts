package world.player.skill.smithing.smeltOre

import api.predef.*
import com.google.common.collect.ImmutableList
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.AmountInputInterface
import world.player.skill.smithing.BarType
import world.player.skill.smithing.Smithing

/**
 * An enum representing all types of ores that can be used with a furnace.
 */
enum class SmeltOre(val barType: BarType, val useItem: Item) {
    TIN(barType = BarType.BRONZE,
        useItem = Item.byName("Tin ore")),
    COPPER(barType = BarType.BRONZE,
           useItem = Item.byName("Copper ore")),
    IRON(barType = BarType.IRON,
         useItem = Item.byName("Iron ore")),
    COAL(barType = BarType.STEEL,
         useItem = Item.byName("Coal")),
    SILVER(barType = BarType.SILVER,
           useItem = Item.byName("Silver ore")),
    GOLD(barType = BarType.GOLD,
         useItem = Item.byName("Gold ore")),
    MITHRIL(barType = BarType.MITHRIL,
            useItem = Item.byName("Mithril ore")),
    ADAMANTITE(barType = BarType.ADAMANT,
               useItem = Item.byName("Adamantite ore")),
    RUNITE(barType = BarType.RUNE,
           useItem = Item.byName("Runite ore"));

    companion object {
        val VALUES = ImmutableList.copyOf(values())
    }

    val useId = useItem.id
}

/**
 * Smelts the specified [barType] for [times] amount of times.
 */
fun smelt(plr: Player, barType: BarType, times: Int) {
    plr.submitAction(SmeltAction(plr, barType, times))
}

/**
 * Handles the make 1, make 5, make 10, and make X options for each bar type.
 */
fun smeltButtons(make1: Int, make5: Int, make10: Int, makeX: Int, barType: BarType) {
    button(make1) { smelt(plr, barType, 1) }
    button(make5) { smelt(plr, barType, 5) }
    button(make10) { smelt(plr, barType, 10) }
    button(makeX) {
        plr.interfaces.open(object : AmountInputInterface() {
            override fun onAmountInput(player: Player?, value: Int) {
                smelt(plr, barType, value)
            }
        })
    }
}

/* Use ore on furnace to smelt all or click furnace to open interface. */
for (furnaceId in Smithing.FURNACE_OBJECTS) {
    for (ore in SmeltOre.VALUES) {
        useItem(ore.useId).onObject(furnaceId) { smelt(plr, ore.barType, 28) }
    }
    object2(furnaceId) { plr.interfaces.open(SmeltingInterface()) }
}

/* Smelt 1, 5, 10, X buttons for all bars. */
smeltButtons(make1 = 3987, make5 = 3986, make10 = 2807, makeX = 2414, barType = BarType.BRONZE)
smeltButtons(make1 = 3991, make5 = 3990, make10 = 3989, makeX = 3988, barType = BarType.IRON)
smeltButtons(make1 = 3995, make5 = 3994, make10 = 3993, makeX = 3992, barType = BarType.SILVER)
smeltButtons(make1 = 3999, make5 = 3998, make10 = 3997, makeX = 3996, barType = BarType.STEEL)
smeltButtons(make1 = 4003, make5 = 4002, make10 = 4001, makeX = 4000, barType = BarType.GOLD)
smeltButtons(make1 = 7441, make5 = 7440, make10 = 6397, makeX = 4158, barType = BarType.MITHRIL)
smeltButtons(make1 = 7446, make5 = 7444, make10 = 7443, makeX = 7442, barType = BarType.ADAMANT)
smeltButtons(make1 = 7450, make5 = 7449, make10 = 7448, makeX = 7447, barType = BarType.RUNE)
