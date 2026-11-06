package game.npc.spawn.tzhaar

import api.predef.*
import api.shop.dsl.ShopHandler
import io.luna.game.model.item.shop.Currency
import io.luna.game.model.item.shop.RestockPolicy
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueUtils

/**
 * TzHaar-Hur-Tel, who sells TzHaar weapons and the obsidian cape.
 */
val HUR_TEL = 2620

/**
 * TzHaar-Hur-Lek, who sells ores and gems.
 */
val HUR_LEK = 2622

/**
 * TzHaar-Mej-Roh, who sells runes.
 */
val MEJ_ROH = 2623

val EQUIPMENT_STORE = "TzHaar-Hur-Tel's Equipment Store"
val ORE_AND_GEM_STORE = "TzHaar-Hur-Lek's Ore and Gem Store"
val RUNE_STORE = "TzHaar-Mej-Roh's Rune Store"

/**
 * The TzHaar shops sell at 150% of an item's value and buy at 15%, changing by 2% for each item of stock. The 150%
 * matches the Tokkul prices on Jagex's TzHaar rewards page of May 2006.
 */
val TZHAAR_PRICING = Shop.StockPricing(0.15, 0.02, 1.5)

/**
 * What a TzHaar-Hur answers to "What are you then?".
 */
val HUR_LINES = arrayOf(
    "Silly JalYt, I am TzHaar-Hur, one of the crafters for this city.",
    "There are the wise TzHaar-Mej who guide us, the mighty TzHaar-Ket who guard us, and the swift TzHaar-Xil " +
        "who hunt for our food."
)

/**
 * A TzHaar shopkeeper's greeting. [whatDidYouCallMe] is the talk about the player's TzHaar name.
 */
fun greet(plr: Player, npc: Int, shop: String, whatDidYouCallMe: (Player) -> Unit) {
    plr.newDialogue()
        .npc(npc, *DialogueUtils.splitChatLines("Can I help you ${TzHaarNames.name(plr)}?"))
        .options("What do you have to trade?", { it.overlays.open(ShopInterface(world, shop)) },
                 "What did you call me?", { whatDidYouCallMe(it) },
                 "No I'm fine thanks.", { it.newDialogue().player("No I'm fine thanks.").open() })
        .open()
}

/**
 * TzHaar-Mej-Roh's answer to "What are you then?", which leads on to the other castes.
 */
fun mejRohWhatAreYou(plr: Player) {
    plr.newDialogue()
        .player("What are you then?")
        .npc(MEJ_ROH, *DialogueUtils.splitChatLines("Foolish JalYt, I am TzHaar-Mej, one of the mystics of this city."))
        .options("What other types are there?", {
            it.newDialogue()
                .player("What other types are there?")
                .npc(MEJ_ROH, *DialogueUtils.splitChatLines(
                    "There are the mighty TzHaar-Ket who guard us, the swift TzHaar-Xil who hunt for our food, and " +
                        "the skilled TzHaar-Hur who craft our homes and tools."))
                .open()
        }, "Ah ok then.", {
            it.newDialogue().player("Ah ok then.").open()
        })
        .open()
}

ShopHandler.create(EQUIPMENT_STORE) {
    currency = Currency.TOKKUL
    restock = RestockPolicy.BALANCED
    stockPricing = TZHAAR_PRICING
    sell {
        "Toktz-xil-ul" x 500 every 10
        "Toktz-xil-ak" x 1 every 100
        "Toktz-xil-ek" x 1 every 100
        "Tzhaar-ket-om" x 1 every 100
        "Toktz-mej-tal" x 1 every 100
        "Tzhaar-ket-em" x 1 every 100
        "Obsidian cape" x 1 every 100
        "Toktz-ket-xil" x 1 every 100
    }
    open { npc2 += HUR_TEL }
}

ShopHandler.create(ORE_AND_GEM_STORE) {
    currency = Currency.TOKKUL
    restock = RestockPolicy.BALANCED
    stockPricing = TZHAAR_PRICING
    sell {
        "Tin ore" x 5 every 100
        "Copper ore" x 5 every 100
        "Iron ore" x 2 every 100
        "Silver ore" zero 0 every 100
        "Coal" zero 0 every 100
        "Gold ore" zero 0 every 100
        "Mithril ore" zero 0 every 100
        "Adamantite ore" zero 0 every 100
        "Runite ore" zero 0 every 100
        "Uncut sapphire" x 1 every 100
        "Uncut emerald" x 1 every 100
        "Uncut ruby" zero 0 every 100
        "Uncut diamond" zero 0 every 100
        "Uncut dragonstone" zero 0 every 100
        "Uncut onyx" x 1 every 100
    }
    open { npc2 += HUR_LEK }
}

ShopHandler.create(RUNE_STORE) {
    currency = Currency.TOKKUL
    restock = RestockPolicy.BALANCED
    stockPricing = TZHAAR_PRICING
    sell {
        "Fire rune" x 5000 every 20
        "Water rune" x 5000 every 20
        "Air rune" x 5000 every 20
        "Earth rune" x 5000 every 20
        "Mind rune" x 5000 every 20
        "Body rune" x 5000 every 20
        "Chaos rune" x 2500 every 95
        "Death rune" x 2500 every 105
    }
    open { npc2 += MEJ_ROH }
}

npc1(HUR_TEL) { greet(plr, HUR_TEL, EQUIPMENT_STORE) { TzHaarNames.whatDidYouCallMe(it, HUR_TEL, *HUR_LINES) } }
npc1(HUR_LEK) { greet(plr, HUR_LEK, ORE_AND_GEM_STORE) { TzHaarNames.whatDidYouCallMe(it, HUR_LEK, *HUR_LINES) } }
npc1(MEJ_ROH) { greet(plr, MEJ_ROH, RUNE_STORE) { TzHaarNames.whatDidYouCallMe(it, MEJ_ROH) { mejRohWhatAreYou(it) } } }
