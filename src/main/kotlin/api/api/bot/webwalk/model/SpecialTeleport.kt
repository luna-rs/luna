package api.bot.webwalk.model

import api.bot.webwalk.plan.BankItems
import game.item.teleport.Camulet
import game.item.teleport.Camulet.camuletCharges
import game.item.teleport.EctophialTeleport
import game.item.teleport.EnchantedLyre
import game.item.teleport.TeleportCrystal
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot

/**
 * Owned native teleport items with a single destination and no jewellery destination menu.
 * Item charge stages come from their player handlers. Camulet charges are personal and shared by all copies,
 * so carrying or banking more Camulets cannot multiply the usable charge budget.
 *
 * @property pieces Native charged item IDs and the charges held by each physical stage.
 * @property destination Native landing anchor.
 * @property option Inventory packet option activating the item.
 * @author lare96
 */
enum class SpecialTeleport(val pieces: Map<Int, Int>, val destination: Position, val option: Int = 1) {
    CRYSTAL(TeleportCrystal.CHARGED.mapIndexed { index, id -> id to 4 - index }.toMap(), TeleportCrystal.LLETYA),
    LYRE(EnchantedLyre.NEXT_CHARGE.keys.mapIndexed { index, id -> id to 4 - index }.toMap(), EnchantedLyre.RELLEKKA),
    ECTOPHIAL(mapOf(EctophialTeleport.FULL to 1), EctophialTeleport.DESTINATION),
    CAMULET(mapOf(Camulet.CAMULET to Camulet.MAX_CHARGES), Camulet.TEMPLE, 4);

    /**
     * Separate planner currency prevents collision with ordinary jewellery names.
     */
    val chargeKey: String get() = "special_$name"

    /**
     * Counts live owned charges, optionally including items that the trip can really withdraw from its bank.
     */
    fun charges(bot: Bot, bank: BankItems? = null): Int {
        val owned = pieces.mapValues { (id, _) -> bot.inventory.computeAmountForId(id).toLong() +
            bot.equipment.computeAmountForId(id) + (bank?.amount(id) ?: 0) }
        if (this == CAMULET) return if (owned.values.any { it > 0 }) bot.camuletCharges.coerceIn(0, Camulet.MAX_CHARGES) else 0
        return owned.entries.sumOf { (id, amount) -> amount * pieces.getValue(id) }
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    companion object {
        /**
         * Resolves generated teleport keys and the planner's namespaced charge keys.
         */
        fun named(key: String?): SpecialTeleport? = entries.firstOrNull { it.name == key || it.chargeKey == key }
    }
}
