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
package game.content.brimhavenArena

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.predef.*
import api.predef.ext.*
import game.skill.agility.AgilityAction
import game.skill.agility.AgilityStep
import io.luna.game.action.ActionState
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.overlay.WalkableInterface
import io.luna.game.model.mob.varp.Varp
import io.luna.game.model.`object`.GameObject
import io.luna.net.msg.out.PositionHintMessageWriter

/**
 * Owns paid visits and the shared hundred-tick pillar rotation on the game thread.
 * Source rules and identifiers: LostCityRS/Content 44eab09081ef0f6c5f3eaabd1897a588ad7d9e50.
 *
 * @author lare96
 */
object BrimhavenArena {
    const val TICKET = 2996
    const val VARP = 309
    val ENTRANCE = Position(2808, 3193)
    val LANDING = tile(53, 53)
    val pillars = (0..23).map { tile(9 + it % 5 * 11, 10 + it / 5 * 11) }
    internal var Player.paid by Attr.boolean().persist("brimhaven_arena_paid")
    private val sessions = mutableMapOf<Player, Session>()
    private val actions = mutableMapOf<Player, AgilityAction>()
    var targetIndex = 0
        private set
    internal var ticks = 0L
        private set
    val target: Position get() = pillars[targetIndex]

    fun tile(x: Int, y: Int, z: Int = 3) = Position(2752 + x, 9536 + y, z)
    fun inside(p: Position): Boolean = p.x in 2752..2815 && p.y in 9536..9599 && p.z in 0..3
    fun session(player: Player): Session? = sessions[player]
    /**
     * Click listeners run inside the interaction's own strong action, so only a lock, death or combat means busy.
     */
    internal fun ready(player: Player): Boolean = player.state == EntityState.ACTIVE && player.isAlive &&
        !player.isLocked && !player.combat.inCombat()
    private fun near(player: Player, npc: Npc, id: Int): Boolean = ready(player) && npc.id == id &&
        npc.state == EntityState.ACTIVE && npc.position.isWithinDistance(ENTRANCE, 16) &&
        player.position.isWithinDistance(npc.position, 3)

    fun pay(player: Player, clerk: Npc): Boolean {
        if (!near(player, clerk, 437) || inside(player.position)) return false
        if (player.paid) { player.sendMessage("You've already paid. Head down the ladder!"); return true }
        if (player.inventory.computeAmountForId(995) < 200 || !player.inventory.remove(Item(995, 200))) {
            player.sendMessage("You need 200 coins to enter the Agility Arena.")
            return false
        }
        player.paid = true
        refresh(player)
        player.sendMessage("You give Cap'n Izzy the 200 coin entrance fee.")
        return true
    }

    fun talk(player: Player, clerk: Npc) {
        if (!near(player, clerk, 437)) return
        player.newDialogue()
            .player(Expression.HAPPY, "Ahoy Cap'n!")
            .npc(437, Expression.HAPPY, "Ahoy there!")
            .then { ask(it, clerk) }.open()
    }

    private fun ask(player: Player, clerk: Npc) {
        player.newDialogue().options("What is this place?", {
            it.newDialogue()
                .player(Expression.CONFUSED, "What is this place?")
                .npc(437, Expression.HAPPY, "This, me hearty, is the entrance to the Brimhaven", "Agility Arena!")
                .npc(437, Expression.HAPPY, "I were diggin for buried treasure when I found it!",
                    "Amazed I was! It was a sight to behold!")
                .npc(437, Expression.HAPPY, "It were the biggest thing I'd ever seen! It",
                    "must've been at least a league from side to side!")
                .npc(437, Expression.HAPPY, "It made me list, I were that excited!")
                .npc(437, Expression.HAPPY, "I'd found a huge cave with all these platforms. I",
                    "reckon it be an ancient civilisation that made",
                    "it. I had to be mighty careful as there was these", "traps everywhere! Dangerous it was!")
                .npc(437, Expression.HAPPY, "Entrance is only 200 coins!")
                .then { player -> ask(player, clerk) }.open()
        }, "What do I do in the arena?", {
            it.newDialogue()
                .player(Expression.CONFUSED, "What do I do in the arena?")
                .npc(437, "Well, me hearty, it's simple. Ye can cross between",
                    "two platforms by using the traps or obstacles", "strung across 'em. Try and make your way to the",
                    "pillar that is indicated by the flashing arrow.")
                .npc(437, "Ye receive tickets for tagging more than one",
                    "pillar in a row. So ye won't get a ticket from the",
                    "first pillar but ye will for every platform ye tag", "in a row after that.")
                .npc(437, "If ye miss a platform ye will miss out on the next",
                    "ticket so try and get every platform you can! When",
                    "ye be done, take the tickets to Jackie over there", "and she'll exchange them for more stuff!")
                .player(Expression.HAPPY, "Thanks!")
                .then { player -> ask(player, clerk) }.open()
        }, "I'd like to use the Agility Arena, please.", { offerEntry(it, clerk) },
            "See you later.", { it.newDialogue().player("See you later.").open() }).open()
    }

    private fun offerEntry(player: Player, clerk: Npc) {
        val dialogue = player.newDialogue().player("I'd like to use the Agility Arena, please.")
        if (player.paid) {
            dialogue.npc(437, Expression.LAUGHING, "Ye've already paid, so down ye goes...").open()
            return
        }
        dialogue.npc(437, "Aye, entrance be 200 coins.")
            .npc(437, Expression.SHOCKED, "A word of warning me hearty! There are dangerous", "traps down there!")
        if (player.inventory.computeAmountForId(995) < 200) {
            dialogue.player("I don't have the money on me at the moment.")
                .npc(437, Expression.ANGRY, "No coins, no entrance!").open()
            return
        }
        dialogue.options("Okay, here's 200 coins.", {
            it.newDialogue().player("Okay, here's 200 coins.").then { player ->
                if (pay(player, clerk))
                    player.newDialogue().npc(437, Expression.HAPPY, "May the wind be in ye sails!").open()
            }.open()
        }, "Never mind.", { it.newDialogue().player("Never mind.").open() }).open()
    }

    /**
     * The arena ladders are climbed by the shared ladder plugin; the way down only takes players who have paid.
     */
    fun checkAccess(player: Player): Boolean {
        if (player.paid) return true
        player.sendMessage("Pay Cap'n Izzy before entering the arena.")
        return false
    }

    fun ladder(player: Player, ladder: GameObject): Boolean {
        if (!ready(player) || ladder.state != EntityState.ACTIVE ||
            !player.position.isWithinDistance(ladder.position, 2)) return false
        return when (ladder.id) {
            3610 -> if (inside(player.position) && player.z == 0 && ladder.position.z == 0) {
                val landing = ladder.position.translate(0, 0, 3)
                traverse(player, listOf(AgilityStep(landing, 2, animation = 828, jump = true)),
                    "You climb back onto the platforms.")
            } else false
            else -> false
        }
    }eeeeeeeeeeeeeeeee

    fun enter(player: Player): Boolean {
        if (!inside(player.position) || !player.isAlive || !player.paid) return false
        if (session(player) != null) return true
        sessions[player] = Session()
        player.overlays.open(ArenaOverlay())
        refresh(player)
        player.queue(PositionHintMessageWriter(target, 60))
        return true
    }

    fun leave(player: Player, clearPayment: Boolean = true) {
        ArenaObstacles.cancelApproach(player)
        actions.remove(player)?.interrupt()
        if (sessions.remove(player) == null) return
        if (clearPayment) player.paid = false
        refresh(player)
        player.queue(PositionHintMessageWriter(null))
        if (player.overlays.getOverlay(ArenaOverlay::class.java) != null) player.overlays.closeWalkable()
    }

    fun login(player: Player) {
        if (inside(player.position)) {
            if (!enter(player)) player.move(ENTRANCE)
        } else refresh(player)
    }

    fun tag(player: Player, pillar: GameObject): Boolean {
        val session = session(player) ?: return false
        if (!ready(player) || player.z != 3 || pillar.state != EntityState.ACTIVE ||
            pillar.id !in setOf(3608, 3581) || !player.position.isWithinDistance(pillar.position, 1) ||
            !world.collisionManager.raycast(player.position, pillar.position)) return false
        if (pillar.position != target) {
            player.sendMessage("You can only get a ticket when the flashing arrow is above the pillar.")
            return false
        }
        if (session.tagged) {
            if (session.first) firstTagLines(player)
            else player.sendMessage("You can only get one ticket at a time, wait till the arrow moves again.")
            return false
        }
        if (session.eligible && !player.inventory.add(Item(TICKET))) {
            player.sendMessage("Make room for your Agility Arena Ticket and tag the pillar again."); return false
        }
        session.tagged = true
        session.first = !session.eligible
        session.eligible = true
        refresh(player)
        if (session.first) {
            firstTagLines(player)
            player.newDialogue().text("You get tickets by tagging more than one pillar in a row. @red@Tag the",
                "@red@next pillar for a ticket!").open()
        } else {
            player.sendMessage("You have received an Agility Arena Ticket!")
            player.newDialogue().give(Item(TICKET), "You have received an Agility Arena Ticket!", false).open()
        }
        return true
    }

    private fun firstTagLines(player: Player) {
        player.sendMessage("You get tickets by tagging more than one pillar in a row, tag the next")
        player.sendMessage("pillar!")
    }

    internal fun rotate(index: Int) {
        require(index in pillars.indices)
        targetIndex = index
        for ((player, session) in sessions.toMap()) {
            if (!inside(player.position) || !player.isAlive || player.state != EntityState.ACTIVE) { leave(player); continue }
            if (!session.tagged) { session.eligible = false; session.first = false }
            session.tagged = false
            refresh(player)
            player.queue(PositionHintMessageWriter(target, 60))
        }
    }

    internal fun tick() {
        ticks++
        if (ticks % 100 == 0L) {
            // The arrow always moves to a new pillar.
            rotate((targetIndex + 1 + rand().nextInt(pillars.size - 1)) % pillars.size)
            ArenaTraps.rebuildPlanks()
        }
        if (ticks % 10 == 0L) ArenaTraps.blades()
    }

    internal fun refresh(player: Player) {
        val session = session(player)
        val bits = (if (player.paid) 2 else 0) or (if (session?.tagged == true) 1 else 0) or
            (if (session?.eligible == true) 4 else 0) or (if (session?.first == true) 8 else 0)
        player.sendVarp(Varp(VARP, bits))
    }

    /**
     * Runs native locked agility movement. Only the same live action may grant its completion effects.
     * Area departure or logout cancels it without granting experience or later moving the player back.
     */
    internal fun traverse(player: Player, steps: List<AgilityStep>, message: String?,
                          complete: () -> Unit = {}): Boolean {
        if (!ready(player)) return false
        val action = AgilityAction(player, steps, message)
        actions[player] = action
        action.onSuccess = {
            if (actions[player] === action) {
                actions.remove(player)
                if (player.isAlive && player.state == EntityState.ACTIVE && player.position == steps.last().destination) complete()
            }
        }
        player.submitAction(action)
        return true
    }

    internal fun process(player: Player) {
        if (!player.isAlive || player.state != EntityState.ACTIVE) { leave(player); return }
        if (session(player) == null && !enter(player)) { player.move(ENTRANCE); return }
        if (ready(player) && player.z == 3) ArenaTraps.trigger(player)
        if (actions[player]?.state != ActionState.PROCESSING) actions.remove(player)
    }

    fun openRewards(player: Player, trader: Npc) {
        if (near(player, trader, 1055)) player.overlays.open(Rewards(trader))
    }

    /**
     * Ticket prices and both native button targets for each reward.
     *
     * @author lare96
     */
    enum class Reward(val cost: Int, val xp: Double = 0.0, val item: Int = -1, vararg val buttons: Int) {
        XP_ONE(1, 240.0, buttons = intArrayOf(8387, 8388)),
        XP_TEN(10, 2480.0, buttons = intArrayOf(8389, 8395)),
        XP_TWENTY_FIVE(25, 6500.0, buttons = intArrayOf(8390, 8396)),
        XP_HUNDRED(100, 28000.0, buttons = intArrayOf(8391, 8397)),
        XP_THOUSAND(1000, 320000.0, buttons = intArrayOf(8392, 8398)),
        TOADFLAX(3, item = 2998, buttons = intArrayOf(8382, 8384)),
        SNAPDRAGON(10, item = 3000, buttons = intArrayOf(8393, 8394)),
        HOOK(800, item = 2997, buttons = intArrayOf(8381, 8385))
    }

    fun exchange(player: Player, reward: Reward): Boolean {
        val window = player.overlays.getOverlay(Rewards::class.java) ?: return false
        if (!near(player, window.trader, 1055)) return false
        val count = player.inventory.computeAmountForId(TICKET)
        if (count < reward.cost) { player.sendMessage("You need ${reward.cost} tickets for that reward."); return false }
        if (reward.item != -1 && player.inventory.isFull && count != reward.cost) {
            player.sendMessage("Make room for your reward first."); return false
        }
        if (!player.inventory.remove(Item(TICKET, reward.cost))) return false
        if (reward.item != -1) check(player.inventory.add(Item(reward.item)))
        else player.agility.addExperience(reward.xp)
        return true
    }

    /**
     * Runtime streak state, deliberately reset on every visit or login.
     *
     * @author lare96
     */
    class Session {
        var eligible = false
            internal set
        var tagged = false
            internal set
        var first = false
            internal set
    }

    /**
     * Native ticket eligibility overlay.
     *
     * @author lare96
     */
    class ArenaOverlay : WalkableInterface(8287)

    /**
     * Remembers the trader that opened this reward screen to reject remote widget clicks.
     *
     * @author lare96
     */
    class Rewards(val trader: Npc) : StandardInterface(8292)
}
