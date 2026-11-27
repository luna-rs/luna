package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotNpcDialogues
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.webwalk.plan.BankAccess
import api.bot.webwalk.walk.WebWalker
import com.google.gson.JsonObject
import game.content.ernestTheChicken.DraynorManor as Manor
import game.content.ernestTheChicken.ErnestTheChicken as Quest
import game.content.ernestTheChicken.ManorLeverPuzzle as Puzzle
import game.content.ernestTheChicken.ManorLeverPuzzle.Door
import game.content.ernestTheChicken.ManorLeverPuzzle.Lever
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.interact.InteractionPolicy
import io.luna.game.model.mob.movement.NavigationResult
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Plays Ernest the Chicken through the same dialogue, pickup and object interactions as a player.
 * The saved stage, carried items and current lever settings determine where an interrupted run resumes.
 *
 * @author lare96
 */
class ErnestTheChickenBotScript(bot: Bot, private val data: ErnestData = ErnestData()) : BotScript(bot) {

    companion object {
        private const val ATTEMPT_MS = 13 * 60_000L
        private const val RETRY_MS = 60 * 60_000L
        private var Bot.ernestRetryAt by Attr.long().persist("ernest_bot_retry_at")
        private val veronica = Position(3110, 3330)
        private val professor = Position(3110, 3367, 2)
        private val bookcaseEast = Position(3098, 3358)
        private val closetInside = Manor.closet.translate(1, 0)
        private val connectedRooms = mapOf(1 to 4, 4 to 1, 7 to 8, 8 to 7)
        private val roomTiles = mapOf(
            1 to Position(3107, 9765), 2 to Position(3102, 9765), 3 to Position(3097, 9765),
            4 to Position(3108, 9760), 5 to Position(3102, 9760), 6 to Position(3097, 9760),
            7 to Position(3116, 9754), 8 to Position(3102, 9755), 9 to Puzzle.oilCan
        )

        fun eligible(bot: Bot): Boolean = Quest.stage(bot) < Quest.COMPLETE && bot.isAlive &&
            !bot.combat.inCombat() && !bot.overlays.hasWindow() &&
            bot.controllers.primary.javaClass == PlayerController::class.java &&
            System.currentTimeMillis() >= bot.ernestRetryAt
    }

    /**
     * Preserves the attempt deadline when the script is saved and later restored.
     *
     * @author lare96
     */
    class ErnestData : BotScriptData() {
        var deadline = System.currentTimeMillis() + ATTEMPT_MS

        override fun load(data: JsonObject) {
            deadline = data.get("deadline").asLong
        }

        override fun save(data: JsonObject) {
            data.addProperty("deadline", deadline)
        }
    }

    override val actionSafety = ActionSafety.COMBAT
    override val allowsCombatResponse = false
    var failure: String? = null
        private set

    override fun snapshot(): ErnestData = data

    override suspend fun run(): Boolean {
        if (stage() == Quest.COMPLETE) return true
        var step = "leave the basement or closet"
        val finished = withTimeoutOrNull(data.deadline - System.currentTimeMillis()) {
            while (live() && stage() != Quest.COMPLETE) {
                step = nextStep()
                bot.log("Ernest the Chicken: $step.")
                if (!advance()) {
                    delay(1_200)
                    if (!live() || !advance()) return@withTimeoutOrNull false
                }
            }
            stage() == Quest.COMPLETE
        } == true

        if (!finished) {
            failure = "Could not $step at ${bot.position}, quest stage ${stage()}."
            bot.ernestRetryAt = System.currentTimeMillis() + RETRY_MS
            bot.log("Ernest the Chicken: $failure")
        }
        return true
    }

    private fun nextStep(): String = when {
        inBasement() && has(Quest.OIL_CAN) -> "leave the lever maze"
        inBasement() -> "solve the lever maze and collect the oil can"
        inStudy() -> "open the secret passage"
        inCloset() -> if (has(Quest.RUBBER_TUBE)) "leave the closet" else "pick up the rubber tube"
        stage() == 0 -> "speak to Veronica"
        stage() == Quest.STARTED -> "ask Professor Oddenstein about Ernest"
        !has(Quest.PRESSURE_GAUGE) -> "recover the pressure gauge"
        !has(Quest.RUBBER_TUBE) -> "recover the rubber tube"
        !has(Quest.OIL_CAN) -> "enter the basement"
        else -> "give the professor his machine parts"
    }

    private suspend fun advance(): Boolean = when {
        inBasement() -> basement()
        inStudy() && (stage() != Quest.FIND_PARTS || has(Quest.OIL_CAN)) ->
            click(Manor.SECRET_LEVER, Manor.secretLever, local = true, policy = Puzzle.leverReach) { !inStudy() }
        inStudy() -> click(Manor.LADDER_DOWN, Manor.ladderDown, local = true, done = ::inBasement)
        inCloset() && stage() == Quest.FIND_PARTS && !has(Quest.RUBBER_TUBE) ->
            collect(Quest.RUBBER_TUBE, Manor.rubberTube, local = true)
        inCloset() -> use(Manor.KEY, Manor.CLOSET, Manor.closet) { !inCloset() }
        stage() == 0 -> talk(Quest.VERONICA, veronica, listOf(Quest.ACCEPT), setOf("I'll see what I can do.")) {
            stage() >= Quest.STARTED
        }
        stage() == Quest.STARTED -> talk(Quest.PROFESSOR, professor, listOf(Quest.ASK_ABOUT_ERNEST, Quest.CHANGE_HIM_BACK)) {
            stage() >= Quest.FIND_PARTS
        }
        !has(Quest.PRESSURE_GAUGE) -> gauge()
        !has(Quest.RUBBER_TUBE) -> tube()
        !has(Quest.OIL_CAN) -> walk(bookcaseEast) &&
            click(Manor.BOOKCASE_SOUTH, Manor.bookcaseSouth, local = true, done = ::inStudy)
        else -> talk(Quest.PROFESSOR, professor) { stage() == Quest.COMPLETE }
    }

    private suspend fun gauge(): Boolean {
        if (!fountainPoisoned()) {
            if (!has(Manor.POISONED_FISH_FOOD)) {
                if (!has(Manor.FISH_FOOD) && !collect(Manor.FISH_FOOD, Manor.fishFood)) return false
                if (!has(Manor.POISON) && !collect(Manor.POISON, Manor.poison)) return false
                if (!handler.inventory.useItem(Manor.POISON).onItem(Manor.FISH_FOOD) ||
                    !await { has(Manor.POISONED_FISH_FOOD) }) return false
            }
            if (!use(Manor.POISONED_FISH_FOOD, Manor.FOUNTAIN, Manor.fountain, ::fountainPoisoned)) return false
        }
        return click(Manor.FOUNTAIN, Manor.fountain) { has(Quest.PRESSURE_GAUGE) }
    }

    private suspend fun tube(): Boolean {
        if (!has(Manor.KEY)) {
            if (!has(Manor.SPADE) && !collect(Manor.SPADE, Manor.spade)) return false
            if (!use(Manor.SPADE, Manor.COMPOST, Manor.compost) { has(Manor.KEY) }) return false
        }
        if (!use(Manor.KEY, Manor.CLOSET, Manor.closet, ::inCloset)) return false
        return collect(Quest.RUBBER_TUBE, Manor.rubberTube, local = true)
    }

    private suspend fun basement(): Boolean {
        var room = roomTiles.entries.sortedBy { it.value.computeLongestDistance(bot.position) }
            .firstOrNull { localWalk(it.value) }?.key ?: return false
        val target = if (has(Quest.OIL_CAN)) 7 else 9
        for (next in plan(PuzzleState(room, Puzzle.levers(bot)), target) ?: return false) {
            if (next.crossing != null) {
                val door = next.crossing
                val stand = door.stand(room)
                if (!localWalk(stand) ||
                    !click(door.id, door.tile, local = true, policy = Puzzle.doorReach) {
                        bot.position == door.stand(next.state.room)
                    }) return false
            } else if (next.lever != null) {
                val lever = next.lever
                if (!click(lever.id, lever.tile, local = true, policy = Puzzle.leverReach) {
                        Puzzle.levers(bot) == next.state.levers
                    }) return false
            } else if (!localWalk(roomTiles.getValue(next.state.room))) {
                return false
            }
            room = next.state.room
        }
        return if (target == 9) collect(Quest.OIL_CAN, Puzzle.oilCan, local = true)
        else click(Manor.LADDER_UP, Manor.ladderUp, local = true, done = ::inStudy)
    }

    /**
     * A room and lever combination in the basement.
     *
     * @author lare96
     */
    private data class PuzzleState(val room: Int, val levers: Int)

    /**
     * The next room or lever state and the native interaction needed to reach it.
     *
     * @author lare96
     */
    private data class PuzzleMove(val state: PuzzleState, val crossing: Door? = null, val lever: Lever? = null)

    private fun plan(start: PuzzleState, target: Int): List<PuzzleMove>? {
        val visited = mutableSetOf(start)
        val queue = ArrayDeque<Pair<PuzzleState, List<PuzzleMove>>>()
        queue.add(start to emptyList())
        while (queue.isNotEmpty()) {
            val (state, path) = queue.removeFirst()
            if (state.room == target) return path
            connectedRooms[state.room]?.let { adjacent ->
                val next = state.copy(room = adjacent)
                if (visited.add(next)) queue.add(next to (path + PuzzleMove(next)))
            }
            for (door in Door.entries) {
                if (!door.isOpen(state.levers)) continue
                val nextRoom = when (state.room) {
                    door.firstRoom -> door.secondRoom
                    door.secondRoom -> door.firstRoom
                    else -> continue
                }
                val next = state.copy(room = nextRoom)
                if (visited.add(next)) queue.add(next to (path + PuzzleMove(next, crossing = door)))
            }
            for (lever in Lever.entries) {
                if (lever.room != state.room) continue
                val next = state.copy(levers = state.levers xor lever.bit)
                if (visited.add(next)) queue.add(next to (path + PuzzleMove(next, lever = lever)))
            }
        }
        return null
    }

    private suspend fun talk(id: Int, at: Position, choices: List<String> = emptyList(),
                             speech: Set<String> = emptySet(), done: () -> Boolean): Boolean {
        val npc = bot.world.locator.findNpcs(at, 10) {
            it.id == id && it.z == at.z && it.isAlive && it.state == EntityState.ACTIVE
        }.minByOrNull { it.position.computeLongestDistance(at) } ?: return false
        if (!handler.interactions.approach(npc, timeout = 90.seconds, allowOutOfView = true, canContinue = ::live)) return false
        if (choices.isEmpty()) return handler.interactions.interactLocally(1, npc, allowed = ::live) && await(30_000, done)
        return BotNpcDialogues.respond(bot, npc, choices, completed = done, playerSpeech = speech,
            advanceSpeech = true, allowed = ::live) {
            handler.interactions.interactLocally(1, npc, allowed = ::live)
        }
    }

    private suspend fun collect(id: Int, at: Position, local: Boolean = false): Boolean {
        val item = bot.world.locator.findItems(at, 0) { it.id == id && it.state == EntityState.ACTIVE && (it.isGlobal || it.view.isViewableFor(bot)) }.firstOrNull()
            ?: return false
        val picked = if (local) handler.interactions.interactLocally(1, item, allowed = ::live)
                     else handler.interactions.interact(1, item, ::live)
        return picked && await { has(id) }
    }

    private suspend fun click(id: Int, at: Position, local: Boolean = false,
                              policy: InteractionPolicy = InteractionPolicy.STANDARD_SIZE, done: () -> Boolean): Boolean {
        val obj = objectAt(id, at) ?: return false
        val reached = if (local) handler.interactions.approachLocally(obj, policy)
                      else handler.interactions.approach(obj, policy, timeout = 90.seconds, canContinue = ::live)
        return reached && live() && bot.output.sendObjectInteraction(1, obj) && await { done() && !bot.isLocked }
    }

    private suspend fun use(item: Int, objectId: Int, at: Position, done: () -> Boolean): Boolean {
        val obj = objectAt(objectId, at) ?: return false
        return handler.inventory.useItem(item).onObject(obj, ::live) && await(done = done)
    }

    private fun objectAt(id: Int, at: Position): GameObject? =
        bot.world.locator.findObjectsOnTile(at) { it.id == id && it.state == EntityState.ACTIVE }.firstOrNull()

    private suspend fun localWalk(at: Position): Boolean =
        bot.position == at || withTimeoutOrNull(15_000) { bot.navigator.navigate(at, false).await() } == NavigationResult.REACHED

    private suspend fun walk(at: Position): Boolean =
        WebWalker.webWalk(bot, at, radius = 0, attempts = 2, bank = BankAccess.NONE, fallback = false).success

    private suspend fun await(timeout: Long = 9_000, done: () -> Boolean): Boolean = withTimeoutOrNull(timeout) {
        while (live() && !done()) delay(150)
        live() && done()
    } == true

    private fun fountainPoisoned() = Manor.fountainPoisoned(bot)
    private fun stage() = Quest.stage(bot)
    private fun has(item: Int) = bot.inventory.contains(item)
    private fun live() = bot.state == EntityState.ACTIVE && bot.isAlive
    private fun inBasement() = bot.z == 0 && bot.x in 3089..3122 && bot.y in 9742..9770
    private fun inStudy() = bot.z == 0 && bot.x in 3091..3096 && bot.y in 3354..3363
    private fun inCloset() = bot.z == 0 && bot.x in closetInside.x..3112 && bot.y in 3366..3368
}
