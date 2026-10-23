package game.item.teleport

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import io.luna.game.model.Position
import io.luna.game.model.mob.Player

/**
 * The elven teleport crystal, which takes the player to Lletya, and Eluned's re-enchanting of the empty crystal.
 *
 * @author TheLining
 */
object TeleportCrystal {

    /**
     * The charged teleport crystal item ids, from (4) to (1).
     */
    val CHARGED = listOf(6099, 6100, 6101, 6102)

    /**
     * The Tiny elf crystal item id, left once the last charge is used.
     */
    const val TINY_ELF_CRYSTAL = 6103

    /**
     * The crystal Eluned gives back for a Tiny elf crystal: Teleport crystal (3).
     */
    const val RECHARGED = 6100

    /**
     * The tile the crystal lands on in Lletya.
     */
    val LLETYA = Position(2328, 3170)

    /**
     * Eluned, who roves Isafdar.
     */
    const val ELUNED_ISAFDAR = 1679

    /**
     * Eluned in Lletya. The client shows her as [ELUNED_LLETYA_VISIBLE] only while varbit 797 (bit 0 of varp 518) is
     * set, during part of Mourning's End Part I. Otherwise [ELUNED_ISAFDAR] does the recharging.
     *
     * TODO Mourning's End Part I: set varbit 797 for the stage where Eluned waits in Lletya.
     */
    const val ELUNED_LLETYA = 2375

    /**
     * The NPC [ELUNED_LLETYA] appears as, used for her chathead.
     */
    const val ELUNED_LLETYA_VISIBLE = 2376

    /**
     * The coins item id.
     */
    const val COINS = 995

    /**
     * The price of the first recharge.
     */
    const val FIRST_RECHARGE_PRICE = 750

    /**
     * How much cheaper each recharge is than the one before.
     */
    const val RECHARGE_DISCOUNT = 150

    /**
     * The lowest price a recharge falls to.
     */
    const val MIN_RECHARGE_PRICE = 150

    /**
     * How many times Eluned has re-enchanted a crystal for this player.
     */
    var Player.teleportCrystalRecharges by Attr.int().persist("teleport_crystal_recharges")

    /**
     * The price Eluned asks this player for the next recharge.
     */
    val Player.teleportCrystalRechargePrice: Int
        get() = (FIRST_RECHARGE_PRICE - RECHARGE_DISCOUNT * teleportCrystalRecharges).coerceAtLeast(MIN_RECHARGE_PRICE)

    /**
     * Returns the item a charged crystal becomes after a teleport.
     *
     * @param id The charged crystal item id.
     * @return The crystal with one charge fewer, or [TINY_ELF_CRYSTAL] after the last charge.
     */
    fun nextStage(id: Int): Int {
        val index = CHARGED.indexOf(id)
        require(index >= 0) { "$id is not a charged teleport crystal." }
        return CHARGED.getOrNull(index + 1) ?: TINY_ELF_CRYSTAL
    }
}
