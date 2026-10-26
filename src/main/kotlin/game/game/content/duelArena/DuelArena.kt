/*
 * Rules and interface identifiers adapted from LostCityRS/Content, commit 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
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
package game.content.duelArena

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.combat.death.DeathHookHandler
import api.predef.*
import api.predef.ext.*
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerContextMenuOption
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.overlay.InventoryOverlayInterface
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.Varp
import io.luna.net.msg.out.WidgetItemsMessageWriter
import game.player.Animations
import io.luna.game.model.mob.block.Animation.AnimationPriority
import io.luna.game.model.mob.combat.damage.CombatDamageAction
import io.luna.game.model.mob.Mob
import io.luna.game.model.mob.combat.attack.*
import java.util.WeakHashMap
import io.luna.game.model.EntityType
import io.luna.game.model.Direction
import engine.combat.status.StatusEffectType

/**
 * Game-thread-owned duel negotiations and matches. Stake escrow is saved on each participant;
 * abandoned negotiations and server restarts refund it without dropping valuable items on the ground.
 *
 * @author lare96
 */
object DuelArena {
    val LOBBY = Position(3360, 3275)
    private val sessions = mutableMapOf<Player, Duel>()
    private val requests = mutableMapOf<Player, Pair<Player, Long>>()
    private val attacks = WeakHashMap<CombatAttack<*>, Duel>()
    private var Player.duelHistory by Attr.string().persist("duel_arena_history")
    var Player.duelEscrow by Attr.string().persist("duel_arena_escrow")
    val arenas = listOf(Position(3333, 3244), Position(3364, 3225), Position(3333, 3206),
        Position(3364, 3244), Position(3333, 3225), Position(3364, 3206))
    private val flowers = (2460..2468).toSet()
    fun session(player: Player): Duel? = sessions[player]
    fun scoreboard(player: Player) {
        player.overlays.open(StandardInterface(6308))
        val lines = player.duelHistory.split('|').filter { it.isNotEmpty() }
        for (i in 0..9) player.sendText(lines.getOrNull(i) ?: "", 6402 + i)
    }
    fun stopAttack(player: Player, attack: CombatAttack<*>): Boolean {
        val duel = session(player)
        val otherDuel = (attack.victim as? Player)?.let { session(it) }
        if (duel == null && otherDuel == null) return attacks.containsKey(attack)
        if (duel == null || attack.victim !is Player || !duel.fighting(player, attack.victim as Player)) return true
        val blocked = when (attack) {
            is MagicCombatAttack<*> -> DuelRules.NO_MAGIC.enabled(duel.rules)
            is RangedCombatAttack<*> -> DuelRules.NO_RANGED.enabled(duel.rules)
            is MeleeCombatAttack<*> -> DuelRules.NO_MELEE.enabled(duel.rules)
            else -> false
        }
        if (!blocked) attacks[attack] = duel
        return blocked
    }

    fun blockImpact(player: Player, other: Mob, source: CombatAttack<*>): Boolean {
        val original = attacks[source]
        if (original != null) return other !is Player || !original.fighting(player, other)
        return session(player) != null || (other is Player && session(other) != null)
    }
    fun inside(pos: Position): Boolean = pos.z == 0 && pos.x in 3328..3393 && pos.y in 3203..3325
    fun arena(pos: Position): Boolean = pos.z == 0 &&
        (pos.x in 3333..3357 || pos.x in 3364..3388) &&
        (pos.y in 3206..3220 || pos.y in 3225..3239 || pos.y in 3244..3258)

    private fun available(p: Player): Boolean = p.state == EntityState.ACTIVE && p.isAlive && !p.isLocked &&
        !p.combat.inCombat() && inside(p.position) && !arena(p.position) &&
        p.controllers.primary.javaClass == PlayerController::class.java && !p.overlays.hasWindow()

    fun challenge(player: Player, other: Player): Boolean {
        if (player === other || !available(player) || !available(other) ||
            player in sessions || other in sessions || !player.position.isWithinDistance(other.position, 16)) return false
        val incoming = requests[other]
        if (incoming?.first === player && incoming.second > System.currentTimeMillis()) {
            requests.remove(other)
            if (player.duelEscrow.isNotEmpty() || other.duelEscrow.isNotEmpty()) {
                claim(player); claim(other)
                return false
            }
            val duel = Duel(player, other)
            sessions[player] = duel; sessions[other] = duel
            requests.remove(player)
            duel.display()
        } else {
            requests[player] = other to (System.currentTimeMillis() + 60_000)
            player.sendMessage("Sending duel request...")
            other.sendMessage("${player.username}:duelreq:")
        }
        return true
    }

    fun escrow(player: Player): List<Item> = if (player.duelEscrow.isEmpty()) emptyList() else
        player.duelEscrow.split(';').map { record ->
            val parts = record.split(':')
            Item(parts[0].toInt(), parts[1].toInt())
        }

    private fun save(player: Player, items: List<Item>) {
        player.duelEscrow = items.joinToString(";") { "${it.id}:${it.amount}" }
    }

    fun claim(player: Player) {
        if (player in sessions) return
        val remaining = mutableListOf<Item>()
        for (item in escrow(player)) {
            if (player.inventory.hasSpaceFor(item) && player.inventory.add(item)) continue
            if (player.bank.hasSpaceFor(item) && player.bank.add(item)) continue
            remaining.add(item)
        }
        save(player, remaining)
        if (remaining.isNotEmpty()) player.sendMessage("Make room in your inventory or bank, then speak to Mubariz to collect your stake.")
    }

    fun login(player: Player) {
        if (arena(player.position)) player.move(LOBBY)
        claim(player)
    }

    fun leave(player: Player) {
        requests.remove(player)
        requests.entries.removeIf { it.value.first === player }
        session(player)?.let { it.finish(if (it.started) it.other(player) else null) }
    }

    fun launch() {
        DeathHookHandler.addPlayerHook(DuelController::class.java) {
            val duel = (victim.controllers.primary as DuelController).duel
            preDeath { victim.animation(Animations.DEATH, AnimationPriority.IMMUTABLE) }
            death { /* The stake is the only loss in a duel. */ }
            postDeath {
                if (!duel.ended) duel.finish(duel.other(victim).takeIf { it.isAlive })
                duel.cleanup(victim)
            }
        }
        world.schedule(1) { sessions.values.toSet().forEach { it.tick() } }
    }

    /**
     * Two-stage acceptance is invalidated by every offer or rule change. Mutations are synchronous.
     *
     * @author lare96
     */
    class Duel(val first: Player, val second: Player) {
        var rules = 0
            private set
        var confirmation = false
            private set
        var started = false
            private set
        var ended = false
            private set
        var countdown = 4
            private set
        private var age = 0
        private var transitioning = false
        private var arenaId = -1
        var revision = 0
            private set
        private val accepted = mutableSetOf<Player>()
        val players get() = listOf(first, second)
        fun other(p: Player): Player = if (p === first) second else first
        fun contains(p: Player): Boolean = p === first || p === second
        fun fighting(a: Player, b: Player): Boolean = started && !ended && countdown == 0 &&
            contains(a) && other(a) === b && a.isAlive && b.isAlive
        private fun negotiating(p: Player): Boolean = contains(p) && !started && !ended &&
            players.all { it.state == EntityState.ACTIVE && it.isAlive && !it.isLocked &&
                inside(it.position) && !arena(it.position) } && first.position.isWithinDistance(second.position, 16)

        fun toggle(player: Player, rule: DuelRules): Boolean {
            if (!negotiating(player) || confirmation) return false
            val next = rules xor (1 shl rule.bit)
            if (!DuelRules.valid(next)) return false
            if (DuelRules.FLOWERS.enabled(next) && players.any { it.equipment.get(Equipment.WEAPON)?.id !in flowers }) return false
            rules = next
            revision++
            accepted.clear()
            display()
            return true
        }

        fun stake(player: Player, index: Int, id: Int, quantity: Int, remove: Boolean = false): Boolean {
            if (!negotiating(player) || confirmation || index !in 0..27 || quantity == 0 || quantity < -1) return false
            val offered = escrow(player).toMutableList()
            val source = if (remove) offered.getOrNull(index) else player.inventory.get(index)
            if (source == null || source.id != id || !source.itemDef.isTradeable) return false
            val total = if (remove) offered.filter { it.id == id }.sumOf { it.amount.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
                else player.inventory.computeAmountForId(id)
            val count = if (quantity == -1) total else minOf(quantity, total)
            val item = Item(id, count)
            if (remove) {
                if (!player.inventory.hasSpaceFor(item) || !player.inventory.add(item)) return false
                var remaining = count
                val iterator = offered.listIterator()
                while (iterator.hasNext() && remaining > 0) {
                    val next = iterator.next()
                    if (next.id != id) continue
                    val removed = minOf(remaining, next.amount)
                    if (removed == next.amount) iterator.remove() else iterator.set(Item(id, next.amount - removed))
                    remaining -= removed
                }
            } else {
                val slot = if (item.itemDef.isStackable) offered.indexOfFirst { it.id == id } else -1
                if (slot == -1 && offered.size + (if (item.itemDef.isStackable) 1 else count) > 28) return false
                if (slot != -1 && offered[slot].amount.toLong() + count > Int.MAX_VALUE) return false
                if (!player.inventory.remove(item)) return false
                if (slot != -1) offered[slot] = Item(id, offered[slot].amount + count)
                else if (item.itemDef.isStackable) offered.add(item) else repeat(count) { offered.add(Item(id)) }
            }
            save(player, offered)
            revision++
            accepted.clear()
            display()
            return true
        }

        fun accept(player: Player): Boolean {
            if (!negotiating(player)) return false
            val overlay = player.overlays[DuelInterface::class] ?: return false
            if (overlay.duel !== this || overlay.confirm != confirmation) return false
            accepted.add(player)
            if (accepted.size != 2) { display(); return true }
            if (!confirmation) {
                confirmation = true
                accepted.clear()
                display()
                return true
            }
            if (DuelRules.FLOWERS.enabled(rules) && players.any { it.equipment.get(Equipment.WEAPON)?.id !in flowers }) {
                confirmation = false; accepted.clear(); display(); return false
            }
            val prizes = escrow(first) + escrow(second)
            val totals = prizes.groupBy { it.id }.mapValues { (_, items) -> items.sumOf { it.amount.toLong() } }
            if (players.any { participant -> !participant.inventory.hasSpaceForAll(prizes) || totals.any { (id, amount) ->
                    amount + participant.inventory.computeAmountForId(id) > Int.MAX_VALUE
                } }) {
                accepted.clear()
                players.forEach { it.sendMessage("Both players need room to receive the stakes before the duel can begin.") }
                return false
            }
            return start()
        }

        private fun start(): Boolean {
            val choices = if (DuelRules.OBSTACLES.enabled(rules)) 3..5 else 0..2
            val active = sessions.values.toSet().filter { it.started && !it.ended }
            val occupied = active.flatMap { it.players }.map { it.position }.toSet()
            var base: Position? = null
            for (id in choices) {
                if (active.count { it.arenaId == id } >= 12) continue
                val origin = arenas[id]
                for (y in origin.y..origin.y + 14) {
                    for (x in origin.x until origin.x + 24) {
                        val left = Position(x, y)
                        val right = Position(x + 1, y)
                        if (left in occupied || right in occupied || world.collisionManager.isBlocked(left, false) ||
                            world.collisionManager.isBlocked(right, false) ||
                            !world.collisionManager.traversable(left, EntityType.PLAYER, Direction.EAST)) continue
                        base = left; arenaId = id
                        break
                    }
                    if (base != null) break
                }
                if (base != null) break
            }
            if (base == null) {
                accepted.clear()
                players.forEach { it.sendMessage("All suitable arenas are full. Please try again shortly.") }
                return false
            }
            started = true
            transitioning = true
            for ((index, player) in players.withIndex()) {
                player.overlays.closeWindows()
                player.inventory.clearSecondaryWidget()
                player.controllers.register(DuelController(player, this))
                player.move(Position(base.x + index, base.y))
                player.contextMenu.hide(PlayerContextMenuOption.CHALLENGE)
                player.contextMenu.show(PlayerContextMenuOption.ATTACK)
                if (DuelRules.NO_PRAYER.enabled(rules)) player.combat.prayers.deactivateAll()
                if (DuelRules.NO_POTIONS.enabled(rules)) {
                    for (skill in player.skills) if (skill.level > skill.staticLevel) skill.level = skill.staticLevel
                }
            }
            transitioning = false
            age = 0
            return true
        }

        fun display() {
            transitioning = true
            for (player in players) {
                val current = player.overlays[DuelInterface::class]
                if (current?.duel !== this || current.confirm != confirmation) player.overlays.open(DuelInterface(this, confirmation))
                player.inventory.setSecondaryWidget(6574)
                player.inventory.updateSecondaryWidget(player)
                player.queue(WidgetItemsMessageWriter(if (confirmation) 6507 else 6669, escrow(player)))
                player.queue(WidgetItemsMessageWriter(if (confirmation) 6508 else 6670, escrow(other(player))))
                player.sendText(other(player).username, 6671)
                player.sendText(if (player in accepted) "Waiting for other player..." else if (other(player) in accepted)
                    "Other player has accepted." else "", if (confirmation) 6571 else 6684)
                player.sendVarp(Varp(286, rules))
                if (confirmation) {
                    val lines = DuelRules.entries.filter { it.enabled(rules) }.map { it.label }
                    val fields = (8242..8249).toList() + (8251..8253).toList()
                    fields.forEachIndexed { i, id -> player.sendText(lines.getOrNull(i) ?: "", id) }
                    player.sendText(if (escrow(player).isEmpty()) "Absolutely nothing!" else "", 6509)
                    player.sendText(if (escrow(other(player)).isEmpty()) "Absolutely nothing!" else "", 6502)
                    for (id in listOf(8238, 8239, 8240, 8241, 8250)) player.sendText("", id)
                }
            }
            transitioning = false
        }

        fun closed() { if (!transitioning && !started && !ended) finish(null) }

        fun tick() {
            if (ended) return
            age++
            val missing = players.firstOrNull { it.state != EntityState.ACTIVE }
            if (missing != null) { finish(if (started) other(missing) else null); return }
            if (!started) { if (age >= 500 || !negotiating(first)) finish(null); return }
            if (countdown > 0) {
                countdown--
                players.forEach { it.speak(if (countdown == 0) "FIGHT!" else countdown.toString()) }
            }
            if (age >= 6000) finish(null)
        }

        fun forfeit(player: Player): Boolean {
            if (!contains(player) || !started || ended || DuelRules.NO_FORFEIT.enabled(rules)) return false
            finish(other(player))
            return true
        }

        fun finish(winner: Player?) {
            if (ended) return
            require(winner == null || contains(winner))
            ended = true
            val prizes = if (winner != null) escrow(first) + escrow(second) else emptyList()
            if (winner != null) { save(other(winner), emptyList()); save(winner, prizes) }
            players.forEach { sessions.remove(it) }
            for (player in players) {
                player.inventory.clearSecondaryWidget()
                player.overlays.closeWindows()
                player.combat.target = null
                player.combat.autoRetaliateTarget = null
                player.actions.getAll(CombatDamageAction::class.java).forEach { it.interrupt() }
                if (player.isAlive) cleanup(player)
                claim(player)
                player.sendMessage(if (winner == null) "The duel was cancelled. Your stake has been returned." else
                    if (winner === player) "You won the duel!" else "You lost the duel.")
            }
            if (winner != null && winner.state == EntityState.ACTIVE) {
                val line = "${winner.username} (${winner.skills.combatLevel}) beat ${other(winner).username} (${other(winner).skills.combatLevel})"
                for (player in players) player.duelHistory =
                    (listOf(line) + player.duelHistory.split('|').filter { it.isNotEmpty() }).take(10).joinToString("|")
                winner.overlays.open(StandardInterface(6733))
                val display = prizes.groupBy { it.id }.map { (id, items) -> Item(id, items.sumOf { it.amount }) }
                winner.queue(WidgetItemsMessageWriter(6822, display))
                winner.sendText(other(winner).username, 6840)
                winner.sendText(other(winner).skills.combatLevel.toString(), 6839)
            }
        }

        fun cleanup(player: Player) {
            if (player.controllers.primary is DuelController) player.controllers.unregister()
            player.contextMenu.hide(PlayerContextMenuOption.ATTACK)
            if (started) player.move(LOBBY)
            if (started) player.skills.resetAll()
            if (started) {
                player.status.remove(StatusEffectType.POISONED)
                player.status.remove(StatusEffectType.IMMOBILIZED)
                player.runEnergy = 100.0
                player.combat.prayers.deactivateAll()
            }
            if (inside(player.position)) player.contextMenu.show(PlayerContextMenuOption.CHALLENGE)
        }
    }

    /**
     * Native stake and confirmation windows close their shared negotiation together.
     *
     * @author lare96
     */
    class DuelInterface(val duel: Duel, val confirm: Boolean) : InventoryOverlayInterface(if (confirm) 6412 else 6575, 6573) {
        override fun onClose(plr: Player) { duel.closed() }
    }
}
