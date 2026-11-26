package game.content.rogueTrader

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import game.content.quests.Quest
import game.content.quests.QuestGates
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.mob.Player

/**
 * Rogue Trader: helping Ali Morrisane find suppliers of runes, blackjacks and clothes, which he then sells from his
 * stall in Al Kharid.
 *
 * @author TheLining
 */
object RogueTrader {

    const val ALI = 1862
    const val AUBURY = 553
    const val SIAMUN = 1983

    /**
     * The Blackjack seller as spawned. He only shows once The Feud is finished, as [BLACKJACK_SELLER_SHOWN].
     */
    const val BLACKJACK_SELLER = 1876
    const val BLACKJACK_SELLER_SHOWN = 2548
    const val STREET_URCHIN = 1868

    /**
     * The Feud's progress, which shows the Blackjack seller at [FEUD_FINISHED].
     */
    const val FEUD_VARBIT = 334
    const val FEUD_FINISHED = 28

    const val COINS = 995

    /**
     * What the street urchin charges to rob the Blackjack seller.
     */
    const val URCHIN_FEE = 100

    const val GENERAL_STOCK = "Ali's Discount Wares"
    const val ELEMENTAL_RUNES = "Ali's Elemental Runes"
    const val CATALYTIC_RUNES = "Ali's Catalytic Runes"
    const val OFFENSIVE_BLACKJACKS = "Ali's Offensive Blackjacks"
    const val DEFENSIVE_BLACKJACKS = "Ali's Defensive Blackjacks"
    const val DESERT_CLOTHES = "Ali's Desert Clothes"
    const val MENAPHITE_CLOTHES = "Ali's Menaphite Clothes"

    /**
     * How far Aubury's rune deal has got.
     */
    object Runes {
        const val NONE = 0
        const val AGREED = 1
        const val DELIVERED = 2
    }

    /**
     * Which blackjacks the Blackjack seller sends Ali.
     */
    object Blackjacks {
        const val NONE = 0
        const val OFFENSIVE = 1
        const val DEFENSIVE = 2
    }

    /**
     * Which clothes Siamun sends Ali.
     */
    object Clothes {
        const val NONE = 0
        const val DESERT = 1
        const val MENAPHITE = 2
    }

    /**
     * How far the street urchin's robbery has got.
     */
    object Urchin {
        const val NONE = 0
        const val CALLED = 1
        const val PAID = 2
        const val ROBBED = 3
    }

    var Player.rogueTraderStarted by Attr.boolean().persist("rogue_trader_started")
    var Player.carpetDiscount by Attr.boolean().persist("rogue_trader_carpet_discount")
    var Player.runeDeal by Attr.int().persist("rogue_trader_runes")
    var Player.smallCasketOpened by Attr.boolean().persist("rogue_trader_small_casket")
    var Player.largeCasketOpened by Attr.boolean().persist("rogue_trader_large_casket")
    var Player.blackjackStock by Attr.int().persist("rogue_trader_blackjacks")
    var Player.blackjackNews by Attr.boolean().persist("rogue_trader_blackjack_news")
    var Player.metBlackjackSeller by Attr.boolean().persist("rogue_trader_met_seller")
    var Player.urchinStage by Attr.int().persist("rogue_trader_urchin")
    var Player.clothesStock by Attr.int().persist("rogue_trader_clothes")
    var Player.clothesNews by Attr.boolean().persist("rogue_trader_clothes_news")
    var Player.rogueTraderThanked by Attr.boolean().persist("rogue_trader_thanked")

    /**
     * Whether [plr] can take part: Rogue Trader needs The Feud.
     */
    fun canStart(plr: Player) = !plr.isBot && QuestGates.passes(plr, Quest.THE_FEUD)

    /**
     * Whether Aubury can be asked about runes, which needs Rune Mysteries.
     */
    fun canAskAubury(plr: Player) = QuestGates.passes(plr, Quest.RUNE_MYSTERIES)

    /**
     * Whether Siamun can be asked about clothes, which needs Icthlarin's Little Helper.
     */
    fun canAskSiamun(plr: Player) = QuestGates.passes(plr, Quest.ICTHLARINS_LITTLE_HELPER)

    /**
     * Whether all three suppliers are sending Ali their goods.
     */
    fun allSuppliers(plr: Player) = plr.runeDeal == Runes.DELIVERED &&
        plr.blackjackStock != Blackjacks.NONE && plr.clothesStock != Clothes.NONE

    /**
     * Opens the shop [name] for [plr].
     */
    fun openShop(plr: Player, name: String) = plr.overlays.open(ShopInterface(world, name))

    /**
     * Opens the blackjacks Ali stocks.
     */
    fun openBlackjacks(plr: Player) =
        openShop(plr, if (plr.blackjackStock == Blackjacks.OFFENSIVE) OFFENSIVE_BLACKJACKS else DEFENSIVE_BLACKJACKS)

    /**
     * Opens the clothes Ali stocks.
     */
    fun openClothes(plr: Player) =
        openShop(plr, if (plr.clothesStock == Clothes.DESERT) DESERT_CLOTHES else MENAPHITE_CLOTHES)

    /**
     * The junk Ali sells a player who only meant to say goodbye.
     */
    private val JUNK = listOf(2138, 590, 1265, 1925, 946)

    /**
     * Ali sells [plr] one piece of junk for 10 coins, if they have the coins and the room.
     */
    fun sellJunk(plr: Player) {
        val junk = Item(JUNK.random())
        if (plr.inventory.computeAmountForId(COINS) < 10 || !plr.inventory.hasSpaceFor(junk)) {
            return
        }
        plr.inventory.remove(Item(COINS, 10))
        plr.inventory.add(junk)
        plr.sendMessage("Despite your best efforts Ali M still manages to sell you some junk.")
    }
}
