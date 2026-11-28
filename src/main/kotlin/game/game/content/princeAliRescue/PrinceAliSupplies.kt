package game.content.princeAliRescue

import game.content.princeAliRescue.PrinceAliRescue as Quest
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player

/**
 * Ned's rope and wig services and Aggie's disguise mixture.
 *
 * @author lare96
 */
object PrinceAliSupplies {
    const val ASK_WIG = "Could you make me a wig?"
    const val MAKE_WIG = "Here are three balls of wool."
    const val ASK_ROPE = "I'd like some rope."
    const val BUY_ROPE = "I'll pay 15 coins for some rope."
    const val MAKE_ROPE = "Here are four balls of wool."
    const val ASK_PASTE = "Could you think of a way to make skin paste?"
    const val MAKE_PASTE = "Yes please. Mix me some skin paste."
    const val ROPE_PRICE = 15

    fun wig(player: Player, npc: Npc) {
        player.newDialogue().npc(Quest.NED, "I could try knitting a wig. I'll need three balls of wool.")
            .options(MAKE_WIG, { p ->
                if (Quest.helping(p)) exchange(p, npc, Quest.WIG, listOf(Item(Quest.WOOL, 3)),
                    "Ned knits the wool into a plain wig.")
            }, "I'll come back with the wool.", { }).open()
    }

    fun rope(player: Player, npc: Npc) {
        player.newDialogue().npc(Quest.NED, "I can sell you rope for 15 coins or make it from four balls of wool.")
            .options(BUY_ROPE, {
                exchange(it, npc, Quest.ROPE, listOf(Item(Quest.COINS, ROPE_PRICE)), "Ned sells you a coil of rope.")
            }, MAKE_ROPE, {
                exchange(it, npc, Quest.ROPE, listOf(Item(Quest.WOOL, 4)), "Ned twists the wool into a coil of rope.")
            }, "I'll come back later.", { }).open()
    }

    fun paste(player: Player, npc: Npc) {
        player.newDialogue().npc(Quest.AGGIE, "I mix ashes, flour and water, then add redberries for colour.")
            .options(MAKE_PASTE, { p ->
                if (!Quest.helping(p)) return@options
                val water = if (p.inventory.contains(Quest.BUCKET_OF_WATER)) Quest.BUCKET_OF_WATER else Quest.JUG_OF_WATER
                exchange(p, npc, Quest.SKIN_PASTE, listOf(Item(Quest.ASHES), Item(Quest.FLOUR), Item(water),
                    Item(Quest.REDBERRIES)), "Aggie stirs the ingredients in her cauldron and hands you the skin paste.")
            }, "I'll come back later.", { }).open()
    }

    fun dyeWig(player: Player) {
        if (player.inventory.containsAll(Quest.WIG, Quest.YELLOW_DYE)) {
            player.inventory.updateAll(listOf(Item(Quest.BLONDE_WIG)), listOf(Item(Quest.WIG), Item(Quest.YELLOW_DYE)))
            player.sendMessage("You dye the wig blonde.")
        }
    }

    private fun exchange(player: Player, npc: Npc, result: Int, ingredients: List<Item>, message: String) {
        if (!Quest.nearby(player, npc)) return
        val inventory = player.inventory
        val output = Item(result)
        when {
            ingredients.any { !inventory.contains(it) } -> player.sendMessage("You don't have everything you need.")
            !inventory.hasSpaceFor(output) && ingredients.none {
                it.id != Quest.COINS || inventory.computeAmountForId(it.id) == it.amount
            } -> player.sendMessage("Make room in your inventory first.")
            else -> {
                inventory.updateAll(listOf(output), ingredients)
                player.newDialogue().give(output, message, false).open()
            }
        }
    }
}
