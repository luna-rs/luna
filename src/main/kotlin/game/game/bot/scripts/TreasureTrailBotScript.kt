package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.Suspendable.waitFor
import api.bot.action.CombatLoadout
import engine.combat.prayer.CombatPrayer
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotGearPurpose
import engine.controllers.Controllers.inWilderness
import engine.controllers.WildernessLocatableController.levelAt
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.webwalk.plan.BankAccess
import api.bot.zone.Zone
import api.predef.*
import api.predef.ext.*
import game.player.item.consume.food.Food
import com.google.gson.JsonObject
import game.content.treasureTrails.EasyClues
import game.content.treasureTrails.EasyClues.Clue
import game.content.treasureTrails.EasyClues.Kind
import game.content.treasureTrails.TreasureTrails
import game.content.treasureTrails.TreasureTrails.trailSteps
import game.content.treasureTrails.TrailClues
import game.content.treasureTrails.TrailNavigation
import game.content.treasureTrails.TrailNavigation.chartProgress
import game.content.treasureTrails.TrailPuzzle
import game.content.treasureTrails.TrailGuardians
import game.content.treasureTrails.TrailGuardians.defeatedGuardian
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.overlay.NumberInput
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration.Companion.seconds

/**
 * Solves owned easy, medium and hard Treasure Trails through ordinary inventory, NPC, object and spade interactions.
 * Native clue data owns every target, scroll transition, roll and reward. No clues or rewards are generated here.
 *
 * A trail changes destination after each step, so this travelling activity uses the common BotScript safety
 * lifecycle rather than pinning an inventory script to a fixed work zone. Existing webwalking and zone travel
 * handle navigation. Missing targets or failed interactions are bounded; the scroll is retained for another trip.
 * Banking reserves room for rewards and supplies an owned spade, navigation tools, keys and auxiliary items.
 * Suppliers grant missing navigation tools through ordinary conversations. Keys require their actual NPC kills;
 * guardians require owned melee supplies, account-build-safe styles and native owner-specific kill credit.
 * Puzzle boxes use legal, acknowledged interface moves and challenge answers use the normal number input.
 *
 * @author lare96
 */
class TreasureTrailBotScript(bot: Bot, private val data: TrailData = TrailData()) : BotScript(bot) {
    override val actionSafety = ActionSafety.COMBAT
    companion object {
        /**
         * Failed or timed-out trails wait thirty minutes before general activities consider another attempt.
         */
        private var Bot.trailRetryAt by Attr.long().persist("bot_trail_retry_at")

        /**
         * Existing digging tool used by native map clues.
         */
        const val SPADE = 952

        /**
         * Native scrolls and intermediate caskets for every implemented tier.
         */
        val TRAIL_ITEMS = TrailClues.byId.keys + TrailClues.byCasket.keys

        /**
         * Saves the remaining trip budget and consecutive failures. Native attributes and inventory own clue progress.
         *
         * @author lare96
         */
        class TrailData : BotScriptData() {
            /**
             * Remaining active trip time, excluding time offline or paused.
             */
            var remainingMillis = 30L * 60 * 1000
            /**
             * Consecutive interactions without a native scroll or casket transition.
             */
            var failures = 0

            override fun load(data: JsonObject) {
                remainingMillis = data.get("remainingMillis")?.asLong ?: 30L * 60 * 1000
                failures = data.get("failures")?.asInt ?: 0
            }

            override fun save(data: JsonObject) {
                data.addProperty("remainingMillis", remainingMillis)
                data.addProperty("failures", failures)
            }
        }

        /**
         * Selects only an already-owned trail with an owned spade, avoiding free or synthetic supplies.
         */
        fun eligible(bot: Bot): Boolean = bot.trailRetryAt <= System.currentTimeMillis() && TRAIL_ITEMS.any { bot.inventory.contains(it) || bot.bank.contains(it) } &&
            (bot.inventory.contains(SPADE) || bot.bank.contains(SPADE))

        /**
         * Collects one supported clue without pricing an untradeable scroll or acquiring duplicate trails.
         */
        fun wantsClue(bot: Bot, id: Int): Boolean = id in TRAIL_ITEMS && !TreasureTrails.hasTrail(bot)

        /**
         * Native step count handles repeated same-ID clues without mistaking a guardian spawn for completion.
         */
        internal fun progressed(bot: Bot, id: Int, steps: Int): Boolean =
            !bot.inventory.contains(id) || bot.trailSteps != steps

        /**
         * Uses the native clue categories to select the real object's menu option. Closed empty drawers
         * are opened first; their replacement is then searched on the following interaction.
         */
        internal fun objectOption(clue: Clue, id: Int): Int? = when (clue.kind) {
            Kind.CHEST_CLOSED -> when (id) {
                in EasyClues.closedChests -> 1
                in EasyClues.openChests -> 2
                else -> null
            }
            Kind.EMPTY_CRATE -> if (id in EasyClues.emptyCrate) 1 else null
            Kind.EMPTY_CRATES -> if (id in EasyClues.emptyCrates) 1 else null
            Kind.EMPTY_BOXES -> if (id in EasyClues.emptyBoxes) 1 else null
            Kind.FALADOR_CRATES -> if (id == 11745) 1 else null
            Kind.DRAWER, Kind.EMPTY_DRAWER -> when (id) {
                in EasyClues.drawer -> 1
                in EasyClues.emptyDrawer -> 2
                else -> null
            }
            null -> if (id in clue.targetObjects) 1 else null
        }
    }

    /**
     * Monotonic checkpoint for the active trip budget.
     */
    private var checkpoint = 0L

    /**
     * The last clue whose combat supplies were prepared; native inventory remains authoritative after a resume.
     */
    private var combatPreparedFor = -1

    /**
     * Resolves scroll and intermediate-casket metadata through the same native catalogue.
     */
    private fun clue(id: Int): Clue = TrailClues.byId[id] ?: TrailClues.byCasket.getValue(id)

    /**
     * Combat is required only for an unearned guardian or missing container key.
     */
    private fun needsCombat(clue: Clue): Boolean =
        (clue.guardian && bot.inventory.contains(clue.id) && bot.defeatedGuardian != clue.id) ||
            (clue.key != -1 && !bot.inventory.contains(clue.key) && !bot.bank.contains(clue.key))

    /**
     * Finds this bot's temporary wizard without selecting someone else's encounter.
     */
    private fun guardian(): Npc? = world.npcs.firstOrNull { TrailGuardians.owner(it) === bot && it.isAlive }


    override suspend fun init(resumed: Boolean): Boolean {
        checkpoint = System.nanoTime()
        if (data.failures >= 3 || data.remainingMillis <= 0 || !eligible(bot)) return true
        val id = TRAIL_ITEMS.firstOrNull { bot.inventory.contains(it) || bot.bank.contains(it) } ?: return true
        val prepared = prepare(id)
        if (!prepared) data.failures = 3
        return !prepared
    }

    /**
     * Uses normal unnoted banking and verifies both the selected trail and owned digging tool arrived.
     */
    private suspend fun prepare(id: Int): Boolean = withTimeoutOrNull(120_000) {
        if (bot.inWilderness() && !handler.combat.fleeWilderness()) return@withTimeoutOrNull false
        if (!awaitActionSafety(ActionSafety.NON_COMBAT)) return@withTimeoutOrNull false
        val current = clue(id)
        val combat = needsCombat(current)
        if (combat) {
            if (current.guardian && bot.combatLevel < 70) return@withTimeoutOrNull false
            val loadout = CombatLoadout.find(bot, BotGearPurpose.MELEE) ?: return@withTimeoutOrNull false
            if (!BotGearLocator(bot, loadout.gear).locateAndEquip()) return@withTimeoutOrNull false
            bot.combat.magic.selectedSpell = CombatSpellDefinition.NONE
            bot.combat.magic.autocastSpell = CombatSpellDefinition.NONE
            bot.combat.magic.isAutocasting = false
            bot.combat.magic.refreshAutocast()
            if (!handler.combat.prepareTrainingStyle()) return@withTimeoutOrNull false
        }
        if (!handler.banking.travelToBankDepositAll() || !awaitActionSafety(ActionSafety.NON_COMBAT)) return@withTimeoutOrNull false
        handler.banking.clickBankingMode(false)
        val optional = (TrailNavigation.equipment + listOf(current.key, current.auxiliary))
            .filter { it > 0 && bot.bank.contains(it) }
        if (!handler.banking.withdrawAll((listOf(id, SPADE) + optional).distinct().map { Item(it) })) return@withTimeoutOrNull false
        if (combat && (!handler.banking.withdrawAnyFood(8, minimumHeal = 12, retry = false) ||
                bot.inventory.filterNotNull().count { (Food.ID_TO_FOOD[it.id]?.heal ?: 0) >= 12 } < 4)) return@withTimeoutOrNull false
        if (combat) combatPreparedFor = current.id
        bot.inventory.contains(id) && bot.inventory.contains(SPADE) && handler.widgets.clickCloseInterface()
    } == true

    /**
     * Reuses existing navigation, with the registered parent zone's travel fallback when direct webwalking fails.
     */
    private suspend fun reach(position: Position): Boolean {
        if (levelAt(position) > 0 && !handler.combat.pkRiskBudget().withinBudget) return false
        if (!awaitActionSafety(ActionSafety.NON_COMBAT)) return false
        if (bot.position.isWithinDistance(position, 1)) return true
        if (handler.webWalkTo(position, radius = 1) && isActionSafe(ActionSafety.NON_COMBAT)) return true
        if (!awaitActionSafety(ActionSafety.NON_COMBAT)) return false
        val parent = Zone.REGIONS[position.regionId] ?: return false
        return handler.webWalkTo(zone = parent, bank = BankAccess.COINS) && awaitActionSafety(ActionSafety.NON_COMBAT) &&
            handler.webWalkTo(position, radius = 1) && isActionSafe(ActionSafety.NON_COMBAT)
    }

    /**
     * Resolves only the clue's exact tile and a supported object variant, including opened replacements.
     */
    private fun findObject(clue: Clue): GameObject? = clue.position?.let { position ->
        world.locator.findObjectsOnTile(position) { objectOption(clue, it.id) != null }.firstOrNull()
    }

    /**
     * Starts the native solving interaction without mutating trail attributes, inventory or rewards directly.
     */
    private suspend fun solve(clue: Clue): Boolean {
        if (clue.npcId != -1) return speakClue(clue)
        if (clue.coordinate && !navigationTools()) return false
        if (clue.key != -1 && !bot.inventory.contains(clue.key) && !collectKey(clue)) return false
        val position = clue.position ?: return false
        if (!reach(position) || !isActionSafe(ActionSafety.NON_COMBAT)) return false
        if (clue.kind == null && clue.targetObjects.isEmpty()) {
            if (!handler.inventory.clickItem(1, SPADE)) return false
            if (clue.guardian && bot.defeatedGuardian != clue.id) {
                if (!waitFor(5.seconds) { guardian() != null || !bot.inventory.contains(clue.id) }) return false
                val wizard = guardian() ?: return !bot.inventory.contains(clue.id)
                if (!fight(wizard) || !waitFor(5.seconds) { bot.defeatedGuardian == clue.id }) return false
                if (!awaitActionSafety(ActionSafety.NON_COMBAT) || !reach(position)) return false
                return handler.inventory.clickItem(1, SPADE)
            }
            return true
        }
        val target = findObject(clue) ?: return false
        if (!handler.interactions.interact(objectOption(clue, target.id) ?: return false, target)) return false
        if (clue.kind == Kind.EMPTY_DRAWER && target.id in EasyClues.drawer) {
            if (!waitFor(3.seconds) { findObject(clue)?.id in EasyClues.emptyDrawer }) return false
            if (!isActionSafe(ActionSafety.NON_COMBAT)) return false
            val opened = findObject(clue) ?: return false
            return handler.interactions.interact(2, opened)
        }
        return true
    }

    /**
     * Gets missing coordinate tools from Professor, Murphy and Brother Kojo in their native order.
     * Existing owned tools are withdrawn during preparation, so suppliers never duplicate banked equipment.
     */
    private suspend fun navigationTools(): Boolean {
        if (TrailNavigation.hasEquipment(bot)) return true
        val visits = buildList {
            if (bot.chartProgress == 0) add(488 to 0)
            if (!bot.inventory.contains(2574)) add(463 to 2574)
            if (!bot.inventory.contains(2575)) add(223 to 2575)
            if (!bot.inventory.contains(2576)) add(488 to 2576)
        }
        for ((npcId, item) in visits) {
            val npc = world.npcs.firstOrNull { it.id == npcId && it.state == EntityState.ACTIVE } ?: return false
            if (!reach(npc.position) || !handler.interactions.interact(1, npc) ||
                !waitFor(5.seconds) { if (item == 0) bot.chartProgress > 0 else bot.inventory.contains(item) }) return false
            if (!isActionSafe(ActionSafety.NON_COMBAT) || !handler.widgets.clickCloseInterface()) return false
        }
        return TrailNavigation.hasEquipment(bot)
    }

    /**
     * Handles native challenge and puzzle handoffs before returning to the same clue NPC for advancement.
     */
    private suspend fun speakClue(clue: Clue): Boolean {
        val npc = world.npcs.firstOrNull { it.id == clue.npcId && it.state == EntityState.ACTIVE && it.isAlive } ?: return false
        val steps = bot.trailSteps
        if (!reach(npc.position) || !handler.interactions.interact(1, npc)) return false
        if (clue.auxiliary == -1) return true
        if (!waitFor(5.seconds) { bot.inventory.contains(clue.auxiliary) || progressed(bot, clue.id, steps) }) return false
        if (progressed(bot, clue.id, steps)) return true
        if (clue.puzzle) {
            if (!solvePuzzle(clue.auxiliary) || !handler.widgets.clickCloseInterface() ||
                !awaitActionSafety(ActionSafety.NON_COMBAT)) return false
            return handler.interactions.interact(1, npc)
        }
        if (!handler.inventory.clickItem(1, clue.auxiliary) || !waitFor(3.seconds) { bot.overlays.hasWindow() } ||
            !handler.widgets.clickCloseInterface() || !awaitActionSafety(ActionSafety.NON_COMBAT) ||
            !handler.interactions.interact(1, npc) || !waitFor(5.seconds) { NumberInput::class in bot.overlays }) return false
        return isActionSafe(ActionSafety.NON_COMBAT) && handler.widgets.enterAmount(clue.answer)
    }

    /**
     * Applies a legal 24-puzzle plan through the native item-widget packets and verifies every displayed move.
     */
    private suspend fun solvePuzzle(box: Int): Boolean {
        if (!handler.inventory.clickItem(1, box) || !waitFor(5.seconds) { TrailPuzzle.PuzzleInterface::class in bot.overlays }) return false
        val tiles = TrailPuzzle.tiles(bot) ?: return false
        val moves = withContext(Dispatchers.Default) { TreasureTrailPuzzleSolver.plan(tiles) } ?: return false
        var board = tiles
        for (index in moves) {
            if (!isActionSafe(ActionSafety.NON_COMBAT) || TrailPuzzle.PuzzleInterface::class !in bot.overlays) return false
            val blank = board.indexOf(0)
            if (!TrailPuzzle.adjacent(index, blank) || board[index] == 0) return false
            val expected = board.toMutableList().also { it[blank] = it[index]; it[index] = 0 }
            if (!handler.widgets.clickItemAction(6976, 6980, 5, index, 2748 + board[index]) ||
                !waitFor(3.seconds) { TrailPuzzle.tiles(bot) == expected }) return false
            board = expected
        }
        return TrailPuzzle.solved(bot, box)
    }

    /**
     * Kills only a matching key NPC and collects the ordinary owner-visible key drop.
     */
    private suspend fun collectKey(clue: Clue): Boolean {
        val target = world.npcs.filter { it.id in clue.keyNpcs && it.isAlive && it.state == EntityState.ACTIVE &&
            (it.combat.target == null || it.combat.target === bot) }
            .minByOrNull { it.position.computeLongestDistance(bot.position) } ?: return false
        if (handler.combat.isThreat(target) || !reach(target.position)) return false
        if (!fight(target)) return false
        val deathPosition = target.position
        var key: GroundItem? = null
        if (!waitFor(10.seconds) {
            key = world.locator.findItems(deathPosition, 16) {
                it.id == clue.key && it.state == EntityState.ACTIVE && it.isVisibleTo(bot)
            }.firstOrNull()
            key != null
        }) return false
        return awaitActionSafety(ActionSafety.NON_COMBAT) && handler.interactions.interact(1, key ?: return false) &&
            waitFor(5.seconds) { bot.inventory.contains(clue.key) }
    }

    /**
     * Uses the existing melee style and food handlers; XP caps, player threats and retreat remain authoritative.
     * Guardian credit is granted only by the native NPC death hook, never by this controller.
     */
    private suspend fun fight(npc: Npc): Boolean {
        val disabled = bot.reflex.isDisableCombatReflex
        val wizard = TrailGuardians.owner(npc) === bot
        val protections = listOf(CombatPrayer.PROTECT_FROM_MAGIC, CombatPrayer.PROTECT_FROM_MISSILES,
            CombatPrayer.PROTECT_FROM_MELEE)
        val previousPrayers = protections.filter { bot.combat.prayers.isActive(it) }
        bot.reflex.isDisableCombatReflex = true
        try {
            while (npc.isAlive && npc.state == EntityState.ACTIVE) {
                if (!isActionSafe(ActionSafety.COMBAT)) { if (!awaitActionSafety(ActionSafety.COMBAT)) return false }
                if (bot.combat.lastCombatWith is io.luna.game.model.mob.Player && bot.combat.inCombat() ||
                    bot.inWilderness() && !handler.combat.pkRiskBudget().withinBudget ||
                    !handler.combat.prepareTrainingStyle()) return escape()
                if (wizard && bot.prayer.staticLevel >= 37 && bot.prayer.level > 0)
                    bot.combat.prayers.activateIfOff(CombatPrayer.PROTECT_FROM_MAGIC)
                if (bot.healthPercent < 60 && !handler.inventory.eatAnyFood(minimumHeal = 12)) return escape()
                if (!bot.inventory.contains(SPADE)) return escape()
                if (bot.combat.target !== npc && !handler.interactions.interact(3, npc)) return escape()
                waitFor(1.seconds) { !npc.isAlive || npc.state != EntityState.ACTIVE }
            }
            return true
        } finally {
            bot.reflex.isDisableCombatReflex = disabled
            if (wizard && CombatPrayer.PROTECT_FROM_MAGIC !in previousPrayers)
                bot.combat.prayers.deactivate(CombatPrayer.PROTECT_FROM_MAGIC, true)
            if (wizard) previousPrayers.forEach { bot.combat.prayers.activateIfOff(it) }
        }
    }

    /**
     * Abandons the current attempt through existing escape behavior; the clue stays owned.
     */
    private suspend fun escape(): Boolean {
        bot.combat.target = null
        if (bot.inWilderness()) handler.combat.fleeWilderness() else handler.combat.fleeCombat()
        return false
    }

    override suspend fun run(): Boolean {
        val now = System.nanoTime()
        data.remainingMillis -= (now - checkpoint) / 1_000_000
        checkpoint = now
        if (data.remainingMillis <= 0 || data.failures >= 3) return true
        if (bot.combat.inCombat()) {
            val wizard = guardian()
            if (wizard != null) { if (!fight(wizard)) data.failures++ } else escape()
            return false
        }
        if (bot.inWilderness() && !handler.combat.pkRiskBudget().withinBudget) { escape(); return true }
        val id = TRAIL_ITEMS.firstOrNull { bot.inventory.contains(it) } ?: return true
        val current = clue(id)
        if ((needsCombat(current) && combatPreparedFor != current.id) ||
            (current.key > 0 && !bot.inventory.contains(current.key) && bot.bank.contains(current.key)) ||
            (current.auxiliary > 0 && !bot.inventory.contains(current.auxiliary) && bot.bank.contains(current.auxiliary)) ||
            bot.inventory.computeRemainingSize() < 12) {
            if (!prepare(id)) data.failures++
            return data.failures >= 3
        }
        if (!awaitActionSafety(ActionSafety.NON_COMBAT) || !handler.widgets.clickCloseInterface()) return false
        val steps = bot.trailSteps
        val started = withTimeoutOrNull(600_000) {
            if (id in TrailClues.byCasket) handler.inventory.clickItem(1, id) else {
                // Read the player's actual scroll before following its target.
                if (!handler.inventory.clickItem(1, id) || !waitFor(3.seconds) { bot.overlays.hasWindow() } ||
                    !awaitActionSafety(ActionSafety.NON_COMBAT) || !handler.widgets.clickCloseInterface()) false
                else solve(TrailClues.byId.getValue(id))
            }
        } == true
        val progressed = progressed(bot, id, steps) || (started && waitFor(10.seconds) { progressed(bot, id, steps) })
        if (progressed) data.failures = 0 else if (isActionSafe()) data.failures++
        // A repeated clue may need another guardian; never reuse its old supply-preparation marker.
        combatPreparedFor = -1
        return data.failures >= 3
    }

    override fun allowsLoot(item: GroundItem): Boolean = false

    override suspend fun finish() {
        TrailGuardians.leave(bot, reset = false)
        bot.combat.target = null
    }

    override suspend fun completed() {
        if (data.failures > 0 || data.remainingMillis <= 0) bot.trailRetryAt = System.currentTimeMillis() + 30L * 60 * 1000
        withTimeoutOrNull(60_000) {
            if (bot.inWilderness()) handler.combat.fleeWilderness()
            if (awaitActionSafety(ActionSafety.NON_COMBAT)) handler.banking.travelToBankDepositAll()
        }
    }

    override fun snapshot(): TrailData = TrailData().also {
        it.failures = data.failures
        val elapsed = if (checkpoint == 0L) 0 else (System.nanoTime() - checkpoint) / 1_000_000
        it.remainingMillis = (data.remainingMillis - elapsed).coerceAtLeast(0)
    }
}

/**
 * Plans legal moves for a 5-by-5 sliding puzzle without modifying any player or interface state.
 * Rows are fixed left-to-right, with the last two tiles placed together. The final two rows are fixed in
 * column pairs, leaving a solvable 2-by-2 finish. Small projected breadth-first searches track only the
 * current target tiles and blank; completed tiles are never moved. Search size is bounded by 25P3 for
 * a pair and the final four-cell permutation, avoiding an unbounded full-board A-star search.
 *
 * @author lare96
 */
internal object TreasureTrailPuzzleSolver {
    /**
     * Returns the displayed tile indices to click, or null for a malformed or unsolvable board.
     */
    fun plan(tiles: List<Int>): List<Int>? {
        if (tiles.size != 25 || tiles.toSet() != (0..24).toSet()) return null
        val numbered = tiles.filter { it != 0 }
        val inversions = numbered.indices.sumOf { i -> (i + 1 until numbered.size).count { numbered[i] > numbered[it] } }
        if (inversions % 2 != 0) return null
        val board = tiles.toIntArray()
        val locked = BooleanArray(25)
        val moves = mutableListOf<Int>()
        fun place(goals: List<Pair<Int, Int>>): Boolean {
            val path = search(board, locked, goals) ?: return false
            for (index in path) {
                val blank = board.indexOf(0)
                board[blank] = board[index]
                board[index] = 0
                moves += index
            }
            goals.forEach { locked[it.second] = true }
            return true
        }
        for (row in 0..2) {
            for (column in 0..2) {
                val target = row * 5 + column
                if (!place(listOf(target + 1 to target))) return null
            }
            if (!place(listOf(row * 5 + 4 to row * 5 + 3, row * 5 + 5 to row * 5 + 4))) return null
        }
        for (column in 0..2) {
            if (!place(listOf(16 + column to 15 + column, 21 + column to 20 + column))) return null
        }
        if (!place(listOf(19 to 18, 20 to 19, 24 to 23))) return null
        return moves.takeIf { board.toList() == (1..24).toList() + 0 }
    }

    /**
     * Finds a shortest legal blank path while projecting unrelated, movable tiles as interchangeable filler.
     */
    private fun search(board: IntArray, locked: BooleanArray, goals: List<Pair<Int, Int>>): List<Int>? {
        val count = goals.size + 1
        fun encode(positions: IntArray): Int = positions.fold(0) { key, position -> key * 25 + position }
        fun decode(key: Int): IntArray {
            var remainder = key
            val positions = IntArray(count)
            for (i in count - 1 downTo 0) { positions[i] = remainder % 25; remainder /= 25 }
            return positions
        }
        val start = encode((goals.map { board.indexOf(it.first) } + board.indexOf(0)).toIntArray())
        val previous = HashMap<Int, Int>()
        val clicked = HashMap<Int, Int>()
        val queue = ArrayDeque<Int>()
        previous[start] = start
        queue.addLast(start)
        while (queue.isNotEmpty()) {
            val key = queue.removeFirst()
            val positions = decode(key)
            if (goals.indices.all { positions[it] == goals[it].second }) {
                val path = mutableListOf<Int>()
                var cursor = key
                while (cursor != start) { path += clicked.getValue(cursor); cursor = previous.getValue(cursor) }
                return path.asReversed()
            }
            val blank = positions.last()
            for (next in listOf(blank - 5, blank + 5, blank - 1, blank + 1)) {
                if (next !in 0..24 || locked[next] || !TrailPuzzle.adjacent(blank, next)) continue
                val updated = positions.copyOf()
                for (i in goals.indices) if (updated[i] == next) updated[i] = blank
                updated[count - 1] = next
                val encoded = encode(updated)
                if (previous.putIfAbsent(encoded, key) == null) {
                    clicked[encoded] = next
                    queue.addLast(encoded)
                }
            }
        }
        return null
    }
}
