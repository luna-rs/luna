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

import api.predef.mining

import api.predef.ext.*
import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import game.player.Sound
import game.skill.mining.Pickaxe
import game.skill.mining.Ore
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.model.Entity
import io.luna.game.model.EntityState
import io.luna.game.model.LocalGraphic
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.LocalProjectile
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.path.route.LineOfSight
import io.luna.game.model.`object`.GameObject

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
     * Exact original-rock to smoking-rock identities from the source macro_gas parameters.
     */
    val GAS_ROCKS = (2090..2111).associateWith { it + 29 }

    /**
     * The source preserves progress between smoking rocks until an explosion resets the counter.
     */
    private var Player.gasAttempts by Attr.int().persist("mining_gas_attempts")

    /**
     * A replacement belongs to its exact original rock; expiry must not overwrite a foreign replacement.
     */
    private val smoking = mutableMapOf<GameObject, GameObject>()

    /**
     * Owns the one-tick reissued click and its later native action, without claiming unrelated weak actions.
     *
     * @author lare96
     */
    private class GasContinuation(val gas: GameObject, val focus: Entity?) {
        var action: Action<Player>? = null
    }

    private val continuations = mutableMapOf<Player, GasContinuation>()

    /**
     * Finds this player's exact pending or running gas attempt, optionally for the original clicked rock.
     * Replaced objects, cancelled actions and a new foreign weak action cannot keep bot targeting deferred.
     */
    fun continuation(player: Player, original: GameObject? = null): GameObject? {
        val owned = continuations[player] ?: return null
        if (owned.gas.state != EntityState.ACTIVE || smoking[owned.gas] == null ||
            original != null && smoking[owned.gas] !== original) return null
        val action = owned.action
        val active = if (action == null) player.interactingWith === owned.focus && player.walking.isEmpty &&
            RandomEvents.canAct(player) && player.actions.size(ActionType.WEAK) == 0
        else !action.isFinished && player.actions.getAll<Action<*>>(ActionType.WEAK).singleOrNull() === action
        return if (active) owned.gas else null
    }

    /**
     * Cancels only the matching owned reissue/action. A later click or another weak action is left untouched.
     */
    fun cancelContinuation(player: Player, gas: GameObject): Boolean {
        val owned = continuations[player]?.takeIf { it.gas === gas } ?: return false
        continuations.remove(player)
        owned.action?.interrupt()
        return true
    }

    /**
     * Identifies the actual smoking replacement at a previously selected rock, without scanning remote mines.
     */
    fun hazardAt(position: Position): GameObject? = smoking.keys.firstOrNull {
        it.position == position && it.state == EntityState.ACTIVE
    }

    /**
     * Replaces one public source rock for sixty ticks and continues the triggering native mining attempt.
     * Private object views and already-replaced rocks do not create a second world-owned hazard.
     */
    fun startGas(player: Player, rock: GameObject): Boolean {
        val id = GAS_ROCKS[rock.id] ?: return false
        val world = player.world
        if (rock.state != EntityState.ACTIVE || !rock.view.isGlobal) return false
        val gas = GameObject.createDynamic(player.context, id, rock.position, rock.objectType, rock.direction, rock.view)
        if (!world.removeObject(rock)) return false
        if (!world.addObject(gas)) {
            world.addObject(GameObject.createDynamic(rock.context, rock.id, rock.position,
                rock.objectType, rock.direction, rock.view))
            return false
        }
        smoking[gas] = rock
        player.playSound(Sound.GAS_HISS)
        world.scheduleOnce(60) {
            continuations.filterValues { it.gas === gas }.keys.toList().forEach { cancelContinuation(it, gas) }
            if (smoking.remove(gas) === rock && gas.state == EntityState.ACTIVE) {
                world.removeObject(gas)
                if (world.objects.findAll(rock.position).noneMatch { rock.replaces(it) }) {
                    world.addObject(GameObject.createDynamic(rock.context, rock.id, rock.position,
                        rock.objectType, rock.direction, rock.view))
                }
            }
        }
        val pending = GasContinuation(gas, player.interactingWith)
        continuations.remove(player)?.action?.interrupt()
        continuations[player] = pending
        world.scheduleOnce(1) {
            if (continuations[player] === pending) {
                if (continuation(player) === gas) {
                    continuations.remove(player)
                    mineGas(player, gas)
                } else cancelContinuation(player, gas)
            }
        }
        return true
    }

    /**
     * Continues mining without ore or experience. Source reconstruction retains its fifth-roll explosion,
     * ten damage and persistent counter; the source itself flags the historical roll count as uncertain.
     */
    fun mineGas(player: Player, rock: GameObject) {
        val original = smoking[rock] ?: return
        val ore = Ore.ROCK_MAP[original.id] ?: return
        val pickaxe = Pickaxe.computePickType(player) ?: run { Pickaxe.sendNoPickaxeMessage(player); return }
        fun usable() = player.state == EntityState.ACTIVE && player.isAlive && !player.isLocked &&
            rock.state == EntityState.ACTIVE && smoking[rock] === original &&
            player.mining.level >= ore.level && !player.inventory.isFull && Pickaxe.hasPick(player, pickaxe) &&
            player.world.collisionManager.reached(player, rock, InteractionPolicy.STANDARD_SIZE)
        if (!usable()) return
        continuations.remove(player)?.action?.interrupt()
        val owned = GasContinuation(rock, rock)
        continuations[player] = owned
        player.interact(rock)
        player.sendMessage("You swing your pick at the rock.")
        player.animation(pickaxe.animation)
        val action = object : Action<Player>(player, ActionType.WEAK, false, pickaxe.speed - 1) {
            override fun run(): Boolean {
                if (!usable()) return true
                if (mob.gasAttempts++ < 4) {
                    delay = pickaxe.speed
                    mob.animation(pickaxe.animation)
                    return false
                }
                mob.gasAttempts = 0
                mob.animation(Animation.CANCEL)
                mob.playSound(Sound.GAS_EXPLOSION)
                LocalGraphic(mob.context, 157, 92, 0, rock.position, ChunkUpdatableView.globalView()).display()
                mob.damage(10)
                val current = Pickaxe.computePickType(mob) ?: return true
                val parts = Parts.entries.first { it.pickaxe == current }
                if (replacePickaxe(mob, current, parts.broken)) {
                    mob.sendMessage("A spark from your pickaxe ignites the gas!")
                    mob.sendMessage("The blast damages your pickaxe!")
                }
                return true
            }

            override fun onFinished() {
                if (continuations[player] === owned) continuations.remove(player)
            }
        }
        owned.action = action
        player.submitAction(action)
    }

    /**
     * Offers the native item-metadata repair price, then revalidates the exact broken item and owned payment.
     * Lost City identifies this dialogue as reconstructed from Bob; the repair transaction is source-backed.
     */
    fun offerRepair(player: Player, broken: Int) {
        val parts = Parts.entries.firstOrNull { it.broken == broken } ?: return
        val price = if (parts.repairCost == 0) "free" else "${parts.repairCost}gp"
        player.newDialogue().npc(594, "Quite badly damaged, but easy to repair.",
            "Would you like me to repair it for $price?")
            .options("Yes, please.", { repair(it, parts) }, "No, thank you.", { it.resetDialogues() }).open()
    }

    /**
     * Repairs one current broken pickaxe in place, charging no more than its native source metadata price.
     */
    internal fun repair(player: Player, parts: Parts): Boolean {
        player.resetDialogues()
        val inventory = player.inventory
        val slot = inventory.computeIndexForId(parts.broken)
        if (slot < 0) return false
        if (inventory.computeAmountForId(995) < parts.repairCost) {
            player.sendMessage("I'll need ${parts.repairCost}gp to repair that.")
            return false
        }
        inventory.startBulkUpdate()
        try {
            if (parts.repairCost > 0 && !inventory.remove(Item(995, parts.repairCost))) return false
            inventory[slot] = Item(parts.pickaxe.id)
        } finally {
            inventory.finishBulkUpdate()
        }
        player.sendMessage(if (parts.repairCost == 0) "Nurmof fixes your pickaxe for free!"
            else "Nurmof fixes your pickaxe for ${parts.repairCost}gp")
        return true
    }

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
