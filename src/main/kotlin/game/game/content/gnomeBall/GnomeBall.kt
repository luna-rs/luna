/*
 * Gameplay rules and identifiers adapted from LostCityRS/Content, commit 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
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
package game.content.gnomeBall

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.combat.death.DeathHookHandler
import api.predef.*
import api.predef.ext.*
import game.obj.doors.DoorPassageAction
import game.obj.doors.DoorType
import game.player.Sound
import game.skill.Skills
import io.luna.game.model.*
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.*
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.overlay.WalkableInterface
import io.luna.game.model.mob.varp.Varp
import io.luna.game.model.`object`.GameObject
import io.luna.net.msg.out.SoundMessageWriter

/**
 * Owns pitch sessions and shared NPC possession on the game thread. Personal balls are held separately
 * from the temporary match ball, so leaving or restoring a saved player cannot duplicate either.
 *
 * @author lare96
 */
object GnomeBall {
    const val BALL = 751
    const val GATE = 2394
    val GOAL = Position(2404, 3488)
    val EXIT = Position(2382, 3488)
    val PASS = PlayerContextMenuOption(2, "Pass-to", false)
    private val sessions = mutableMapOf<Player, Session>()
    private val gnomes = mutableMapOf<Npc, Possession>()
    private val cooldowns = mutableMapOf<Npc, Long>()
    private var clock = 0L
    internal var Player.heldBalls by Attr.int().persist("gnome_ball_held")
    internal var Player.rewardBall by Attr.boolean().persist("gnome_ball_reward")
    internal var Player.matchBall by Attr.boolean().persist("gnome_ball_temporary")

    /**
     * Exact four-part pitch boundary from the source's gnomeball_zones table.
     */
    fun inside(p: Position): Boolean = p.z == 0 && (
        p.x in 2387..2394 && p.y in 3481..3496 || p.x in 2398..2406 && p.y in 3481..3496 ||
        p.x in 2384..2407 && p.y in 3484..3492 || p.x == 2383 && p.y in 3487..3489)

    fun session(player: Player): Session? = sessions[player]
    fun hasBall(player: Player): Boolean = player.matchBall && player.equipment[Equipment.WEAPON]?.id == BALL

    /**
     * Returns only the NPC holding this player's current match ball, excluding other players' possessions.
     * Wingers return it automatically; a defending baller must be tackled through the normal interaction.
     */
    fun holder(player: Player): Npc? {
        val owner = session(player) ?: return null
        return gnomes.entries.firstOrNull { it.value.owner === owner }?.key
    }
    private fun live(player: Player): Boolean = player.state == EntityState.ACTIVE && player.isAlive && inside(player.position)
    private fun ready(player: Player): Boolean = live(player) && !player.isLocked && !player.combat.inCombat()
    private fun nearby(player: Player, npc: Npc, distance: Int): Boolean = npc.state == EntityState.ACTIVE &&
        inside(npc.position) && player.position.isWithinDistance(npc.position, distance) &&
        world.collisionManager.raycast(player.position, npc.position)

    fun prepare(player: Player): Boolean {
        if (player.controllers.primary.javaClass != PlayerController::class.java || !player.isAlive ||
            player.isLocked || player.combat.inCombat()) return false
        val hands = listOf(Equipment.WEAPON, Equipment.SHIELD).mapNotNull { player.equipment[it] }
        if (!player.inventory.hasSpaceForAll(hands)) {
            player.sendMessage("You need inventory space to put away your weapon and shield.")
            return false
        }
        for (slot in listOf(Equipment.WEAPON, Equipment.SHIELD)) if (player.equipment.occupied(slot)) player.equipment.unequip(slot)
        return true
    }

    fun enter(player: Player): Boolean {
        if (sessions.containsKey(player)) return true
        if (!inside(player.position) || !prepare(player)) return false
        val count = player.inventory.computeAmountForId(BALL)
        if (player.heldBalls.toLong() + count > Int.MAX_VALUE - 1) return false
        if (count > 0) {
            if (!player.inventory.remove(Item(BALL, count))) return false
            player.heldBalls += count
            player.sendMessage("The referee will return your own balls when you leave.")
        }
        val session = Session(player)
        sessions[player] = session
        player.controllers.register(GnomeBallController(player))
        player.contextMenu.show(PASS)
        player.overlays.open(Scoreboard())
        player.sendVarp(Varp(143, 0))
        return true
    }

    fun openGate(player: Player, gate: GameObject) {
        if (gate.id != GATE || !gate.position.isWithinDistance(EXIT, 3)) return
        if (player.actions.first(Crossing::class.java) == null) player.submitAction(Crossing(player, gate))
    }

    /**
     * Uses the shared clipped door crossing; area entry also checks anyone entering by another route.
     *
     * @author lare96
     */
    private class Crossing(player: Player, gate: GameObject) : DoorPassageAction(player, gate,
        DoorType(GATE, 2395, null, null, null, 3)) {
        override fun checkAccess(entering: Boolean): Boolean = inside(mob.position) || prepare(mob)
    }

    fun referee(player: Player, npc: Npc) {
        if (npc.id != 635 || !nearby(player, npc, 2)) return
        if (!inside(player.position)) { claim(player); return }
        if (session(player) == null && !enter(player)) return
        player.newDialogue().options("Play Gnome Ball", {
            player.overlays.closeWindows()
            start(player, npc)
        }, "Explain the rules", {
            player.newDialogue().npc(635, 588, "Take a ball, dodge or tackle the defenders, and shoot",
                "from in front of the goal. Pass to green wingers",
                "for help. Five goals wins a ball and experience.").open()
        }, "Collect my saved balls", { player.overlays.closeWindows(); claim(player) }).open()
    }

    fun start(player: Player, referee: Npc): Boolean {
        val session = session(player) ?: return false
        if (!ready(player) || referee.id != 635 || !nearby(player, referee, 2) || session.inPlay()) return false
        if (player.equipment.occupied(Equipment.WEAPON)) return false
        session.started = true
        referee.animation(Animation(201))
        player.playSound(Sound.WHISTLE)
        session.flight = true
        projectile(referee.position, player.position)
        session.later(2) { session.giveBall() }
        return true
    }

    fun shoot(player: Player, goal: GameObject, roll: Double = rand().nextDouble()): Boolean {
        val session = session(player) ?: return false
        val distance = player.position.computeLongestDistance(GOAL)
        if (!ready(player) || !session.started || !hasBall(player) || goal.id != 2393 || goal.position != GOAL ||
            player.position.x >= GOAL.x || distance > 10 || !world.collisionManager.raycast(player.position, GOAL)) return false
        takeBall(player)
        session.flight = true
        player.animation(Animation(783))
        player.queue(SoundMessageWriter(Sound.THROW.id, 1, 10))
        val scored = roll < Skills.successRate(25, 300 - distance * 5, player.ranged.level.coerceIn(1, 99))
        projectile(player.position, if (scored) GOAL else GOAL.translate(1, 1))
        session.later(3) {
            session.flight = false
            if (scored) {
                session.score++
                player.playSound(Sound.SCORE)
                player.sendMessage("You score!")
                if (session.score == 5) {
                    session.score = 0
                    player.rewardBall = true
                    player.ranged.addExperience(30.0)
                    player.agility.addExperience(30.0)
                    player.sendMessage("You win! Your prize ball will be returned when you leave.")
                } else player.ranged.addExperience((session.score + 3).toDouble())
                player.sendVarp(Varp(143, session.score))
            } else player.sendMessage("Your shot misses.")
        }
        return true
    }

    fun pass(player: Player, winger: Npc): Boolean {
        val session = session(player) ?: return false
        if (!ready(player) || !hasBall(player) || winger.id != 633 || !nearby(player, winger, 10) ||
            winger in gnomes || (cooldowns[winger] ?: 0) > clock) return false
        takeBall(player)
        player.animation(Animation(783))
        player.playSound(Sound.THROW)
        projectile(player.position, winger.position)
        hold(winger, session, 634, clock + 10)
        winger.animation(Animation(200))
        cooldowns[winger] = clock + 8
        return true
    }

    fun pass(player: Player, recipient: Player): Boolean {
        val source = session(player) ?: return false
        val target = session(recipient) ?: return false
        if (player === recipient || !ready(player) || !ready(recipient) || recipient.overlays.hasWindow() ||
            !hasBall(player) || target.inPlay() || recipient.equipment.occupied(Equipment.WEAPON) ||
            !player.position.isWithinDistance(recipient.position, 10) ||
            !world.collisionManager.raycast(player.position, recipient.position)) return false
        takeBall(player)
        source.started = true
        target.started = true
        target.flight = true
        player.animation(Animation(783))
        player.playSound(Sound.THROW)
        recipient.playSound(Sound.THROW)
        projectile(player.position, recipient.position)
        target.later(2) {
            target.giveBall()
            if (session(player) === source && live(player)) player.playSound(Sound.CATCH)
        }
        return true
    }

    /**
     * Reward balls can also be passed outside the pitch, using an inventory item on another player.
     */
    fun passPersonal(player: Player, recipient: Player): Boolean {
        if (inside(player.position) || inside(recipient.position) || player === recipient ||
            player.state != EntityState.ACTIVE || recipient.state != EntityState.ACTIVE ||
            !player.isAlive || !recipient.isAlive || player.isLocked || recipient.isLocked ||
            player.combat.inCombat() || recipient.combat.inCombat() || recipient.overlays.hasWindow() ||
            player.equipment.occupied(Equipment.WEAPON) || recipient.equipment.occupied(Equipment.WEAPON) ||
            player.controllers.primary.javaClass != PlayerController::class.java ||
            recipient.controllers.primary.javaClass != PlayerController::class.java ||
            !player.position.isWithinDistance(recipient.position, 10) ||
            !world.collisionManager.raycast(player.position, recipient.position) || !player.inventory.remove(BALL)) return false
        recipient.equipment.set(Equipment.WEAPON, Item(BALL))
        player.animation(Animation(783)); recipient.animation(Animation(782))
        player.playSound(Sound.THROW); recipient.playSound(Sound.THROW)
        player.playSound(Sound.CATCH); recipient.playSound(Sound.CATCH)
        projectile(player.position, recipient.position)
        return true
    }

    fun tackle(player: Player, npc: Npc, roll: Double = rand().nextDouble()): Boolean {
        val session = session(player) ?: return false
        val base = npc.baseId
        if (!ready(player) || !session.started || hasBall(player) || session.flight || base !in listOf(621, 625, 629) ||
            !nearby(player, npc, 1) || (cooldowns[npc] ?: 0) > clock) return false
        val held = gnomes[npc]
        if (held != null && (held.owner == null || held.owner !== session)) return false
        cooldowns[npc] = clock + 4
        val success = roll < Skills.successRate(30, if (base == 629 || held != null) 220 else 200,
            player.agility.level.coerceIn(1, 99))
        player.animation(Animation(if (success) 778 else 780))
        npc.animation(Animation(if (success) 203 else 204))
        if (success) {
            if (held != null) { release(npc); session.giveBall() }
            hold(npc, null, base + 2, clock + 11)
        }
        return true
    }

    internal fun intercept(npc: Npc, player: Player, roll: Double = rand().nextDouble()): Boolean {
        val session = session(player) ?: return false
        if (!ready(player) || !hasBall(player) || npc.baseId !in listOf(621, 625, 629) ||
            npc in gnomes || (cooldowns[npc] ?: 0) > clock || !nearby(player, npc, 1)) return false
        cooldowns[npc] = clock + 7
        if (roll < Skills.successRate(30, 200, player.agility.level.coerceIn(1, 99))) {
            npc.animation(Animation(207))
            hold(npc, null, npc.baseId + 3, clock + 7)
        } else {
            takeBall(player)
            hold(npc, session, npc.baseId + 1, Long.MAX_VALUE)
            npc.animation(Animation(210)); player.animation(Animation(779))
            player.damage(rand().nextInt(2) + 1)
            player.sendMessage("The gnome tackles you and takes the ball!")
        }
        return true
    }

    private fun hold(npc: Npc, owner: Session?, id: Int, until: Long) {
        val locked = npc.walking.isLocked
        npc.navigator.cancel(); npc.walking.clear(); npc.walking.isLocked = true
        gnomes[npc] = Possession(owner, until, locked)
        npc.transform(id)
    }

    private fun release(npc: Npc) {
        val held = gnomes.remove(npc) ?: return
        npc.resetTransform()
        npc.walking.isLocked = held.locked
    }

    internal fun tick(npcs: Collection<Npc>) {
        clock++
        sessions.values.toList().forEach { session ->
            if (!live(session.player)) leave(session.player) else session.tick()
        }
        for ((npc, held) in gnomes.toMap()) {
            if (npc.state != EntityState.ACTIVE || !inside(npc.position) ||
                held.owner?.let { session(it.player) !== it } == true) { release(npc); continue }
            if (clock < held.until) continue
            val owner = held.owner
            release(npc)
            if (owner != null && npc.baseId == 633 && !owner.inPlay()) {
                owner.flight = true
                projectile(npc.position, owner.player.position)
                npc.animation(Animation(201))
                owner.later(2) {
                    owner.player.playSound(Sound.THROW)
                    owner.giveBall()
                }
            }
        }
        cooldowns.entries.removeIf { it.value <= clock || it.key.state != EntityState.ACTIVE }
        for (npc in npcs) {
            if (npc.state != EntityState.ACTIVE || !inside(npc.position) || npc in gnomes) continue
            if (npc.id == 636 && clock % 3 == 0L && rand().nextBoolean()) npc.animation(Animation(211 + rand().nextInt(8)))
            if (npc.id !in listOf(621, 625, 629) || (cooldowns[npc] ?: 0) > clock) continue
            val target = sessions.keys.filter { hasBall(it) && ready(it) && nearby(it, npc, 6) }
                .minByOrNull { it.position.computeLongestDistance(npc.position) } ?: continue
            if (target.position.isWithinDistance(npc.position, 1)) intercept(npc, target)
            else if (clock % 4 == 0L) npc.navigator.navigate(target, false, false)
        }
    }

    private fun takeBall(player: Player) {
        if (hasBall(player)) player.equipment.set(Equipment.WEAPON, null)
        player.matchBall = false
    }

    fun leave(player: Player) {
        val previous = sessions.remove(player)
        if (player.matchBall) {
            if (player.equipment[Equipment.WEAPON]?.id == BALL) player.equipment.set(Equipment.WEAPON, null)
            else player.inventory.remove(BALL)
            player.matchBall = false
        }
        gnomes.filterValues { it.owner === previous && previous != null }.keys.toList().forEach(::release)
        if (player.controllers.primary is GnomeBallController) player.controllers.unregister()
        player.contextMenu.hide(PASS)
        if (player.overlays.getOverlay(Scoreboard::class.java) != null) player.overlays.closeWalkable()
        player.sendVarp(Varp(143, 0))
        if (player.isAlive) claim(player)
    }

    fun claim(player: Player) {
        if (session(player) != null) return
        if (player.rewardBall && player.heldBalls < Int.MAX_VALUE) { player.heldBalls++; player.rewardBall = false }
        val inventoryCount = minOf(player.heldBalls, player.inventory.computeRemainingSize())
        if (inventoryCount > 0 && player.inventory.add(Item(BALL, inventoryCount))) player.heldBalls -= inventoryCount
        if (player.heldBalls > 0 && player.bank.add(Item(BALL, player.heldBalls))) player.heldBalls = 0
        if (player.heldBalls > 0) player.sendMessage("Make room and speak to the referee to collect your saved balls.")
    }

    fun login(player: Player) {
        val stranded = inside(player.position)
        leave(player)
        if (stranded) player.move(EXIT)
    }

    fun launch() {
        DeathHookHandler.addPlayerHook(GnomeBallController::class.java) {
            leave(victim)
            DeathHookHandler.defaultPlayerHook?.invoke(this)
        }
        world.schedule(1) { tick(world.locator.findNpcs(Position(2396, 3488), 20) { true }) }
    }

    private fun projectile(from: Position, to: Position) {
        LocalProjectile.followPath(world.context).setSourcePosition(from).setTargetPosition(to)
            .setId(55).setStartHeight(40).setEndHeight(36).setTiming(0, 60).setInitialSlope(15).build().display()
    }

    /**
     * A shared NPC can carry only one player's ball or recover from one tackle at a time.
     *
     * @author lare96
     */
    private data class Possession(val owner: Session?, val until: Long, val locked: Boolean)

    /**
     * The native score overlay, identified separately so cleanup leaves unrelated overlays alone.
     *
     * @author lare96
     */
    class Scoreboard : WalkableInterface(4958)

    /**
     * Delayed effects belong to this session instance and are discarded when it leaves the pitch.
     *
     * @author lare96
     */
    class Session(val player: Player) {
        var started = false
            internal set
        var score = 0
            internal set
        internal var flight = false
        private val pending = mutableListOf<Pair<Long, () -> Unit>>()
        fun inPlay(): Boolean = hasBall(player) || flight || gnomes.values.any { it.owner === this }
        internal fun later(ticks: Int, action: () -> Unit) { pending.add(clock + ticks to action) }
        internal fun tick() {
            val due = pending.filter { it.first <= clock }
            pending.removeAll(due.toSet())
            for ((_, action) in due) if (session(player) === this && live(player)) action()
        }
        internal fun giveBall() {
            flight = false
            if (session(player) !== this || !live(player) || player.equipment.occupied(Equipment.WEAPON)) return
            player.matchBall = true
            player.equipment.set(Equipment.WEAPON, Item(BALL))
            player.animation(Animation(782))
            player.playSound(Sound.CATCH)
        }
    }
}
