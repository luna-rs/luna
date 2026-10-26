package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.Suspendable.waitFor
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import engine.controllers.Controllers.inWilderness
import game.content.gamesRoom.GamesRoom
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerContextMenuOption
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.dialogue.OptionDialogue
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Visits Burthorpe for one board game, using native challenges, dialogue choices and widget actions.
 * A visit has an absolute deadline; interruption resigns and a restored attempt cannot start another game.
 *
 * @author lare96
 */
class GamesRoomBotScript(bot: Bot, private var attempted: Boolean = false,
                         private var started: Boolean = false,
                         private val runeLink: Boolean = rand().nextBoolean(),
                         private val waitingUntil: Long = System.currentTimeMillis() + 300_000,
                         private val deadline: Long = System.currentTimeMillis() + 1_200_000) : BotScript(bot) {
    companion object {
        val LOBBY = Position(2208, 4938)

        fun eligible(bot: Bot): Boolean = ActionSafety.NON_COMBAT.allows(bot) &&
            bot.state == EntityState.ACTIVE && !bot.inWilderness() &&
            bot.controllers.primary.javaClass == PlayerController::class.java &&
            !bot.overlays.hasWindow() && !GamesRoom.playing(bot)

        /**
         * Only idle visitors running this activity receive unsolicited bot-to-bot challenges.
         * The lower index sends first so two visitors cannot open competing option dialogues.
         */
        internal fun opponent(bot: Bot, candidates: Collection<Player>): Player? = candidates.firstOrNull {
            it !== bot && it.state == EntityState.ACTIVE && it.isAlive && !it.isLocked &&
                GamesRoom.inRoom(it.position) && bot.position.isWithinDistance(it.position, 16) &&
                !it.overlays.hasWindow() && !it.combat.inCombat() && !GamesRoom.playing(it) &&
                (it !is Bot || it.index > bot.index &&
                    (it.scriptStack.current() as? GamesRoomBotScript)?.attempted == false)
        }

        internal fun chooseColumn(columns: List<Int>): Int? = columns.minByOrNull { kotlin.math.abs(3 - it) }

        internal fun chooseMove(moves: List<Pair<Int, Int>>): Pair<Int, Int>? =
            moves.filter { it.second / 8 == 0 }.ifEmpty { moves }.randomOrNull()
    }

    /**
     * Persists both deadlines and whether this single-game attempt was already used.
     *
     * @author lare96
     */
    class BoardGameData : BotScriptData() {
        var attempted = false
        var started = false
        var runeLink = false
        var waitingUntil = 0L
        var deadline = 0L
        override fun load(data: JsonObject) {
            attempted = data.get("attempted").asBoolean
            started = data.get("started").asBoolean
            runeLink = data.get("runeLink").asBoolean
            waitingUntil = data.get("waitingUntil").asLong
            deadline = data.get("deadline").asLong
        }
        override fun save(data: JsonObject) {
            data.addProperty("attempted", attempted)
            data.addProperty("started", started)
            data.addProperty("runeLink", runeLink)
            data.addProperty("waitingUntil", waitingUntil)
            data.addProperty("deadline", deadline)
        }
    }

    constructor(bot: Bot, data: BoardGameData) : this(bot, data.attempted, data.started, data.runeLink,
        data.waitingUntil, data.deadline)

    private var ownedDialogue: OptionDialogue? = null

    override suspend fun init(resumed: Boolean): Boolean {
        if (System.currentTimeMillis() >= deadline || !isActionSafe()) return true
        if (attempted || started) return !GamesRoom.playing(bot)
        if (System.currentTimeMillis() >= waitingUntil || !eligible(bot)) return true
        return withTimeoutOrNull(180_000) {
            (!GamesRoom.inRoom(bot.position) || bot.position.z != LOBBY.z) && !handler.webWalkTo(LOBBY, radius = 3)
        } ?: true
    }

    override suspend fun run(): Boolean {
        if (!isActionSafe() || System.currentTimeMillis() >= deadline || !GamesRoom.inRoom(bot.position)) return true
        if (GamesRoom.playing(bot)) {
            attempted = true
            started = true
            val column = chooseColumn(GamesRoom.columns(bot))
            val move = chooseMove(GamesRoom.moves(bot))
            if (column != null) {
                if (!handler.widgets.clickButton(10617, 10736 + column)) return true
            } else if (move != null) {
                if (!handler.widgets.dragItem(10380, 10494, move.first, move.second)) return true
            }
            delay(2.seconds)
            return false
        }
        if (started || System.currentTimeMillis() >= waitingUntil) return true
        if (bot.overlays.hasWindow()) return true
        val incoming = GamesRoom.challengers(bot).firstOrNull()
        val target = incoming ?: if (!attempted) opponent(bot,
            world.locator.findPlayers(bot.position, 16) { true }) else null
        if (target != null) {
            attempted = true
            val success = withTimeoutOrNull(20_000) {
                if (!handler.interactions.interact(PlayerContextMenuOption.CHALLENGE.index, target) {
                    isActionSafe() && System.currentTimeMillis() < waitingUntil &&
                        GamesRoom.inRoom(bot.position) && GamesRoom.inRoom(target.position) &&
                        !GamesRoom.playing(target)
                }) return@withTimeoutOrNull false
                if (!waitFor(5.seconds) { bot.overlays[OptionDialogue::class] != null }) return@withTimeoutOrNull false
                val dialogue = bot.overlays[OptionDialogue::class] ?: return@withTimeoutOrNull false
                ownedDialogue = dialogue
                val options = dialogue.options
                if (options.firstOrNull()?.startsWith("Accept ") == true) {
                    if (!handler.widgets.clickDialogueOption(1)) return@withTimeoutOrNull false
                    if (!waitFor(5.seconds) { GamesRoom.playing(bot) }) return@withTimeoutOrNull false
                    started = true
                } else {
                    val choice = "Challenge to unranked ${if (runeLink) "RuneLink" else "Draughts"}"
                    if (!handler.widgets.clickDialogueOption(choice) || !waitFor(5.seconds) {
                        bot.overlays[OptionDialogue::class]?.options?.contains("30 seconds per move") == true
                    }) return@withTimeoutOrNull false
                    ownedDialogue = bot.overlays[OptionDialogue::class]
                    if (!handler.widgets.clickDialogueOption("30 seconds per move")) return@withTimeoutOrNull false
                    if (!waitFor(5.seconds) { !bot.overlays.hasWindow() }) return@withTimeoutOrNull false
                }
                true
            } == true
            if (!success) return true
        }
        delay(2.seconds)
        return false
    }

    override fun paused() {
        GamesRoom.leave(bot)
        if (ownedDialogue != null && bot.overlays[OptionDialogue::class] === ownedDialogue) bot.overlays.closeWindows()
        ownedDialogue = null
        bot.navigator.cancel()
        bot.walking.clear()
    }

    override suspend fun finish() { paused() }
    override fun allowsLoot(item: GroundItem): Boolean = false
    override fun snapshot() = BoardGameData().also {
        it.attempted = attempted
        it.started = started
        it.runeLink = runeLink
        it.waitingUntil = waitingUntil
        it.deadline = deadline
    }
}
