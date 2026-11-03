/*
 * Adapted from LostCityRS/Content, scripts/macro events/scripts/thieving/macro_event_poisonous_gas.rs2.
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

import api.predef.thieving

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.ext.*
import game.player.Sound
import game.skill.thieving.searchForTraps.ThievingChest
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Hit.HitType
import io.luna.game.model.`object`.GameObject
import kotlin.random.Random

/**
 * Lost City's owner-only chest gas timer. The native trap sequence gives no loot or experience, replaces the
 * chest for the source's explicitly estimated hundred ticks, and deals green-hit damage without poisoning.
 * The saved coordinate resumes its timer only while the actual visible gas chest still exists.
 *
 * @author lare96
 */
object ChestGasRandomEvent {
    /**
     * Native gas chest and the source's temporary lifetime, which is marked guessed in Lost City.
     */
    const val GAS_CHEST = 2141
    private const val LIFETIME = 100

    private var Player.gasPosition by Attr.string().persist("thieving_chest_gas")
    private var Player.nextGasTick by Attr.long()

    /**
     * Resolves the saved source position against an actual active gas object in this player's visibility view.
     */
    fun hazard(player: Player): GameObject? {
        val values = player.gasPosition.split(':').mapNotNull { it.toIntOrNull() }
        if (values.size != 3) return null
        val position = Position(values[0], values[1], values[2])
        return player.world.objects.findAll(position).filter {
            it.id == GAS_CHEST && it.state == EntityState.ACTIVE && it.view.isViewableFor(player)
        }.findFirst().orElse(null)
    }

    /**
     * Source trap-disarming timing before the gas appears. Every delayed stage rechecks the original chest;
     * a chest replaced by another player never receives a stale gas transformation or delayed reward.
     */
    fun start(player: Player, chest: GameObject, type: ThievingChest): Boolean {
        if (chest.state != EntityState.ACTIVE || player.thieving.level < type.level) return false
        player.submitAction(object : LockedAction(player, false, 2) {
            private var stage = 0
            override fun onLock() {
                mob.sendMessage("You search the chest for traps.")
                mob.sendMessage("You find a trap on the chest,")
            }
            override fun run(): Boolean {
                if (mob.state != EntityState.ACTIVE || !mob.isAlive || chest.state != EntityState.ACTIVE) return true
                when (stage++) {
                    0 -> {
                        mob.sendMessage("You disable the trap.")
                        mob.playSound(Sound.LOCKED)
                    }
                    1 -> {
                        mob.sendMessage("You open the chest.")
                        mob.playSound(Sound.CHEST_OPEN)
                    }
                    else -> {
                        mob.animation(Animation(536))
                        val view = if (type.globalRefresh) ChunkUpdatableView.globalView() else ChunkUpdatableView.localView(mob)
                        val gas = GameObject.createDynamic(mob.context, GAS_CHEST, chest.position,
                            chest.objectType, chest.direction, view)
                        if (!mob.world.objects.register(gas)) return true
                        val position = gas.position
                        mob.gasPosition = "${position.x}:${position.y}:${position.z}"
                        mob.nextGasTick = mob.world.currentTick + 2
                        mob.world.scheduleOnce(1) {
                            if (mob.state == EntityState.ACTIVE && mob.isAlive) mob.speak("I better run!")
                        }
                        mob.world.scheduleOnce(LIFETIME) {
                            if (gas.state == EntityState.ACTIVE) mob.world.objects.register(GameObject.createDynamic(
                                chest.context, chest.id, chest.position, chest.objectType, chest.direction, view))
                        }
                        return true
                    }
                }
                return false
            }
        })
        return true
    }

    /**
     * Source poison_gas checks one row and one-tile distance every four ticks. It never installs a poison status
     * or damages bystanders. Missing/expired gas clears the saved target, including after a world restart.
     */
    fun tick(player: Player) {
        if (player.gasPosition.isEmpty()) return
        val gas = hazard(player) ?: run { player.gasPosition = ""; return }
        if (player.world.currentTick < player.nextGasTick || !player.isAlive) return
        player.nextGasTick = player.world.currentTick + 4
        if (player.position.z == gas.position.z && player.position.y == gas.position.y &&
            player.position.isWithinDistance(gas.position, 1)) {
            player.playSound(Sound.GAS_HISS)
            player.damage(Random.nextInt(2, 4), HitType.POISON)
        }
    }
}
