package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.Suspendable.waitFor
import api.bot.action.BotCombatActionHandler
import api.bot.action.BotCombatMagic
import api.bot.action.CombatLoadout
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.predef.*
import api.predef.ext.contains
import com.google.gson.JsonObject
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotGearLocator
import engine.controllers.Controllers.inWilderness
import game.content.duelArena.DuelArena
import game.content.duelArena.DuelRules
import game.player.item.consume.food.Food
import io.luna.game.model.EntityState
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerContextMenuOption
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.overlay.StandardInterface
import io.luna.game.model.mob.overlay.NumberInput
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Visits the arena for one supplied melee, Ranged or Magic duel through native challenge and acceptance actions.
 * Bot visitors negotiate compatible native rules and optionally match small GP stakes within their saved budget.
 * Both acceptance screens are checked again after offer changes; interruptions abandon the match.
 *
 * @author lare96
 */
class DuelArenaBotScript(bot: Bot, private var attempted: Boolean = false,
                         private var started: Boolean = false,
                         private val waitingUntil: Long = System.currentTimeMillis() + 300_000,
                         private val deadline: Long = System.currentTimeMillis() + 1_200_000,
                         private var discipline: BotGearPurpose? = null,
                         private var stakeBudget: Int = -1,
                         private var plannedRules: Int? = null) : BotScript(bot) {
    companion object {
        private val DISCIPLINES = listOf(BotGearPurpose.MELEE, BotGearPurpose.RANGED, BotGearPurpose.MAGIC)

        internal fun meleeReady(bot: Bot): Boolean = DuelBotPolicy.loadout(bot, BotGearPurpose.MELEE, true) != null

        fun eligible(bot: Bot): Boolean = ActionSafety.NON_COMBAT.allows(bot) &&
            bot.state == EntityState.ACTIVE && !bot.inWilderness() && DISCIPLINES.any { DuelBotPolicy.loadout(bot, it) != null } &&
            bot.controllers.primary.javaClass == PlayerController::class.java &&
            !bot.overlays.hasWindow() && DuelArena.session(bot) == null

        /**
         * Visitors challenge only other unused visitors; human requests are answered separately.
         * Ordering avoids both bots sending a first request at once.
         */
        internal fun opponent(bot: Bot, candidates: Collection<Player>): Bot? = candidates.filterIsInstance<Bot>()
            .firstOrNull { it !== bot && it.index > bot.index && eligible(it) &&
                DuelArena.inside(it.position) && !DuelArena.arena(it.position) &&
                bot.position.isWithinDistance(it.position, 16) &&
                (it.scriptStack.current() as? DuelArenaBotScript)?.let { script ->
                    !script.attempted && System.currentTimeMillis() < script.waitingUntil
                } == true }

        internal fun acceptable(bot: Bot, duel: DuelArena.Duel): Boolean {
            val visitor = bot.scriptStack.current() as? DuelArenaBotScript
            val purpose = visitor?.discipline ?: DISCIPLINES.firstOrNull { DuelBotPolicy.loadout(bot, it, true) != null }
                ?: return false
            val budget = visitor?.stakeBudget?.coerceAtLeast(0) ?: 0
            val offers = duel.players.map { DuelArena.escrow(it) }
            val amounts = offers.map { offer -> offer.sumOf { it.amount.toLong() } }
            return duel.contains(bot) && DuelRules.valid(duel.rules) &&
                DuelBotPolicy.loadout(bot, purpose, true) != null && DuelBotPolicy.permitted(purpose, duel.rules) &&
                !DuelRules.NO_FORFEIT.enabled(duel.rules) && !DuelRules.FLOWERS.enabled(duel.rules) &&
                offers.all { offer -> offer.all { it.id == 995 } } && amounts.distinct().size == 1 &&
                amounts.all { it <= budget }
        }
    }

    /**
     * Persists the single attempt and absolute deadlines so restored visits cannot start over.
     *
     * @author lare96
     */
    class DuelData : BotScriptData() {
        var attempted = false
        var started = false
        var waitingUntil = 0L
        var deadline = 0L
        var discipline: BotGearPurpose? = null
        var stakeBudget = -1
        var plannedRules: Int? = null
        override fun load(data: JsonObject) {
            attempted = data.get("attempted").asBoolean
            started = data.get("started").asBoolean
            waitingUntil = data.get("waitingUntil").asLong
            deadline = data.get("deadline").asLong
            discipline = if (data.has("discipline")) data.get("discipline").takeUnless { it.isJsonNull }
                ?.asString?.let(BotGearPurpose::valueOf) else BotGearPurpose.MELEE
            stakeBudget = data.get("stakeBudget")?.asInt ?: 0
            plannedRules = data.get("plannedRules")?.takeUnless { it.isJsonNull }?.asInt
        }
        override fun save(data: JsonObject) {
            data.addProperty("attempted", attempted)
            data.addProperty("started", started)
            data.addProperty("waitingUntil", waitingUntil)
            data.addProperty("deadline", deadline)
            data.addProperty("discipline", discipline?.name)
            data.addProperty("stakeBudget", stakeBudget)
            data.addProperty("plannedRules", plannedRules)
        }
    }

    constructor(bot: Bot, data: DuelData) : this(bot, data.attempted, data.started, data.waitingUntil, data.deadline,
        data.discipline, data.stakeBudget, data.plannedRules)

    override val actionSafety = ActionSafety.COMBAT
    override val allowsCombatResponse: Boolean get() = DuelArena.session(bot) == null
    private var observed: DuelArena.Duel? = null
    private var acceptedRevision = -1
    private var acceptedConfirmation = false
    private var failures = 0
    private var cleaned = false
    private var loadout: CombatLoadout? = null
    private val magicTactics = BotCombatMagic(bot, handler)

    override suspend fun init(resumed: Boolean): Boolean {
        if (cleaned || System.currentTimeMillis() >= deadline) return true
        val duel = DuelArena.session(bot)
        if (attempted || started) {
            observed = duel
            discipline?.let { loadout = DuelBotPolicy.loadout(bot, it, true) }
            return duel == null
        }
        if (System.currentTimeMillis() >= waitingUntil || !eligible(bot)) return true
        return withTimeoutOrNull(180_000) {
            !prepareOuting() || !DuelArena.inside(bot.position) && !handler.webWalkTo(DuelArena.LOBBY, radius = 3)
        } ?: true
    }

    override suspend fun run(): Boolean {
        if (cleaned || System.currentTimeMillis() >= deadline) return true
        val duel = DuelArena.session(bot)
        if (duel != null) {
            if (observed != null && observed !== duel) return true
            observed = duel
            attempted = true
            if (duel.started) {
                started = true
                if (!isActionSafe() || duel.countdown > 0) { delay(1.seconds); return false }
                val purpose = discipline ?: return true
                val current = DuelBotPolicy.loadout(bot, purpose, true) ?: return true
                if (!DuelBotPolicy.permitted(purpose, duel.rules)) return true
                loadout = current
                val other = duel.other(bot)
                if (!duel.fighting(bot, other)) { delay(1.seconds); return false }
                if (!DuelRules.NO_FOOD.enabled(duel.rules) && bot.emotions.isNervousAboutHp) {
                    val food = bot.inventory.withIndex().firstOrNull { it.value?.id in Food.ID_TO_FOOD }
                    if (food != null) {
                        handler.inventory.eat(food.index, food.value!!.id)
                        delay(1.seconds)
                        return false
                    }
                }
                if (!prepareAttack(other, current)) { delay(1.seconds); return false }
                if (bot.combat.target !== other) {
                    val attacked = withTimeoutOrNull(10_000) {
                        handler.interactions.interact(PlayerContextMenuOption.ATTACK.index, other) {
                            DuelArena.session(bot) === duel && duel.fighting(bot, other) &&
                                isActionSafe() && DuelBotPolicy.loadout(bot, purpose, true) != null &&
                                DuelBotPolicy.permitted(purpose, duel.rules) && System.currentTimeMillis() < deadline
                        }
                    } == true
                    failures = if (attacked) 0 else failures + 1
                }
            } else {
                if (System.currentTimeMillis() >= waitingUntil || !isActionSafe()) return true
                if (!duel.confirmation && !negotiate(duel)) { delay(1.seconds); return failures >= 5 }
                if (!acceptable(bot, duel)) {
                    handler.widgets.clickButton(if (duel.confirmation) 6412 else 6575,
                        if (duel.confirmation) 6558 else 6676)
                    return true
                }
                if (acceptedRevision != duel.revision || acceptedConfirmation != duel.confirmation) {
                    val revision = duel.revision
                    val confirmation = duel.confirmation
                    if (!handler.widgets.clickButton(if (confirmation) 6412 else 6575,
                            if (confirmation) 6520 else 6674)) return true
                    acceptedRevision = revision
                    acceptedConfirmation = confirmation
                }
            }
            delay(1.seconds)
            return failures >= 5
        }
        if (observed != null || started || System.currentTimeMillis() >= waitingUntil ||
            !DuelArena.inside(bot.position) || DuelArena.arena(bot.position) || !isActionSafe() ||
            bot.overlays.hasWindow()) return true
        val target = DuelArena.challengers(bot).firstOrNull() ?: if (!attempted)
            opponent(bot, world.locator.findPlayers(bot.position, 16) { true }) else null
        if (target != null) {
            attempted = true
            val challenged = withTimeoutOrNull(15_000) {
                handler.interactions.interact(PlayerContextMenuOption.CHALLENGE.index, target) {
                    isActionSafe() && System.currentTimeMillis() < waitingUntil &&
                        DuelArena.inside(bot.position) && DuelArena.inside(target.position) &&
                        DuelArena.session(bot) == null && DuelArena.session(target) == null
                }
            } == true
            if (!challenged) return true
        }
        delay(1.seconds)
        return false
    }

    /**
     * Prepares one owned discipline and a small optional stake before sending any challenge. Supplies and wallet
     * contents pass through normal banking, while the gear locator retains the account's native equip checks.
     */
    private suspend fun prepareOuting(): Boolean {
        val purpose = discipline ?: DISCIPLINES.filter { DuelBotPolicy.loadout(bot, it) != null }.randomOrNull()
            ?: return false
        discipline = purpose
        val selected = DuelBotPolicy.loadout(bot, purpose) ?: return false
        if (stakeBudget < 0) {
            val chance = 0.1 + bot.personality.confidence.coerceIn(0.0, 1.0) * 0.4
            stakeBudget = if (rand(chance)) DuelBotPolicy.stakeLimit(bot) else 0
        }
        if (!BotGearLocator(bot, selected.gear).locateAndEquip()) return false
        val runeStock = selected.runes.mapValues { (_, amount) -> amount * 50 }.toMutableMap()
        if (purpose == BotGearPurpose.MAGIC) {
            for ((id, amount) in BotCombatMagic.optionalRuneStock(bot, selected.gear[Equipment.WEAPON], selected.gear)) {
                runeStock[id] = maxOf(runeStock[id] ?: 0, amount)
            }
        }
        val supplies = runeStock.map { (id, amount) -> Item(id, amount) }.toMutableList()
        if (stakeBudget > 0) supplies += Item(995, stakeBudget)
        val food = Food.ID_TO_FOOD.entries.filter { it.key in bot.bank && it.value.heal > 0 }
            .maxByOrNull { it.value.heal }?.key
        if (food != null) supplies += Item(food, minOf(8, bot.bank.computeAmountForId(food)))
        if (supplies.isNotEmpty() && !handler.banking.travelToBankWithdraw(supplies)) return false
        if (selected.runes.any { (id, amount) -> bot.inventory.computeAmountForId(id) < amount } ||
            bot.inventory.computeAmountForId(995) < stakeBudget) return false
        loadout = DuelBotPolicy.loadout(bot, purpose, true) ?: return false
        return true
    }

    /**
     * Keeps all native duel prohibitions in force. Manual damage and utility spells use the same combat Magic
     * policy as ordinary supplied fights; immutable PK styles and XP caps still constrain the chosen discipline.
     */
    private suspend fun prepareAttack(other: Player, selected: CombatLoadout): Boolean {
        val purpose = discipline ?: return false
        if (purpose == BotGearPurpose.MAGIC) {
            val spell = selected.spell ?: return false
            if (spell.button != -1 && (!bot.combat.magic.isAutocasting || bot.combat.magic.autocastSpell != spell) &&
                !handler.combat.setAutocastSpell(spell)) return false
            if (spell.button == -1) {
                bot.combat.magic.isAutocasting = false
                bot.combat.magic.autocastSpell = CombatSpellDefinition.NONE
            }
            return magicTactics.prepare(other, spell)
        }
        bot.combat.magic.selectedSpell = CombatSpellDefinition.NONE
        bot.combat.magic.autocastSpell = CombatSpellDefinition.NONE
        bot.combat.magic.isAutocasting = false
        val style = BotCombatActionHandler.allowedStyles(bot, bot.combat.weapon.typeDef.styles).firstOrNull {
            (SKILL_RANGED in it.exp) == (purpose == BotGearPurpose.RANGED)
        } ?: return false
        if (bot.combat.weapon.styleDef != style) bot.combat.weapon.changeStyle(style.button)
        return true
    }

    /**
     * Only the lower-index bot proposes rules, preventing two visitors from repeatedly undoing one another.
     * Both prepared disciplines remain legal. Human rule choices are reviewed without rewriting their proposal.
     */
    private suspend fun negotiate(duel: DuelArena.Duel): Boolean {
        val other = duel.other(bot)
        val visitor = (other as? Bot)?.scriptStack?.current() as? DuelArenaBotScript
        if (visitor != null && bot.index < other.index) {
            val desired = plannedRules ?: buildRules(visitor.discipline).also { plannedRules = it }
            val rule = DuelRules.entries.firstOrNull { it.enabled(desired) != it.enabled(duel.rules) }
            if (rule != null) {
                if (!handler.widgets.clickButton(6575, rule.button)) failures++
                return false
            }
        }
        val offers = duel.players.map { DuelArena.escrow(it) }
        if (offers.any { offer -> offer.any { it.id != 995 } }) return true
        val own = DuelArena.escrow(bot).sumOf { it.amount.toLong() }
        val theirs = DuelArena.escrow(other).sumOf { it.amount.toLong() }
        val target = if (visitor != null) minOf(stakeBudget.coerceAtLeast(0), visitor.stakeBudget.coerceAtLeast(0)).toLong()
            else theirs
        if (target > stakeBudget.coerceAtLeast(0)) return true
        if (own != target) {
            val changed = changeStake(duel, target.toInt())
            failures = if (changed) 0 else failures + 1
            return false
        }
        return theirs == target
    }

    /**
     * Uses native rule bits and avoids incompatible no-melee/no-movement combinations and inescapable sessions.
     */
    private fun buildRules(other: BotGearPurpose?): Int {
        val purposes = setOfNotNull(discipline, other)
        var mask = 0
        fun add(rule: DuelRules, enabled: Boolean) { if (enabled) mask = mask or (1 shl rule.bit) }
        add(DuelRules.NO_MELEE, BotGearPurpose.MELEE !in purposes)
        add(DuelRules.NO_RANGED, BotGearPurpose.RANGED !in purposes)
        add(DuelRules.NO_MAGIC, BotGearPurpose.MAGIC !in purposes)
        add(DuelRules.NO_MOVEMENT, purposes == setOf(BotGearPurpose.MELEE) && rand(0.5))
        add(DuelRules.NO_FOOD, rand(0.25))
        add(DuelRules.NO_POTIONS, rand(0.5))
        add(DuelRules.NO_PRAYER, rand(0.25))
        add(DuelRules.NO_SPECIAL, rand(0.5))
        add(DuelRules.OBSTACLES, !DuelRules.NO_MOVEMENT.enabled(mask) && rand(0.25))
        return mask.takeIf(DuelRules::valid) ?: 0
    }

    /**
     * Offers or withdraws an exact bounded GP amount through the native stake-X input. Session, offer revision,
     * input ownership and resulting escrow are checked so simultaneous changes cannot authorize a stale transfer.
     */
    private suspend fun changeStake(duel: DuelArena.Duel, target: Int): Boolean {
        val before = DuelArena.escrow(bot).sumOf { it.amount.toLong() }.toInt()
        val withdrawing = before > target
        val amount = kotlin.math.abs(target - before)
        if (amount == 0) return true
        val index = if (withdrawing) DuelArena.escrow(bot).indexOfFirst { it.id == 995 }
            else bot.inventory.computeIndexForId(995)
        if (index < 0 || !withdrawing && bot.inventory.computeAmountForId(995) < amount) return false
        val revision = duel.revision
        if (!bot.output.sendItemWidgetClick(5, index, if (withdrawing) 6574 else 6669, 995)) return false
        if (!waitFor(3.seconds) { NumberInput::class in bot.overlays || DuelArena.session(bot) !== duel }) return false
        val input = bot.overlays.getOverlay(NumberInput::class.java) ?: return false
        if (DuelArena.session(bot) !== duel || duel.revision != revision || duel.confirmation ||
            bot.overlays.getOverlay(NumberInput::class.java) !== input || !handler.widgets.enterAmount(amount)) return false
        return waitFor(5.seconds) { DuelArena.session(bot) !== duel ||
            DuelArena.escrow(bot).sumOf { it.amount.toLong() } == target.toLong() } &&
            DuelArena.session(bot) === duel && DuelArena.escrow(bot).sumOf { it.amount.toLong() } == target.toLong()
    }

    override fun paused() {
        if (cleaned) return
        cleaned = true
        attempted = true
        if (observed == null || DuelArena.session(bot) === observed) DuelArena.leave(bot)
        if (discipline == BotGearPurpose.MAGIC) {
            bot.combat.magic.selectedSpell = CombatSpellDefinition.NONE
            bot.combat.magic.autocastSpell = CombatSpellDefinition.NONE
            bot.combat.magic.isAutocasting = false
            bot.combat.magic.refreshAutocast()
        }
        bot.navigator.cancel()
        bot.walking.clear()
    }

    override suspend fun finish() {
        paused()
        if (started && bot.overlays.getOverlay(StandardInterface::class.java)?.id == 6733)
            handler.widgets.clickCloseInterface()
    }
    override fun allowsLoot(item: GroundItem): Boolean = false
    override fun snapshot() = DuelData().also {
        it.attempted = attempted; it.started = started
        it.waitingUntil = waitingUntil; it.deadline = deadline
        it.discipline = discipline; it.stakeBudget = stakeBudget; it.plannedRules = plannedRules
    }
}
