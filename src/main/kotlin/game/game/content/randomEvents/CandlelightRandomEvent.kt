/*
 * Adapted from LostCityRS/Content (377-wip), scripts/macro events/scripts/general/macro_event_candlelight.rs2.
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

import api.predef.ext.removeNpc
import api.predef.ext.addItem

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.combat.CombatHandler.displayCombatOverlay
import api.predef.*
import game.player.Sound
import io.luna.game.model.EntityState
import io.luna.game.model.LocalGraphic
import io.luna.game.model.Position
import io.luna.game.model.chunk.ChunkUpdatableView
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.game.model.mob.dialogue.Expression
import io.luna.game.model.mob.dialogue.NpcDialogue
import io.luna.game.model.mob.dialogue.PlayerDialogue
import io.luna.game.model.mob.dialogue.TextDialogue
import io.luna.game.model.mob.overlay.GameTabSet.TabIndex
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.varp.Varp
import io.luna.net.msg.out.CameraTargetMessageWriter
import io.luna.net.msg.out.ResetCameraMessageWriter
import kotlin.random.Random

/**
 * Lost City's personal Candlelight puzzle, using the native church, pillar morphs and lighter interface.
 * Eleven independently rolled candles retain their source states across logout. Burning a spent candle blows
 * out every lit candle; the 500-tick taper can be replaced by Pete. Source provides no room escape or timeout.
 * Rewards and return travel occur once, after the native completion dialogue. The supplied source explicitly marks
 * its reward distribution and noted-log/bone quantities as reconstructed; those values are preserved unchanged.
 *
 * @param player The owner of this puzzle and its saved return point.
 * @param priest The temporary source priest that brings this player to the church after the protected source delay.
 * @author lare96
 */
class CandlelightRandomEvent(override val player: Player, private val priest: Npc) : RandomEventEncounter {
    companion object {
        const val PRIEST = 3206
        const val PETE = 3207
        const val TAPER = 3208
        const val INTERFACE = 17525
        const val UP = 17527
        const val DOWN = 17528
        const val LIGHT = 17529
        const val LEFT = 17530
        const val RIGHT = 17531
        const val CLOSE = 17532
        const val VARP = 666
        const val UNLIT = 0
        const val LIT = 1
        const val BURNT = 2

        /**
         * Source region 0_30_78 and alternating pillar tiles verified against the selected 377 cache.
         */
        val ARRIVAL = Position(1972, 5003)
        val START = Position(1971, 5002)
        val PETE_ANCHOR = Position(1973, 5002)
        val PILLARS = (0 until 11).map { Position(1967 + it % 2, 4997 + it) }
        private val HIDDEN_TABS = listOf(TabIndex.COMBAT, TabIndex.SKILL, TabIndex.QUEST, TabIndex.INVENTORY,
            TabIndex.EQUIPMENT, TabIndex.PRAYER, TabIndex.MAGIC, TabIndex.SETTINGS, TabIndex.EMOTE)

        /**
         * Source permanent player variables: return coordinate, packed candle states and puzzle phase.
         */
        private var Player.candleReturn by Attr.string().persist("candlelight_return")
        private var Player.candleStates by Attr.int().persist("candlelight_states")
        private var Player.candlePhase by Attr.int().persist("candlelight_phase")

        /**
         * Source mapzone boundary; ordinary logout retains its permanent puzzle state.
         */
        fun inRoom(position: Position): Boolean = position.z == 0 && position.x in 1920..1983 && position.y in 4992..5055

        /**
         * Rejects unavailable geography rather than teleporting a player into an incomplete cache revision.
         */
        fun available(): Boolean = PILLARS.withIndex().all { (index, position) ->
            world.objects.findAll(position).anyMatch { it.id == 11368 + index }
        } && pete() != null

        /**
         * The existing shared static NPC speaks to each player's independent puzzle.
         */
        private fun pete(): Npc? = world.locator.findNearestNpc(PETE_ANCHOR, 12) {
            it.id == PETE && it.state == EntityState.ACTIVE
        }

        /**
         * Preserves the supplied members reward table, including the source's documented reconstructed entries.
         */
        internal fun reward(random: (Int) -> Int = { Random.nextInt(it) }): Item = when (random(150)) {
            in 0..49 -> Item(995, (1 shl (random(4) + 3)) * 10)
            in 50..81 -> Item(1623)
            in 82..97 -> Item(1512, 5 + random(6))
            in 98..113 -> Item(1621)
            in 114..127 -> Item(533, 1 + random(5))
            in 128..135 -> Item(1619)
            in 136..145 -> Item(995, 240)
            in 146..147 -> Item(1617)
            148 -> Item(985)
            else -> Item(987)
        }
    }

    override val resumeKey = RandomEvents.Kind.CANDLELIGHT.name
    override val blocksNewEvents get() = !finished
    override val npc get() = if (entered) pete() ?: priest else priest
    override val awaitingResponse get() = entered && !finished && inRoom(player.position)

    /**
     * Room ownership survives client disconnection through source permanent attributes, without persisting NPCs or UI.
     */
    private var entered = inRoom(player.position) && player.candleReturn.isNotEmpty()
    private var arrivalDelay: RandomEventDelay? = null
    private var finished = false
    private var taper: Npc? = null
    private var taperExpires = 0L
    private var ownedPage: DialogueInterface? = null
    private var conversation = 0

    init {
        if (entered) {
            world.removeNpc(priest)
            showState()
            hideTabs()
        } else {
            player.candleReturn = "${player.position.x},${player.position.y},${player.position.z}"
            player.candlePhase = 0
            do {
                player.candleStates = (0 until 11).fold(0) { packed, index -> packed or (Random.nextInt(3) shl (index * 2 + 1)) }
            } while ((0 until 11).none { state(it) == UNLIT })
            priest.interact(player)
            priest.navigator.follow(player)
            LocalGraphic(ctx, 86, 124, 0, priest.position, ChunkUpdatableView.globalView()).display()
            player.playSound(Sound.SMOKEPUFF2)
            priest.speak("I'm sorry to drag you away from your tasks, but I need a little help with something.")
            delayArrival()
        }
    }

    /**
     * Lost City's p_delay(2) protects player input until the third tick. The callback owns this exact encounter
     * and suspension, and cleanup never releases a later replacement lock. Resumed church puzzles skip this delay.
     */
    private fun delayArrival() {
        val suspension = RandomEventDelay.start(player, 4) ?: return
        arrivalDelay = suspension
        suspension.after(3) {
            try {
                if (arrivalDelay !== suspension || !RandomEvents.owns(this) || entered ||
                    priest.state != EntityState.ACTIVE || pete() == null) return@after
                entered = true
                player.move(ARRIVAL)
                showState()
                hideTabs()
                world.removeNpc(priest)
            } finally {
                if (arrivalDelay === suspension) arrivalDelay = null
                suspension.close()
            }
        }
    }

    override fun tick(): Boolean {
        if (finished) return false
        if (!entered) return arrivalDelay?.active() == true
        if (!inRoom(player.position)) return false
        if (taper != null && world.currentTick >= taperExpires) removeTaper()
        return true
    }

    /**
     * Reads the same two-bit candle state that the native client uses for its pillar morph.
     */
    fun state(index: Int): Int {
        require(index in PILLARS.indices)
        return player.candleStates ushr (index * 2 + 1) and 3
    }

    /**
     * Confirms this event's precise lighter panel, excluding foreign interfaces with the same widget id.
     */
    fun panelOpen(): Boolean = player.overlays.getOverlay(LighterInterface::class.java)?.owner === this

    /**
     * Exact page ownership lets the bot continue this native conversation while its normal activity stays paused.
     */
    fun ownsDialoguePage(): Boolean = ownedPage != null &&
        player.overlays.getOverlay(DialogueInterface::class.java) === ownedPage

    /**
     * The source NPC can supply a fresh taper after expiry or a burnt-candle failure.
     */
    fun taperActive(): Boolean = taper?.state == EntityState.ACTIVE

    override fun talk(visitor: Player) {
        if (visitor !== player || !RandomEvents.owns(this) || !awaitingResponse) return
        val intro = player.candlePhase == 0
        val failed = player.candlePhase == 2
        if (!intro && !failed && PILLARS.indices.none { state(it) == UNLIT }) {
            speak(listOf(NpcDialogue(PETE, Expression.HAPPY, "Thank you for your help! I'll send you back now."))) {
                claimReward()
            }
        } else if (intro) {
            speak(listOf(PlayerDialogue(Expression.QUIZZICAL, "What's wrong?"),
                NpcDialogue(PETE, Expression.NEUTRAL, "Some of the candles have been blown out", "and I can't reach them in order to", "relight them all."),
                NpcDialogue(PETE, Expression.QUIZZICAL, "Could you light the candles that are not", "burnt down for me? I will handle the rest later."),
                NpcDialogue(PETE, Expression.NEUTRAL, "I can give you a reward from the monastery's", "treasury for your troubles."),
                PlayerDialogue(Expression.NEUTRAL, "I'll do my best."), TextDialogue("Pete hands you a lighter."))) { startLighter() }
        } else if (failed) {
            speak(listOf(PlayerDialogue(Expression.QUIZZICAL, "My lighter has been extinguished."),
                NpcDialogue(PETE, Expression.NEUTRAL, "No problem, I can relight it for you. Try again!"),
                TextDialogue("Pete hands you a new lighter."))) { startLighter() }
        } else {
            speak(listOf(NpcDialogue(PETE, Expression.ANGRY, "You aren't finished yet. There are still candles to light!"))) {
                startLighter()
            }
        }
    }

    /**
     * Shows source dialogue pages with exact ownership. Closing a page without Continue abandons its callback.
     */
    private fun speak(pages: List<DialogueInterface>, completed: () -> Unit) {
        val token = ++conversation
        fun show(index: Int) {
            if (token != conversation || !RandomEvents.owns(this) || !awaitingResponse) return
            val page = pages[index]
            page.setContinueAction {
                if (token == conversation && ownedPage === page && RandomEvents.owns(this) && awaitingResponse) {
                    ownedPage = null
                    if (index == pages.lastIndex) completed() else show(index + 1)
                }
            }
            player.resetDialogues()
            ownedPage = page
            player.overlays.open(page)
        }
        show(0)
    }

    /**
     * Restarts the source control panel and its independent 500-tick temporary taper.
     */
    private fun startLighter() {
        if (!RandomEvents.owns(this) || !awaitingResponse) return
        removeTaper()
        player.candlePhase = 1
        player.move(START)
        val next = Npc(ctx, TAPER, START.translate(-2, 0)).also {
            it.respawnTicks = -1
            it.isWandering = false
            it.walking.isLocked = true
        }
        if (!world.npcs.add(next)) {
            player.candlePhase = 2
            return
        }
        taper = next
        taperExpires = world.currentTick + 500
        player.overlays.open(LighterInterface(this))
        camera(254)
    }

    /**
     * Handles only the owner's active native panel buttons. The source's two-by-eleven control lane is retained.
     */
    fun button(id: Int) {
        if (!RandomEvents.owns(this) || !awaitingResponse || !panelOpen()) return
        if (id == CLOSE) {
            player.overlays.closeWindows(false)
            return
        }
        if (id == LIGHT) {
            if (!taperActive()) {
                player.candlePhase = 2
                player.sendMessage("Your lighter seems to have been extinguished. Talk to Pete for a new one.")
                return
            }
            val index = PILLARS.indexOf(player.position.translate(-3, 0))
            if (index == -1) { player.sendMessage("There is nothing to light here."); return }
            when (state(index)) {
                UNLIT -> {
                    setState(index, LIT)
                    player.sendMessage("You light the candle.")
                }
                LIT -> player.sendMessage("This candle is already lit.")
                else -> {
                    for (candle in PILLARS.indices) if (state(candle) == LIT) setState(candle, UNLIT)
                    player.candlePhase = 2
                    removeTaper()
                    player.sendMessage("A strong gust of wind seems to blow out the candles.")
                }
            }
            return
        }
        val step = when (id) {
            UP -> -1 to 0
            DOWN -> 1 to 0
            LEFT -> 0 to -1
            RIGHT -> 0 to 1
            else -> return
        }
        val target = player.position.translate(step.first, step.second)
        if (target.x !in 1970..1971 || target.y !in 4997..5007) {
            player.sendMessage("You can't move any further that way.")
            return
        }
        player.moveKeepingWindows(target)
        taper?.takeIf { it.state == EntityState.ACTIVE }?.move(target.translate(-2, 0))
        camera(10)
    }

    /**
     * Writes the native packed varp so each player sees their own pillar states in the shared church.
     */
    private fun setState(index: Int, value: Int) {
        val shift = index * 2 + 1
        player.candleStates = player.candleStates and (3 shl shift).inv() or (value shl shift)
        showState()
    }

    /**
     * Sends the source's current packed candle varp without changing any world objects shared by other players.
     */
    private fun showState() { player.sendVarp(Varp(VARP, player.candleStates)) }

    /**
     * Hides only the source-listed tabs, retaining logout, friends and ignore controls.
     */
    private fun hideTabs() { HIDDEN_TABS.forEach { player.tabs.clear(it) } }

    /**
     * Source camera positions are relative to the player's current control tile.
     */
    private fun camera(speed: Int) {
        player.queue(CameraTargetMessageWriter(player.position.translate(-1, 0), 335, speed, speed, false))
        player.queue(CameraTargetMessageWriter(player.position.translate(-2, 0), 315, speed, speed, true))
    }

    /**
     * Removes only this player's temporary taper, never the shared Pious Pete NPC.
     */
    private fun removeTaper() {
        taper?.let { if (it.state != EntityState.INACTIVE) world.removeNpc(it) }
        taper = null
    }

    /**
     * Latches completion before returning the player and granting one native reward.
     */
    private fun claimReward() {
        if (!RandomEvents.owns(this) || !awaitingResponse || player.candlePhase != 1 ||
            PILLARS.indices.any { state(it) == UNLIT }) return
        val values = player.candleReturn.split(',').mapNotNull { it.toIntOrNull() }
        if (values.size != 3) return
        val destination = Position(values[0], values[1], values[2])
        finished = true
        RandomEvents.answered(this)
        player.move(destination)
        val gift = reward()
        if (!player.inventory.add(gift)) world.addItem(gift, player.position, player)
        clearState()
    }

    override fun close() {
        conversation++
        arrivalDelay?.close()
        arrivalDelay = null
        if (panelOpen() || ownsDialoguePage()) player.overlays.closeWindows(false)
        removeTaper()
        if (priest.state != EntityState.INACTIVE) world.removeNpc(priest)
        player.queue(ResetCameraMessageWriter())
        if (!inRoom(player.position) || finished || !player.isAlive) clearState()
    }

    /**
     * Clears the source variables and restores normal tabs and camera after return or mapzone exit.
     */
    private fun clearState() {
        player.candleReturn = ""
        player.candleStates = 0
        player.candlePhase = 0
        player.tabs.resetAll()
        player.combat.displayCombatOverlay()
        player.sendVarp(Varp(VARP, 0))
        player.queue(ResetCameraMessageWriter())
    }

    /**
     * Closing the real panel releases only its taper and camera, retaining unfinished candle progress.
     *
     * @property owner The player-owned event whose native button actions this interface authorizes.
     * @author lare96
     */
    class LighterInterface(val owner: CandlelightRandomEvent) : StandardInterface(INTERFACE) {
        override fun onClose(player: Player) {
            owner.removeTaper()
            player.queue(ResetCameraMessageWriter())
        }
    }
}
