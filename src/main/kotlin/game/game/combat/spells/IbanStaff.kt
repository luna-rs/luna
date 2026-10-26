package game.combat.spells

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Player

/**
 * Iban's staff charge balance and Underground Pass well recharge.
 * The revision-377 balance belongs to the player, so bank transfers and switching staves cannot refill it.
 * Accounts without saved charge data start with a full staff, preserving existing ownership while quest reward
 * handling is unfinished. A successful Iban Blast consumes one charge against NPCs or two against players,
 * including splashes and zero hits; rejected casts and later impact cancellation do not alter that policy.
 *
 * @author lare96
 */
object IbanStaff {

    /**
     * The usable staff item.
     */
    const val STAFF = 1409

    /**
     * The Underground Pass well used to replenish the charge balance.
     */
    const val WELL = 3305

    /**
     * The original staff's capacity, shared by every staff owned by the same player.
     */
    const val CAPACITY = 120

    /**
     * Remaining casts' charge budget, retained across logout, death, and equipment changes.
     */
    private var Player.charges by Attr.int { CAPACITY }.persist("iban_staff_charges")

    /**
     * Returns the player's saved charge balance, bounded to the staff's capacity.
     */
    fun remaining(player: Player): Int = player.charges.coerceIn(0, CAPACITY)

    /**
     * Resolves the charge cost for the cast's original recipient.
     */
    private fun cost(victim: Mob): Int = if (victim is Player) 2 else 1

    /**
     * Checks charges before runes or experience are committed. A final single charge can still be used on an NPC.
     */
    fun canCast(player: Player, victim: Mob): Boolean {
        if (remaining(player) >= cost(victim)) return true
        player.sendMessage("You have no charges left on the staff.")
        return false
    }

    /**
     * Spends charges after rune requirements succeed, before launching the spell. Called once per accepted cast;
     * damage delivery, target death, and projectile cancellation cannot spend or refund this cost again.
     */
    fun consume(player: Player, victim: Mob) {
        val remaining = remaining(player)
        val cost = cost(victim)
        check(remaining >= cost) { "Iban Blast committed without enough staff charges." }
        player.charges = remaining - cost
    }

    /**
     * Recharges a staff used on the well after the interaction system has brought the player within reach.
     * The one-tick reference delay uses a timed lock, which cannot unlock a subsequent death or other hard lock.
     * Completion rechecks the player, inventory slot, and position before restoring the shared balance.
     */
    fun recharge(player: Player, index: Int) {
        if (player.state != EntityState.ACTIVE || !player.isAlive || player.isLocked ||
            player.inventory[index]?.id != STAFF) return
        val position = player.position
        player.sendMessage("You hold the staff above the well...")
        player.lock(1) {
            if (player.state == EntityState.ACTIVE && player.isAlive && player.position == position &&
                player.inventory[index]?.id == STAFF) {
                player.charges = CAPACITY
                player.sendMessage("...And feel the power of Zamorak flow through you.")
            }
        }
    }
}
