/*
 * Adapted from LostCityRS/Content, scripts/macro events/scripts/mining.
 * MIT License
 *
 * Copyright (c) 2023-2026 Lost City
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package game.content.randomEvents


import api.predef.ext.*
import game.player.Sound
import game.skill.mining.Pickaxe
import io.luna.game.model.LocalProjectile
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.path.route.LineOfSight

/**
 * Recoverable pickaxe-head hazards from Lost City's mining pool. The actual selected tool becomes its
 * handle, and only its matching owned head can restore it through native item-on-item interaction.
 *
 * @author lare96
 */
object MiningRandomEvents {
    /**
     * Native pickaxe, broken form, detached head and Nurmof repair price from the source item metadata.
     *
     * @author lare96
     */
    enum class Parts(val pickaxe: Pickaxe, val broken: Int, val head: Int, val repairCost: Int) {
        BRONZE(Pickaxe.BRONZE, 468, 480, 0),
        IRON(Pickaxe.IRON, 470, 482, 0),
        STEEL(Pickaxe.STEEL, 472, 484, 17),
        MITHRIL(Pickaxe.MITHRIL, 474, 486, 43),
        ADAMANT(Pickaxe.ADAMANT, 476, 488, 107),
        RUNE(Pickaxe.RUNE, 478, 490, 1100)
    }

    /**
     * The source's shared pickaxe handle, including when the detached tool was worn.
     */
    const val HANDLE = 466

    /**
     * Detaches one real pickaxe and launches its head to a nearby tile with an unobstructed walking ray.
     * Ground-item ownership and expiry retain the native source loot lifetime rather than granting a new tool.
     */
    fun loseHead(player: Player, pickaxe: Pickaxe): Boolean {
        val parts = Parts.entries.first { it.pickaxe == pickaxe }
        val world = player.world
        val from = player.position
        val candidates = (-7..7).flatMap { x -> (-7..7).map { y -> from.translate(x, y) } }.filter {
            it != from && from.isWithinDistance(it, 7) && !world.collisionManager.isBlocked(it, false) &&
                LineOfSight.hasLineOfWalk(world.collisionManager.view(false), it.z,
                    from.x, from.y, it.x, it.y, 1, 1, 1, 1, 0)
        }
        val landing = candidates.randomOrNull() ?: from
        val delay = RandomEventDelay.start(player, 3) ?: return false
        try {
            if (!replacePickaxe(player, pickaxe, HANDLE)) {
                delay.close()
                return false
            }
            player.animation(Animation(642))
            player.playSound(Sound.LOST_PICKAXE)
            player.sendMessage("You swing your pick at the rock.")
            val cycles = 12 + from.computeLongestDistance(landing) * 10
            if (landing != from) LocalProjectile.followPath(player.context).setSourcePosition(from).setTargetPosition(landing)
                .setId(309).setStartHeight(35).setEndHeight(0).setTiming(14, cycles)
                .setInitialSlope(16).setLaunchOffset(64).build().display()
            delay.after(RandomEventDelay.projectileTicks(cycles, 60)) {
                val head = world.addItem(parts.head, 1, landing, player)
                world.scheduleOnce(200) { world.items.expire(head) }
                delay.close()
            }
            return true
        } catch (failure: Throwable) {
            delay.close()
            throw failure
        }
    }

    /**
     * Changes only the matching current worn or carried pickaxe, preserving its slot on a full inventory.
     */
    internal fun replacePickaxe(player: Player, pickaxe: Pickaxe, replacement: Int): Boolean {
        if (player.equipment[Equipment.WEAPON]?.id == pickaxe.id) {
            player.equipment[Equipment.WEAPON] = Item(replacement)
            return true
        }
        val slot = player.inventory.computeIndexForId(pickaxe.id)
        if (slot < 0) return false
        player.inventory[slot] = Item(replacement)
        return true
    }

    /**
     * Reattaches a matching owned head and handle once, with both slots revalidated before the exchange.
     */
    fun reattach(player: Player, head: Int): Boolean {
        val parts = Parts.entries.firstOrNull { it.head == head } ?: return false
        val inventory = player.inventory
        val handleSlot = inventory.computeIndexForId(HANDLE)
        val headSlot = inventory.computeIndexForId(head)
        if (handleSlot < 0 || headSlot < 0) return false
        inventory.startBulkUpdate()
        try {
            inventory[headSlot] = null
            inventory[handleSlot] = Item(parts.pickaxe.id)
        } finally {
            inventory.finishBulkUpdate()
        }
        player.playSound(Sound.ATTACH_HANDLE)
        player.sendMessage("You carefully attach the two pieces together.")
        return true
    }
}
