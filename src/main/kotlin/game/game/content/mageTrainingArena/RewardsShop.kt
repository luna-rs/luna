package game.content.mageTrainingArena

import api.predef.*
import api.predef.ext.*
import game.content.mageTrainingArena.MageTrainingArena.Points
import game.content.mageTrainingArena.MageTrainingArena.bonesToPeachesUnlocked
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.net.msg.out.WidgetItemsMessageWriter

/**
 * The Rewards Guardian's Magic Training Arena Shop, where Pizazz Points of all four kinds buy the arena's rewards.
 * Prices are the Knowledge Base's of May 2006, in Telekinetic, Alchemist, Enchantment and Graveyard points.
 *
 * @author TheLining
 */
object RewardsShop {

    const val INTERFACE = 15944
    const val ITEMS = 15948
    private const val TELEKINETIC_LINE = 15955
    private const val ENCHANTMENT_LINE = 15956
    private const val ALCHEMIST_LINE = 15957
    private const val GRAVEYARD_LINE = 15958

    /**
     * The Bones to Peaches spell, shown in the shop as an item.
     */
    const val BONES_TO_PEACHES = 6926

    /**
     * A reward: what it is, its price in each kind of point, and the wand it upgrades, if any.
     */
    class Reward(val id: Int, val telekinetic: Int, val alchemist: Int, val enchantment: Int, val graveyard: Int,
                 val upgrades: Int = -1) {
        val prices get() = listOf(telekinetic, alchemist, enchantment, graveyard)
    }

    /**
     * The stock, in the shop's slot order: wands first, so the three "(Upgrade)" labels sit under the upgrades.
     */
    val STOCK = listOf(
        Reward(6908, 30, 30, 300, 30),
        Reward(6910, 60, 60, 600, 60, upgrades = 6908),
        Reward(6912, 150, 200, 1500, 150, upgrades = 6910),
        Reward(6914, 240, 240, 2400, 240, upgrades = 6912),
        Reward(6916, 400, 450, 4000, 400),
        Reward(6918, 350, 400, 3000, 350),
        Reward(6920, 120, 120, 1200, 120),
        Reward(6922, 175, 225, 1500, 175),
        Reward(6924, 450, 500, 5000, 450),
        Reward(6889, 500, 550, 6000, 500),
        Reward(BONES_TO_PEACHES, 200, 300, 2000, 200),
        Reward(4695, 1, 1, 15, 1),
        Reward(4696, 1, 1, 15, 1),
        Reward(4698, 1, 1, 15, 1),
        Reward(4697, 1, 1, 15, 1),
        Reward(4694, 1, 1, 15, 1),
        Reward(4699, 1, 1, 15, 1),
        Reward(564, 0, 0, 5, 0),
        Reward(562, 0, 1, 5, 1),
        Reward(561, 0, 1, 0, 1),
        Reward(560, 2, 1, 20, 1),
        Reward(563, 2, 0, 0, 0),
        Reward(566, 2, 2, 25, 2),
        Reward(565, 2, 2, 25, 2)
    )

    /**
     * Opens the shop for [plr].
     */
    fun open(plr: Player) {
        plr.overlays.open(ShopInterface())
        plr.queue(WidgetItemsMessageWriter(ITEMS, STOCK.map { Item(it.id) }))
        refresh(plr)
    }

    private fun refresh(plr: Player) {
        plr.sendText(MageTrainingArena.points(plr, Points.TELEKINETIC), TELEKINETIC_LINE)
        plr.sendText(MageTrainingArena.points(plr, Points.ENCHANTMENT), ENCHANTMENT_LINE)
        plr.sendText(MageTrainingArena.points(plr, Points.ALCHEMIST), ALCHEMIST_LINE)
        plr.sendText(MageTrainingArena.points(plr, Points.GRAVEYARD), GRAVEYARD_LINE)
    }

    /**
     * The shop window.
     */
    private class ShopInterface : StandardInterface(INTERFACE)

    private fun isOpen(plr: Player) = plr.overlays.has(ShopInterface::class.java)

    /**
     * The shop's "Value" option.
     */
    fun value(plr: Player, slot: Int) {
        val reward = STOCK.getOrNull(slot) ?: return
        if (!isOpen(plr)) return
        plr.sendMessage("${itemName(reward.id)} costs ${reward.telekinetic} Telekinetic, ${reward.alchemist} " +
                            "Alchemist, ${reward.enchantment} Enchantment and ${reward.graveyard} Graveyard Pizazz " +
                            "Points.")
    }

    /**
     * The shop's "Buy" option.
     */
    fun buy(plr: Player, slot: Int) {
        val reward = STOCK.getOrNull(slot) ?: return
        if (!isOpen(plr)) return
        val kinds = Points.values()
        if (kinds.indices.any { MageTrainingArena.points(plr, kinds[it]) < reward.prices[it] }) {
            plr.sendMessage("You don't have enough Pizazz Points to buy that.")
            return
        }
        if (reward.id == BONES_TO_PEACHES) {
            if (plr.bonesToPeachesUnlocked) {
                plr.sendMessage("You already know the Bones to Peaches spell.")
                return
            }
            plr.bonesToPeachesUnlocked = true
            pay(plr, reward)
            plr.sendMessage("You have learnt the Bones to Peaches spell.")
            return
        }
        if (reward.upgrades != -1) {
            if (!takeWand(plr, reward.upgrades)) {
                plr.sendMessage("You need ${addArticle(itemName(reward.upgrades)).lowercase()} to upgrade.")
                return
            }
            plr.inventory.add(Item(reward.id))
            pay(plr, reward)
            return
        }
        val item = Item(reward.id)
        if (!plr.inventory.hasSpaceFor(item)) {
            plr.sendMessage("You don't have enough inventory space.")
            return
        }
        plr.inventory.add(item)
        pay(plr, reward)
    }

    private fun pay(plr: Player, reward: Reward) {
        Points.values().forEachIndexed { index, kind -> MageTrainingArena.removePoints(plr, kind, reward.prices[index]) }
        refresh(plr)
    }

    /**
     * Takes the wand [id] being upgraded from [plr]'s inventory, or from their hand.
     */
    private fun takeWand(plr: Player, id: Int): Boolean {
        val index = plr.inventory.computeIndexForId(id)
        if (index != -1) {
            plr.inventory[index] = null
            return true
        }
        if (plr.equipment[Equipment.WEAPON]?.id == id && plr.inventory.hasSpaceFor(Item(id))) {
            plr.equipment[Equipment.WEAPON] = null
            return true
        }
        return false
    }
}
