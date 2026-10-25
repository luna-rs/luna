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
package game.content.gamesRoom

import api.predef.*
import api.predef.ext.*
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.`object`.GameObject
import io.luna.game.model.`object`.ObjectDirection
import io.luna.game.model.`object`.ObjectType
import io.luna.game.task.Task
import io.luna.net.msg.out.WidgetAnimationMessageWriter
import io.luna.net.msg.out.WidgetItemsMessageWriter
import kotlin.math.abs

/**
 * Runs unranked Draughts and RuneLink on the game thread, using mutual challenges and native boards.
 * Challenges expire after a minute. Closing a board, leaving the table or logging out resigns.
 *
 * @author lare96
 */
object GamesRoom {
    private val sessions = mutableMapOf<Player, Session>()
    private val offers = mutableMapOf<Player, Offer>()
    private var ticks = 0L
    private var running = false

    /**
     * An immutable challenge; accepting cannot silently change the agreed game or move timer.
     *
     * @author lare96
     */
    private data class Offer(val sender: Player, val recipient: Player, val runeLink: Boolean,
                             val seconds: Int, val expires: Long)

    fun inRoom(position: Position): Boolean = position.z in 0..1 &&
        position.x in 2176..2239 && position.y in 4928..4991

    private fun available(player: Player): Boolean = inRoom(player.position) &&
        player.state == EntityState.ACTIVE && player.isAlive && !player.isLocked &&
        !player.combat.inCombat() && player !in sessions

    private fun canChallenge(player: Player, opponent: Player): Boolean = player !== opponent &&
        available(player) && available(opponent) && player.position.isWithinDistance(opponent.position, 16)

    fun challenge(player: Player, opponent: Player) {
        if (!canChallenge(player, opponent) || player.overlays.hasWindow()) return
        val offer = offers[opponent]
        if (offer?.recipient === player && offer.expires > ticks) {
            val game = if (offer.runeLink) "RuneLink" else "Draughts"
            player.newDialogue().options("Accept $game (${offer.seconds}s per move)", {
                player.overlays.closeWindows()
                if (offers[opponent] === offer) accept(player, opponent)
            }, "Decline", {
                if (offers[opponent] === offer) offers.remove(opponent)
                player.overlays.closeWindows()
            }).open()
            return
        }
        if (opponent.overlays.hasWindow()) {
            player.sendMessage("That player is busy.")
            return
        }
        player.newDialogue().options("Challenge to unranked Draughts", { chooseTime(player, opponent, false) },
            "Challenge to unranked RuneLink", { chooseTime(player, opponent, true) }).open()
    }

    private fun chooseTime(player: Player, opponent: Player, runeLink: Boolean) {
        fun send(seconds: Int) {
            player.overlays.closeWindows()
            offer(player, opponent, runeLink, seconds)
        }
        player.newDialogue().options("15 seconds per move", { send(15) }, "30 seconds per move", { send(30) },
            "1 minute per move", { send(60) }, "2 minutes per move", { send(120) }).open()
    }

    internal fun offer(player: Player, opponent: Player, runeLink: Boolean, seconds: Int): Boolean {
        if (!canChallenge(player, opponent) || seconds !in listOf(15, 30, 60, 120) ||
            player.overlays.hasWindow() || opponent.overlays.hasWindow()) return false
        offers[player] = Offer(player, opponent, runeLink, seconds, ticks + 100)
        player.sendMessage("Sending challenge...")
        opponent.sendMessage("${player.username} challenges you to ${if (runeLink) "RuneLink" else "Draughts"} " +
            "($seconds seconds per move). Challenge them to accept.")
        return true
    }

    internal fun accept(player: Player, opponent: Player): Boolean {
        val offer = offers[opponent] ?: return false
        if (offer.recipient !== player || offer.expires <= ticks || !canChallenge(player, opponent) ||
            player.overlays.hasWindow() || opponent.overlays.hasWindow()) return false
        val table = world.locator.findObjects(Position(2208, 4960), 64) {
            it.id == 4651 && inRoom(it.position) && sessions.values.none { session -> session.table.position == it.position }
        }.minByOrNull { it.position.computeLongestDistance(player.position) }
        if (table == null) {
            player.sendMessage("All tables are currently full.")
            return false
        }
        clearOffers(player)
        clearOffers(opponent)
        val session = Session(listOf(opponent, player), offer.runeLink, offer.seconds, table)
        session.players.forEach { sessions[it] = session }
        session.open()
        return true
    }

    private fun clearOffers(player: Player) {
        offers.entries.removeIf { it.key === player || it.value.recipient === player }
    }

    fun leave(player: Player) {
        clearOffers(player)
        sessions[player]?.resign(player)
        sessions.values.toSet().forEach { it.stopViewing(player) }
    }

    fun view(player: Player, table: Position) {
        if (!available(player) || !player.position.isWithinDistance(table, 3)) return
        sessions.values.firstOrNull { it.table.position == table }?.view(player)
    }

    fun playing(player: Player): Boolean = player in sessions

    /**
     * Lists live challenges addressed to this player without accepting or extending them.
     */
    fun challengers(player: Player): List<Player> = offers.values.filter {
        it.recipient === player && it.expires > ticks && canChallenge(it.sender, player)
    }.map { it.sender }
    fun column(player: Player, column: Int) { sessions[player]?.column(player, column) }
    fun move(player: Player, from: Int, to: Int) { sessions[player]?.move(player, from, to) }
    fun draw(player: Player) { sessions[player]?.draw(player) }

    fun columns(player: Player): List<Int> {
        val session = sessions[player] ?: return emptyList()
        return if (session.runeLink && session.players[session.turn - 1] === player)
            session.link.legalColumns() else emptyList()
    }

    fun moves(player: Player): List<Pair<Int, Int>> {
        val session = sessions[player] ?: return emptyList()
        if (session.runeLink || session.players[session.turn - 1] !== player) return emptyList()
        return session.draughts.legalMoves().map { (from, to) ->
            if (session.players[0] === player) from to to else 63 - from to 63 - to
        }
    }

    fun launch() {
        if (running) return
        running = true
        world.schedule(object : Task(1) {
            override fun execute() { tick() }
        })
    }

    internal fun tick() {
        ticks++
        offers.entries.removeIf { it.value.expires <= ticks ||
            !canChallenge(it.value.sender, it.value.recipient) }
        sessions.values.toSet().forEach { it.tick() }
    }

    /**
     * Owns a reserved table, both boards and their interface lifecycle.
     *
     * @author lare96
     */
    private class Session(val players: List<Player>, val runeLink: Boolean,
                          val seconds: Int, val table: GameObject) {
        private val firstPlayer = rand().nextInt(2) + 1
        val link = RuneLinkBoard(firstPlayer)
        val draughts = DraughtsBoard(firstPlayer)
        private var ended = false
        private var elapsed = 0
        private var drawOffer: Player? = null
        private val spectators = mutableSetOf<Player>()
        val turn: Int get() = if (runeLink) link.turn else draughts.turn
        val interfaceId: Int get() = if (runeLink) 10617 else 10380

        fun open() {
            val straight = table.objectType == ObjectType.DEFAULT
            val offset = if (straight) when (table.direction) {
                ObjectDirection.SOUTH -> -1 to 0
                ObjectDirection.NORTH -> 1 to 0
                ObjectDirection.EAST -> 0 to -1
                else -> 0 to 1
            } else when (table.direction) {
                ObjectDirection.EAST -> -1 to -1
                ObjectDirection.SOUTH -> -1 to 1
                ObjectDirection.NORTH -> 1 to -1
                else -> 1 to 1
            }
            world.addObject(if (runeLink) 4653 else 4652, table.position, table.objectType, table.direction)
            players.forEachIndexed { index, player ->
                val sign = if (index == 0) 1 else -1
                player.walking.clear()
                player.move(table.position.translate(offset.first * sign, offset.second * sign))
                player.overlays.open(BoardInterface(this))
                player.sendMessage("Unranked game: $seconds seconds per move. Closing the board resigns.")
            }
            render(true)
        }

        fun view(player: Player) {
            if (ended) return
            player.overlays.open(ViewInterface(this))
            spectators += player
            renderView(player)
        }

        fun stopViewing(player: Player) {
            spectators.remove(player)
            if (player.overlays.getOverlay(ViewInterface::class.java)?.session === this) player.overlays.closeWindows()
        }

        fun removeViewer(player: Player) { spectators.remove(player) }

        private fun valid(player: Player): Boolean {
            if (ended || player !in players) return false
            val absent = players.firstOrNull {
                it.state != EntityState.ACTIVE || !it.isAlive || !inRoom(it.position) ||
                    !it.position.isWithinDistance(table.position, 3) ||
                    it.overlays.getOverlay(BoardInterface::class.java)?.session !== this
            }
            if (absent != null) { resign(absent); return false }
            return true
        }

        fun tick() {
            if (!valid(players[0])) return
            spectators.toList().filter {
                it.state != EntityState.ACTIVE || !it.position.isWithinDistance(table.position, 3)
            }.forEach(::stopViewing)
            if (++elapsed < seconds * 5 / 3) return
            val player = players[turn - 1]
            if (runeLink) link.legalColumns().firstOrNull()?.let { column(player, it) }
            else draughts.legalMoves().firstOrNull()?.let { applyMove(player, it.first, it.second) }
        }

        fun column(player: Player, column: Int) {
            if (!valid(player) || !runeLink || !link.move(players.indexOf(player) + 1, column)) return
            afterMove(link.finished, link.winner)
        }

        fun move(player: Player, from: Int, to: Int) {
            if (!valid(player) || runeLink) return
            if (from !in 0..63 || to !in 0..63) { render(); return }
            val reverse = players.indexOf(player) == 1
            applyMove(player, if (reverse) 63 - from else from, if (reverse) 63 - to else to)
        }

        private fun applyMove(player: Player, from: Int, to: Int) {
            if (!draughts.move(players.indexOf(player) + 1, from, to)) { render(); return }
            afterMove(draughts.finished, draughts.winner)
        }

        private fun afterMove(finished: Boolean, winner: Int) {
            elapsed = 0
            drawOffer = null
            render(true)
            if (finished) finish(if (winner == 0) "The game is a draw." else "${players[winner - 1].username} wins!")
        }

        fun draw(player: Player) {
            if (!valid(player)) return
            if (drawOffer != null && drawOffer !== player) finish("The game is a draw.")
            else {
                drawOffer = player
                players.filter { it !== player }.forEach {
                    it.sendMessage("Your opponent offers a draw. Click Offer draw to accept.")
                }
            }
        }

        fun resign(player: Player) {
            if (!ended) finish("${players.first { it !== player }.username} wins by resignation.")
        }

        private fun finish(message: String) {
            if (ended) return
            ended = true
            players.forEach { sessions.remove(it) }
            world.addObject(table.id, table.position, table.objectType, table.direction)
            players.forEach {
                if (it.overlays.getOverlay(BoardInterface::class.java)?.session === this) it.overlays.closeWindows()
                it.sendMessage(message)
            }
            spectators.toList().forEach { stopViewing(it); it.sendMessage(message) }
        }

        private fun renderView(player: Player) {
            val items = (if (runeLink) link.snapshot() else draughts.snapshot()).map(::pieceItem)
            player.queue(WidgetItemsMessageWriter(if (runeLink) 10965 else 10600, items))
            player.sendText(players[0].username, if (runeLink) 10942 else 10575)
            player.sendText(players[1].username, if (runeLink) 10951 else 10584)
            player.sendText("", if (runeLink) 10970 else 10604)
            player.sendText("${players[turn - 1].username}'s move", if (runeLink) 10981 else 10615)
            player.sendText("Unranked", if (runeLink) 10982 else 10616)
        }

        private fun pieceItem(piece: Int): Item? = when {
            piece == 0 -> null
            runeLink -> Item(3880 + piece)
            abs(piece) == 2 -> Item(if (piece > 0) 3869 else 3870)
            else -> Item(if (piece > 0) 3881 else 3882)
        }

        private fun render(restartTimer: Boolean = false) {
            val cells = if (runeLink) link.snapshot() else draughts.snapshot()
            players.forEachIndexed { index, player ->
                val view = if (!runeLink && index == 1) cells.reversedArray() else cells
                val items = view.map(::pieceItem)
                player.queue(WidgetItemsMessageWriter(if (runeLink) 10730 else 10494, items))
                player.sendText(players[0].username, if (runeLink) 10685 else 10448)
                player.sendText(players[1].username, if (runeLink) 10694 else 10457)
                player.sendText(players[turn - 1].username, if (runeLink) 10717 else 10479)
                player.sendText(if (turn == index + 1) "Your move." else "Opponent's move.", if (runeLink) 10735 else 10498)
                player.sendText("Unranked", if (runeLink) 10731 else 10492)
                player.sendText("", if (runeLink) 10743 else 10499)
                player.queue(WidgetAnimationMessageWriter(if (runeLink) 10729 else 10493, 1356))
                if (restartTimer) player.queue(WidgetAnimationMessageWriter(if (runeLink) 10707 else 10502,
                    1358 + listOf(15, 30, 60, 120).indexOf(seconds)))
            }
            spectators.forEach(::renderView)
        }
    }

    /**
     * Resigns only its own session when the player closes or replaces the board.
     *
     * @author lare96
     */
    private class BoardInterface(val session: Session) : StandardInterface(session.interfaceId) {
        override fun onClose(player: Player) { session.resign(player) }
    }

    /**
     * Displays a non-draggable board without enrolling the spectator in the match.
     *
     * @author lare96
     */
    private class ViewInterface(val session: Session) : StandardInterface(if (session.runeLink) 10874 else 10507) {
        override fun onClose(player: Player) { session.removeViewer(player) }
    }
}
