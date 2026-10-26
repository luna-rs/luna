/*
 * Gameplay rules adapted from LostCityRS/Content, commit 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
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
package game.content.rangingGuild

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.player.Sound
import io.luna.game.action.ActionType
import io.luna.game.action.impl.LockedAction
import io.luna.game.model.EntityState
import io.luna.game.model.LocalProjectile
import io.luna.game.model.Position
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.combat.AmmoType
import io.luna.game.model.mob.combat.CombatFormula
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.Varp
import io.luna.game.model.`object`.GameObject

/**
 * Persistent ten-shot rounds, bronze ammunition and the original independently rolled target rings.
 * All state changes run on the game thread through ordinary NPC and object interactions.
 *
 * @author lare96
 */
object ArcheryCompetition {
    const val TICKET = 1464
    const val ARROW = 882
    val JUDGE = Position(2669, 3418)
    val targets = setOf(Position(2679, 3426), Position(2679, 3428), Position(2681, 3425),
        Position(2681, 3427), Position(2682, 3425))
    internal var Player.count by Attr.int().persist("ranging_guild_count")
    internal var Player.score by Attr.int().persist("ranging_guild_score")
    internal var Player.hit by Attr.int { 11 }.persist("ranging_guild_hit")

    internal fun ready(p: Player): Boolean = p.state == EntityState.ACTIVE && p.isAlive && !p.isLocked &&
        !p.combat.inCombat() && p.actions.size(ActionType.STRONG) == 0

    internal fun near(p: Player, npc: Npc, id: Int): Boolean = ready(p) && npc.id == id &&
        npc.state == EntityState.ACTIVE && npc.position.isWithinDistance(JUDGE, 20) &&
        p.position.isWithinDistance(npc.position, 3)

    fun talk(p: Player, judge: Npc) {
        if (!near(p, judge, 693)) return
        if (p.count == 11) { claim(p, judge); return }
        if (p.count in 1..10) {
            p.sendMessage("You have ${11 - p.count} shots left and ${p.score} points.")
            p.newDialogue().options("Buy ten replacement arrows (100 coins)", {
                p.overlays.closeWindows(); replaceArrows(p, judge)
            }, "Remind me of the rules", { rules(p) }, "Continue shooting", { p.overlays.closeWindows() }).open()
        } else p.newDialogue().options("Enter the competition (200 coins)", {
            p.overlays.closeWindows(); enter(p, judge)
        }, "How does it work?", { rules(p) }, "No thanks", { p.overlays.closeWindows() }).open()
    }

    private fun rules(p: Player) {
        p.newDialogue().npc(693, "Entry costs 200 coins and includes ten bronze arrows.",
            "Equip a bow and bronze arrows. Shoot from behind the haystack.",
            "You get ten shots and one archery ticket for every ten points.",
            "Talk to me when you finish. The ticket merchant sells prizes.").open()
    }

    fun enter(p: Player, judge: Npc): Boolean {
        if (!near(p, judge, 693) || p.count != 0) return false
        if (!purchase(p, Item(995, 200), Item(ARROW, 10))) return false
        p.count = 1; p.score = 0; p.hit = 11
        refresh(p)
        p.sendMessage("You pay 200 coins. Equip your bow and bronze arrows, then shoot at a target.")
        return true
    }

    fun replaceArrows(p: Player, judge: Npc): Boolean {
        if (!near(p, judge, 693) || p.count !in 1..10) return false
        if (p.inventory.contains(ARROW) || p.equipment.contains(ARROW)) {
            p.sendMessage("You still have bronze arrows. Equip them to continue."); return false
        }
        return purchase(p, Item(995, 100), Item(ARROW, 10))
    }

    /**
     * Preflights the whole exchange, including stack overflow and the slot released by exact payment.
     */
    internal fun purchase(p: Player, payment: Item, reward: Item): Boolean {
        val inventory = p.inventory
        val available = inventory.computeAmountForId(payment.id)
        if (available < payment.amount) { p.sendMessage("You don't have enough ${payment.itemDef.name}."); return false }
        val freed = if (available == payment.amount) 1 else 0
        if (inventory.computeSpaceFor(reward) > inventory.computeRemainingSize() + freed) {
            p.sendMessage("Make room in your inventory first."); return false
        }
        check(inventory.remove(payment))
        check(inventory.add(reward))
        return true
    }

    fun claim(p: Player, judge: Npc): Boolean {
        if (!near(p, judge, 693) || p.count != 11) return false
        val tickets = p.score / 10
        if (tickets > 0 && !p.inventory.add(Item(TICKET, tickets))) {
            p.sendMessage("Make room for your tickets, then speak to me again."); return false
        }
        p.sendMessage("You scored ${p.score} points and earned $tickets archery tickets.")
        p.count = 0; p.score = 0; p.hit = 11
        refresh(p)
        return true
    }

    fun shoot(p: Player, target: GameObject): Boolean {
        if (!ready(p) || target.id != 2513 || target.state != EntityState.ACTIVE || target.position !in targets ||
            !p.position.isWithinDistance(target.position, 10) ||
            !world.collisionManager.raycast(p.position, target.position)) return false
        if (p.count !in 1..10) {
            p.sendMessage(if (p.count == 11) "Speak to the Competition Judge to collect your tickets."
                else "Speak to the Competition Judge to enter first."); return false
        }
        if (p.position.isWithinDistance(target.position, 4)) {
            p.sendMessage("Stand behind the haystack, at least five paces from the target."); return false
        }
        if (p.equipment.computeIdForIndex(Equipment.WEAPON) !in AmmoType.BRONZE_ARROW.def.weapons) {
            p.sendMessage("You need to equip a bow to shoot at the target."); return false
        }
        if (p.equipment.computeIdForIndex(Equipment.AMMUNITION) != ARROW) {
            p.sendMessage("You must equip bronze arrows for this competition."); return false
        }
        val result = roll(CombatFormula.calculateRangedAttackRoll(p))
        p.overlays.closeWindows()
        p.submitAction(Shot(p, target, result))
        return true
    }

    /**
     * Samples the source's five independent ring checks; later successes replace earlier ones.
     */
    internal fun roll(attack: Int, pick: (Int) -> Int = { rand().nextInt(it) }): Int {
        val accuracy = pick(attack.coerceAtLeast(1))
        var result = 11
        if (pick(accuracy + 1) > pick(1001)) result = 9
        if (pick(accuracy + 1) > pick(2001)) result = pick(4) + 5
        if (pick(accuracy + 1) > pick(3001)) result = pick(3) + 2
        if (pick(accuracy + 1) > pick(4001)) result = 1
        if (pick(accuracy + 1) > pick(7001)) result = 0
        return result
    }

    internal fun points(hit: Int): Int = when (hit) {
        0 -> 100; 1 -> 50; in 2..4 -> 30; in 5..8 -> 20; in 9..10 -> 10; else -> 0
    }

    fun refresh(p: Player) {
        p.sendVarp(Varp(156, p.count)); p.sendVarp(Varp(157, p.score)); p.sendVarp(Varp(158, p.hit))
    }

    fun logout(p: Player) { p.actions.first(Shot::class.java)?.interrupt() }

    /**
     * Reserves ammunition and one attempt on launch. Interrupted flights remain spent misses, preventing rerolls.
     * Only a live player still at the launch tile receives the delayed score, XP and result window.
     *
     * @author lare96
     */
    internal class Shot(p: Player, private val target: GameObject, private val result: Int) : LockedAction(p) {
        private val origin = p.position
        private var elapsed = 0
        private var resolved = false

        override fun onLock() {
            mob.walking.clear()
            check(mob.equipment.remove(Item(ARROW)))
            mob.count++
            mob.hit = 11
            refresh(mob)
            mob.interact(target)
            mob.animation(Animation(426))
            mob.graphic(AmmoType.BRONZE_ARROW.def.startGraphic)
            mob.playSound(Sound.ARROWLAUNCH2)
            LocalProjectile.followPath(world.context).setSourcePosition(origin).setTargetPosition(target.position)
                .setId(10).setStartHeight(40).setEndHeight(36).setTiming(15, 55).setInitialSlope(5).build().display()
        }

        override fun run(): Boolean {
            if (resolved || mob.state != EntityState.ACTIVE || !mob.isAlive || mob.position != origin ||
                target.state != EntityState.ACTIVE) return true
            if (++elapsed < 3) return false
            resolved = true
            mob.hit = result
            val points = points(result)
            mob.score += points
            if (points > 0) mob.ranged.addExperience(points / 2.0)
            refresh(mob)
            mob.overlays.open(Result())
            mob.sendText(when (points) {
                100 -> "Bulls-Eye!"; 50 -> "Hit Yellow!"; 30 -> "Hit Red!"; 20 -> "Hit Blue!"
                10 -> "Hit Black!"; else -> "Missed!"
            }, 567)
            if (mob.count == 11) mob.sendMessage("Round complete! Speak to the Competition Judge for your tickets.")
            return true
        }

        override fun onUnlock() { resolved = true; mob.animation(Animation(-1)) }
    }

    /**
     * Identifies the native target result window and its close button.
     *
     * @author lare96
     */
    class Result : StandardInterface(446)
}
