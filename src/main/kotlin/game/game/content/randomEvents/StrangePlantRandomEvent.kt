/*
 * Random-event behavior adapted from LostCityRS/Content (377-wip) and LostCityRS/Engine-TS,
 * engine commit 1d25566cb53e7af1b1cb18ade8af996316c19614.
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
 *
 */
package game.content.randomEvents

import api.predef.world
import api.predef.ext.addItem
import api.predef.ext.removeNpc
import game.player.Sound
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.block.Animation.AnimationPriority

/**
 * Lost City's stationary Strange plant. Fruit ripens after forty-five ticks and becomes unavailable
 * after another forty-five. Ignoring it triggers the source growth animation, hostile transformation
 * and poisonous owner-only combat. Picked fruit releases the personal slot before the plant withers.
 * NPC delays include the engine's extra tick: five plus two ticks to attack, and twenty-three to wither.
 *
 * @author lare96
 */
class StrangePlantRandomEvent(override val player: Player, override val npc: Npc) : RandomEventEncounter {
    /**
     * Live stages stay with this exact NPC. No phase, reward entitlement or world reference is saved.
     *
     * @author lare96
     */
    private enum class Phase { GROWING, RIPE, CHANGING, HOSTILE, PICKED, LEAVING }

    private var phase = Phase.GROWING
    private var age = 0

    override val resumeKey get() = RandomEvents.Kind.STRANGE_PLANT.name
    override val blocksNewEvents get() = phase == Phase.GROWING || phase == Phase.RIPE || phase == Phase.CHANGING
    override val awaitingResponse get() = phase == Phase.RIPE

    init {
        npc.isWandering = false
        npc.navigator.cancel()
        npc.animation(Animation(348))
        player.playSound(Sound.TRIFFID_APPEAR)
    }

    override fun tick(): Boolean {
        if (npc.state != EntityState.ACTIVE || !npc.isAlive) return false
        age++
        when (phase) {
            Phase.GROWING -> if (age == 45) next(Phase.RIPE)
            Phase.RIPE -> if (age == 45) {
                npc.animation(Animation(351, 60, AnimationPriority.NORMAL))
                next(Phase.CHANGING)
            }
            Phase.CHANGING -> {
                if (age == 5) npc.transform(408)
                if (age == 7) {
                    next(Phase.HOSTILE)
                    RandomEvents.answered(this)
                    RandomEvents.beginHostile(this)
                }
            }
            Phase.HOSTILE -> if (age % 20 == 0) {
                if (npc.position.z != player.position.z ||
                    npc.combat.target == null && !npc.position.isWithinDistance(player.position, 3)) {
                    npc.combat.target = null
                    npc.navigator.cancel()
                    npc.animation(Animation(355))
                    next(Phase.LEAVING)
                } else if (npc.combat.target !== player) npc.combat.attack(player)
            }
            Phase.PICKED -> if (age >= 23) return false
            Phase.LEAVING -> if (age >= 2) return false
        }
        return true
    }

    override fun talk(visitor: Player) {
        if (!RandomEvents.owns(this)) return
        if (visitor !== player) {
            visitor.sendMessage("It's not here for you.")
            return
        }
        if (phase == Phase.GROWING) {
            player.sendMessage("The fruit isn't ready to be picked yet...")
            return
        }
        if (!awaitingResponse) return
        next(Phase.PICKED)
        RandomEvents.answered(this)
        val fruit = Item(464)
        if (!player.inventory.add(fruit)) world.addItem(fruit, player.position, player)
        player.playSound(Sound.PICK)
        player.sendMessage("You pick the fruit from the plant.")
        npc.animation(Animation(350))
    }

    override fun close() {
        npc.combat.target = null
        npc.navigator.cancel()
        if (npc.state != EntityState.INACTIVE) world.removeNpc(npc)
    }

    /**
     * Checks ownership and the hostile phase for both outgoing attacks and player target admission.
     */
    internal fun canFight(other: Mob): Boolean = phase == Phase.HOSTILE && other === player &&
        RandomEvents.owns(this) && npc.state == EntityState.ACTIVE && npc.isAlive &&
        player.state == EntityState.ACTIVE && player.isAlive

    /**
     * Advances the live state and restarts that state's source timer.
     */
    private fun next(phase: Phase) {
        this.phase = phase
        age = 0
    }
}
