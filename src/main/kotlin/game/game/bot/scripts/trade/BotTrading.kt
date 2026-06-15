package game.bot.scripts.trade

object BotTrading {
     val BUYING_HEADERS = listOf(
        "BUYING:", "Buying", "BUYING", "Buyingg", "buying", "!! BUYING !!", "[BUYING]:", "NEED:",
        "NEEDING:", "WANTED:", "[BUY]:", "BUY:", "B:", "WTB:", "WANT TO BUY:", "LOOKING FOR:",
        "LOOKING 4:", "LF:", "LFB:", "NEED ASAP:", "BUYING ASAP:", "BUYING NOW:", "BUYING ALL:",
        "BUYING ANY:", "BUYING LOTS:", "PAYING FOR:", "PAYING:", "CAN PAY:", "I BUY:", "I'M BUYING:",
        "IM BUYING:", "buy:", "b:", "wtb:", "need:", "lf:",
        "buying -", "buying >", "buying ~",
    )

     val SELLING_HEADERS = listOf(
        "SELLING:", "Selling", "SELLING", "Sellingg", "selling", "!! SELLING !!", "[SELLING]:",
        "NEED GONE:", "[SALE]:", "SALE:", "SELL:", "S:", "WTS:", "FOR SALE:", "FS:", "OFFERING:",
        "SELLING ASAP:", "SELLING NOW:", "SELLING ALL:", "SELLING ANY:", "SELLING LOTS:",
        "CLEARING:", "CLEAROUT:", "CHEAP:", "CHEAP SALE:", "QUICK SALE:", "QS:", "TAKE:",
        "TAKE ALL:", "MUST GO:", "NEED SOLD:", "GETTING RID OF:", "I SELL:", "I'M SELLING:",
        "IM SELLING:", "sell:", "s:", "wts:", "fs:", "sale:", "selling -", "selling >",
        "selling ~"
    )

     val PER_ITEM_FOOTERS = listOf(
        "ea", "each", "for each", "for each one", "each one", "ea 1", "each 1", "each item",
        "ea item", "per item", "per", "per one", "per piece", "per unit", "a piece", "apiece",
        "a pop", "each pop", "for 1", "for one", "for every one", "every one", "every item",
        "every piece", "single", "single item", "single one", "one each", "one item", "1 item",
        "1 each", "1 ea", "1x", "x1", "/ea", "/each", "/item", "/1", "ea.", "each.", "per."
    )

     val SELL_BUNDLE_COINS_ONLY = listOf(
        "Fire sale! Trade me, coins only", "Trade me, selling all for gp", "Selling all, cash only",
        "Selling the lot, gp only", "Selling this pile, coins only", "Trade me, quick cash sale",
        "Selling all, no swaps", "Selling all, no item trades", "Cash only, trade me",
        "Coins only, trade me", "Selling bundle, gp only", "Selling bundle, cash only",
        "Selling the lot for coins", "Selling everything for gp", "Quick sale, coins only",
        "Need cash, selling all", "Need gp, selling all", "Selling cheap, coins only",
        "Selling cheap for gp", "Trade me, no swaps please", "Trade me, cash offers only",
        "Trade me, coin offers only", "Selling all, gp offers only", "Selling lot, gp offers",
        "Selling all, cash buyer needed", "Selling lot, cash buyer needed",
        "Selling bundle, no junk trades", "Selling all, no random items", "Selling all, pure gp",
        "Pure cash sale, trade me", "Straight gp trade, trade me", "Simple cash trade, trade me",
        "All for coins, trade me", "All for gp, trade me", "Take all for gp",
        "Take the lot, gp only", "Clearing items, coins only", "Clearing bank, gp only",
        "Need it gone, gp only", "Need coins fast, trade me", "Fast sale, cash only",
        "Fast sale, coins only", "Cheap lot, coins only", "Cheap bundle, gp only",
        "Selling my spare loot, gp only", "Selling extra items, cash only",
        "Selling all spares, coins only", "Trade me, paying in gp only"
    )

     val SELL_BUNDLE_EQUIV_VALUE = listOf(
        "Fire sale! Trade me, gp or swaps", "Trade me, selling all for value",
        "Selling all, gp or items", "Selling the lot, fair swaps ok",
        "Selling this pile, swaps ok", "Trade me, fair offers",
        "Selling all, taking offers", "Selling all, taking fair value",
        "Selling bundle, gp or items", "Selling bundle, swaps accepted",
        "Selling the lot, items accepted", "Selling everything, fair trade",
        "Quick sale, gp or swaps", "Taking gp or fair items", "Trade me, useful items ok",
        "Trade me, fair items ok", "Trade me, equal value ok", "Trade me, value offers ok",
        "Selling all, equal value ok", "Selling lot, equal value ok",
        "Selling bundle, equal value", "Selling all, fair swaps only",
        "Selling lot, fair swaps only", "Selling all, no junk swaps",
        "Selling lot, no junk swaps", "Selling all, good items ok",
        "Selling lot, useful items ok", "Selling bundle, useful swaps ok",
        "Selling all, gear or gp ok", "Selling all, supplies or gp ok",
        "Selling all, runes/gear/gp ok", "Selling lot, gear swaps ok",
        "Selling lot, supply swaps ok", "Trading all for fair value",
        "Trading bundle for value", "Swapping or selling all",
        "Buy or swap, trade me", "Swap or buy, trade me",
        "Selling all, best fair offer", "Selling lot, best fair offer",
        "Fair value gets it", "Best fair offer gets it",
        "Open to swaps, trade me", "Open to item trades",
        "Taking offers, trade me", "Taking decent offers",
        "Selling spare loot, gp/items", "Selling extra items, gp/items",
        "Selling all spares, fair value", "Trade me, gp or useful items"
    )
}