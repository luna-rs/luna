package game.content.coalTrucks

/*
 * Adapted from Lost City scripts/areas/area_seers/scripts/coal_trucks.rs2.
 */

import api.predef.ext.*
import game.player.Sound
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.`object`.GameObject

/**
 * Lost City's revision 377 coal trucks: all trucks share one personal, permanent 120-coal store (varp 6).
 * Using unnoted coal deposits all carried coal up to capacity; Remove-coal fills the available inventory slots.
 * Native truck placements come from Luna's selected revision 377 map, including both ends of the railway.
 * Transfers run on the game thread and account for the actual inventory change before updating storage.
 *
 * @author lare96
 */
object CoalTrucks {
    const val TRUCK = 2114
    const val COAL = 453
    const val CAPACITY = 120
    val MINE_TRUCK_POSITION = Position(2574, 3487)
    val DEPOT_TRUCK_POSITION = Position(2696, 3504)

    private val positions = setOf(MINE_TRUCK_POSITION, Position(2576, 3490), Position(2585, 3490),
        Position(2598, 3490), Position(2599, 3490), Position(2600, 3490), Position(2611, 3490),
        Position(2615, 3498), Position(2615, 3506), Position(2621, 3511),
        DEPOT_TRUCK_POSITION, Position(2698, 3507))

    fun storedCoal(player: Player): Int = player.varpManager.getValue(PersistentVarp.COAL_TRUCKS).coerceIn(0, CAPACITY)

    /**
     * Normalizes persisted stock on login, without resetting valid coal saved outside the railway area.
     */
    fun restore(player: Player) {
        player.varpManager.setValue(PersistentVarp.COAL_TRUCKS, storedCoal(player))
    }

    internal fun reachable(player: Player, truck: GameObject): Boolean =
        !player.isLocked && player.state == EntityState.ACTIVE && player.isAlive &&
                truck.world == player.world && truck.state == EntityState.ACTIVE && !truck.isHidden &&
                truck.id == TRUCK && truck.position in positions && player.position.isWithinDistance(truck.position, 1)

    /**
     * Deposits the carried unnoted coal, checking that the item originally selected still occupies its slot.
     */
    fun deposit(player: Player, truck: GameObject, index: Int, expectedId: Int = COAL) {
        if (!reachable(player, truck) || index !in 0 until player.inventory.capacity() ||
            player.inventory.computeIdForIndex(index) != expectedId) return
        if (expectedId != COAL) {
            player.sendMessage("The coal truck isn't meant to transport that.")
            return
        }
        val stored = storedCoal(player)
        if (stored >= CAPACITY) {
            player.sendMessage("The coal truck is full.")
            return
        }
        val inventory = player.inventory
        val before = inventory.computeAmountForId(COAL)
        val amount = minOf(before, CAPACITY - stored)
        if (amount <= 0) return
        val removed: Int
        inventory.startBulkUpdate()
        try {
            if (!inventory.remove(Item(COAL, amount))) return
            removed = before - inventory.computeAmountForId(COAL)
            player.varpManager.setAndSendValue(PersistentVarp.COAL_TRUCKS, stored + removed)
        } finally {
            inventory.finishBulkUpdate()
        }
        if (removed <= 0) return
        player.playSound(Sound.MINE)
        player.sendMessage(if (removed < before) "You put some of the coal in the truck."
                           else "You put the coal in the truck.")
    }

    /**
     * Removes as much saved coal as fits. A full pack or failed insertion leaves the saved remainder available.
     */
    fun withdraw(player: Player, truck: GameObject) {
        if (!reachable(player, truck)) return
        val stored = storedCoal(player)
        if (stored == 0) {
            player.sendMessage("There is no coal left in the truck.")
            return
        }
        val inventory = player.inventory
        val amount = minOf(stored, inventory.computeRemainingSize())
        if (amount == 0) {
            player.sendMessage("You do not have enough inventory space!")
            return
        }
        val before = inventory.computeAmountForId(COAL)
        val added: Int
        inventory.startBulkUpdate()
        try {
            if (!inventory.add(Item(COAL, amount))) return
            added = inventory.computeAmountForId(COAL) - before
            player.varpManager.setAndSendValue(PersistentVarp.COAL_TRUCKS, stored - added)
        } finally {
            inventory.finishBulkUpdate()
        }
        if (added <= 0) return
        player.playSound(Sound.MINE)
        player.sendMessage(if (added < stored) "You remove some of the coal from the truck."
                           else "You remove the coal from the truck.")
    }

    fun investigate(player: Player, truck: GameObject) {
        if (reachable(player, truck)) player.sendMessage("You have ${storedCoal(player)} of $CAPACITY coal stored in the trucks.")
    }
}
