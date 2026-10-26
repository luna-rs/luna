package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.action.BotCombatActionHandler
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.predef.*
import com.google.gson.JsonObject
import engine.controllers.Controllers.inWilderness
import game.content.duelArena.DuelArena
import game.content.duelArena.DuelRules
import game.player.item.consume.food.Food
import io.luna.game.model.EntityState
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerContextMenuOption
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.overlay.StandardInterface
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Visits the arena for one no-stakes melee duel through native challenge, acceptance and attack actions.
 * Both acceptance screens are checked again after offer changes; interruptions abandon the match.
 *
 * @author lare96
 */
class DuelArenaBotScript(bot: Bot, private var attempted: Boolean = false,
                         private var started: Boolean = false,
                         private val waitingUntil: Long = System.currentTimeMillis() + 300_000,
                         private val deadline: Long = System.currentTimeMillis() + 1_200_000) : BotScript(bot) {
    companion object {
        internal fun meleeReady(bot: Bot): Boolean = !bot.preferences.isSkiller &&
            !bot.preferences.scavenging.isIntroductory && bot.equipment.occupied(Equipment.WEAPON) &&
            !bot.combat.weapon.isRanged && !bot.combat.magic.isAutocasting &&
            bot.combat.magic.selectedSpell == CombatSpellDefinition.NONE &&
            BotCombatActionHandler.allowedStyles(bot, listOf(bot.combat.styleDef)).isNotEmpty()

        fun eligible(bot: Bot): Boolean = ActionSafety.NON_COMBAT.allows(bot) &&
            bot.state == EntityState.ACTIVE && !bot.inWilderness() && meleeReady(bot) &&
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

        internal fun acceptable(bot: Bot, duel: DuelArena.Duel): Boolean = duel.contains(bot) &&
            meleeReady(bot) && !DuelRules.NO_MELEE.enabled(duel.rules) &&
            !DuelRules.FLOWERS.enabled(duel.rules) && duel.players.all { DuelArena.escrow(it).isEmpty() }
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
        override fun load(data: JsonObject) {
            attempted = data.get("attempted").asBoolean
            started = data.get("started").asBoolean
            waitingUntil = data.get("waitingUntil").asLong
            deadline = data.get("deadline").asLong
        }
        override fun save(data: JsonObject) {
            data.addProperty("attempted", attempted)
            data.addProperty("started", started)
            data.addProperty("waitingUntil", waitingUntil)
            data.addProperty("deadline", deadline)
        }
    }

    constructor(bot: Bot, data: DuelData) : this(bot, data.attempted, data.started, data.waitingUntil, data.deadline)

    override val actionSafety = ActionSafety.COMBAT
    override val allowsCombatResponse: Boolean get() = DuelArena.session(bot) == null
    private var observed: DuelArena.Duel? = null
    private var acceptedRevision = -1
    private var acceptedConfirmation = false
    private var failures = 0
    private var cleaned = false

    override suspend fun init(resumed: Boolean): Boolean {
        if (cleaned || System.currentTimeMillis() >= deadline) return true
        val duel = DuelArena.session(bot)
        if (attempted || started) {
            observed = duel
            return duel == null
        }
        if (System.currentTimeMillis() >= waitingUntil || !eligible(bot)) return true
        return withTimeoutOrNull(180_000) {
            !DuelArena.inside(bot.position) && !handler.webWalkTo(DuelArena.LOBBY, radius = 3)
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
                if (!meleeReady(bot)) return true
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
                if (bot.combat.target !== other) {
                    val attacked = withTimeoutOrNull(10_000) {
                        handler.interactions.interact(PlayerContextMenuOption.ATTACK.index, other) {
                            DuelArena.session(bot) === duel && duel.fighting(bot, other) &&
                                isActionSafe() && meleeReady(bot) && System.currentTimeMillis() < deadline
                        }
                    } == true
                    failures = if (attacked) 0 else failures + 1
                }
            } else {
                if (System.currentTimeMillis() >= waitingUntil || !isActionSafe()) return true
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

    override fun paused() {
        if (cleaned) return
        cleaned = true
        attempted = true
        if (observed == null || DuelArena.session(bot) === observed) DuelArena.leave(bot)
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
    }
}
