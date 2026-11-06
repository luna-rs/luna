package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.Suspendable.waitFor
import api.bot.action.BotCombatActionHandler
import api.bot.action.BotCombatMagic
import api.bot.action.CombatLoadout
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.webwalk.plan.BankAccess
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotGearPurpose
import engine.controllers.Controllers.inWilderness
import game.content.fightPit.FightPit
import game.content.fightPit.FightPitController
import game.content.fightPit.FightPitDoors
import game.content.fightPit.FightPitRound
import game.player.item.consume.food.Food
import io.luna.game.model.EntityState
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerContextMenuOption
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.varp.PersistentVarp
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Visits one native Fight Pit round with owned melee, Ranged or Magic equipment and supplies.
 * Native doors, the start signal, participant permissions, safe deaths and champion payouts remain authoritative.
 * Absolute deadlines and a saved round identity prevent restored or interrupted visits from starting again.
 *
 * @author lare96
 */
class FightPitBotScript(bot: Bot, private var entered: Boolean = false, private var started: Boolean = false,
                        private var leaving: Boolean = false, private var roundId: Long = -1,
                        private var waitingUntil: Long = System.currentTimeMillis() + 300_000,
                        private val deadline: Long = System.currentTimeMillis() + 1_200_000,
                        private var discipline: BotGearPurpose? = null,
                        private var previousRetaliate: Int? = null) : BotScript(bot) {
    companion object {
        private val DISCIPLINES = listOf(BotGearPurpose.MELEE, BotGearPurpose.RANGED, BotGearPurpose.MAGIC)

        fun eligible(bot: Bot): Boolean = bot.state == EntityState.ACTIVE && ActionSafety.NON_COMBAT.allows(bot) &&
            !bot.inWilderness() && !bot.overlays.hasWindow() &&
            (bot.controllers.primary.javaClass == PlayerController::class.java ||
                (bot.controllers.primary as? FightPitController)?.let { !it.viewing && !it.withdrawing } == true) &&
            DISCIPLINES.any { DuelBotPolicy.loadout(bot, it, FightPit.inside(bot.position)) != null }

        /**
         * Keeps targeting within the current native participant set and excludes viewers and departed players.
         */
        internal fun opponent(bot: Bot, candidates: Collection<Player>): Player? = candidates.filter {
            it !== bot && it.state == EntityState.ACTIVE && it.isAlive && FightPitRound.canFight(bot, it)
        }.minByOrNull { bot.position.computeLongestDistance(it.position) }
    }

    /**
     * Persists the single visit, its deadlines, selected discipline and temporary retaliation setting.
     *
     * @author lare96
     */
    class PitData : BotScriptData() {
        var entered = false
        var started = false
        var leaving = false
        var roundId = -1L
        var waitingUntil = 0L
        var deadline = 0L
        var discipline: BotGearPurpose? = null
        var previousRetaliate: Int? = null
        override fun load(data: JsonObject) {
            entered = data.get("entered").asBoolean
            started = data.get("started").asBoolean
            leaving = data.get("leaving")?.asBoolean ?: false
            roundId = data.get("roundId")?.asLong ?: -1
            waitingUntil = data.get("waitingUntil").asLong
            deadline = data.get("deadline").asLong
            discipline = data.get("discipline")?.takeUnless { it.isJsonNull }?.asString?.let(BotGearPurpose::valueOf)
            previousRetaliate = data.get("previousRetaliate")?.takeUnless { it.isJsonNull }?.asInt
        }
        override fun save(data: JsonObject) {
            data.addProperty("entered", entered)
            data.addProperty("started", started)
            data.addProperty("leaving", leaving)
            data.addProperty("roundId", roundId)
            data.addProperty("waitingUntil", waitingUntil)
            data.addProperty("deadline", deadline)
            data.addProperty("discipline", discipline?.name)
            data.addProperty("previousRetaliate", previousRetaliate)
        }
    }

    constructor(bot: Bot, data: PitData) : this(bot, data.entered, data.started, data.leaving, data.roundId,
        data.waitingUntil, data.deadline, data.discipline, data.previousRetaliate)

    override val actionSafety = ActionSafety.COMBAT
    override val allowsCombatResponse get() = !FightPit.inside(bot.position)
    private val magic = BotCombatMagic(bot, handler)
    private var controller: FightPitController? = null
    private var failures = 0
    private var cleaned = false
    private val traversalOwner = Any()
    private var nextAttackAt = 0L

    override suspend fun init(resumed: Boolean): Boolean {
        if (cleaned || leaving) return true
        val native = bot.controllers.primary as? FightPitController
        if (entered || native != null && FightPit.inside(bot.position)) {
            entered = true
            controller = native
            if (native == null || native.viewing || native.withdrawing || !FightPit.inside(bot.position)) return true
            FightPitDoors.ownTraversals(bot, traversalOwner)
            if (discipline == null) discipline = DISCIPLINES.firstOrNull { DuelBotPolicy.loadout(bot, it, true) != null }
            ownRetaliation()
            return discipline == null
        }
        if (System.currentTimeMillis() >= waitingUntil || System.currentTimeMillis() >= deadline || !eligible(bot)) return true
        return withTimeoutOrNull(180_000) {
            if (!prepare() || !handler.webWalkTo(FightPit.OUTSIDE, radius = 0)) return@withTimeoutOrNull true
            entered = true
            FightPitDoors.ownTraversals(bot, traversalOwner)
            ownRetaliation()
            val door = world.locator.findObjects(FightPit.OUTSIDE, 8) { it.id == FightPit.NORTH_DOOR }.firstOrNull()
                ?: return@withTimeoutOrNull true
            handler.interactions.interact(1, door) { isActionSafe() && System.currentTimeMillis() < deadline }
            val arrived = waitFor(5.seconds) {
                bot.controllers.primary is FightPitController && FightPit.WAITING_ROOM.contains(bot.position)
            }
            controller = bot.controllers.primary as? FightPitController
            if (arrived) waitingUntil = minOf(deadline, System.currentTimeMillis() + 300_000)
            return@withTimeoutOrNull !arrived
        } ?: true
    }

    override suspend fun run(): Boolean {
        if (cleaned || bot.controllers.primary !== controller || !FightPit.inside(bot.position)) return true
        if (leaving || System.currentTimeMillis() >= deadline || !started && System.currentTimeMillis() >= waitingUntil)
            return leave()
        if (!FightPitRound.fighting(bot)) {
            if (started) return leave()
            delay(2.seconds)
            return false
        }
        if (started && roundId != FightPitRound.roundId) return leave()
        started = true
        roundId = FightPitRound.roundId
        if (FightPitRound.phase != FightPitRound.Phase.FIGHT) { delay(1.seconds); return false }
        val purpose = discipline ?: return leave()
        val selected = DuelBotPolicy.loadout(bot, purpose, true) ?: return leave()
        if (bot.emotions.isNervousAboutHp) {
            val food = bot.inventory.withIndex().firstOrNull { it.value?.id in Food.ID_TO_FOOD }
            if (food?.value != null) {
                handler.inventory.eat(food.index, food.value!!.id)
                delay(1.seconds)
                return false
            }
        }
        val target = (bot.combat.target as? Player)?.takeIf {
            it.state == EntityState.ACTIVE && it.isAlive && FightPitRound.canFight(bot, it)
        } ?: opponent(bot, FightPitRound.standing()) ?: run { delay(1.seconds); return false }
        if (!prepareAttack(target, purpose, selected)) return leave()
        if (bot.combat.target !== target || System.currentTimeMillis() >= nextAttackAt) {
            bot.combat.target = null
            val allowed = { bot.controllers.primary === controller && roundId == FightPitRound.roundId &&
                FightPitRound.canFight(bot, target) && isActionSafe() && System.currentTimeMillis() < deadline &&
                DuelBotPolicy.loadout(bot, purpose, true) != null }
            val attacked = withTimeoutOrNull(12_000) {
                handler.interactions.approach(target, allowOutOfView = true, canContinue = allowed) &&
                    handler.interactions.interact(PlayerContextMenuOption.ATTACK.index, target, allowed)
            } == true
            failures = if (attacked) 0 else failures + 1
            if (attacked) nextAttackAt = System.currentTimeMillis() + 12_000
        }
        delay(1.seconds)
        if (failures >= 5) leaving = true
        return false
    }

    /**
     * Uses the existing owned PvP loadout policy and normal banking before entering the waiting room.
     */
    private suspend fun prepare(): Boolean {
        val purpose = discipline ?: DISCIPLINES.filter { DuelBotPolicy.loadout(bot, it) != null }.randomOrNull()
            ?: return false
        discipline = purpose
        val selected = DuelBotPolicy.loadout(bot, purpose) ?: return false
        if (!BotGearLocator(bot, selected.gear).locateAndEquip()) return false
        val supplies = selected.runes.map { (id, amount) -> Item(id, amount * 50) }.toMutableList()
        val food = Food.ID_TO_FOOD.entries.filter { bot.bank.computeAmountForId(it.key) > 0 && it.value.heal > 0 }
            .maxByOrNull { it.value.heal }?.key
        if (food != null) supplies += Item(food, minOf(8, bot.bank.computeAmountForId(food)))
        if (supplies.isNotEmpty() && !handler.banking.travelToBankWithdraw(supplies)) return false
        return DuelBotPolicy.loadout(bot, purpose, true) != null
    }

    /**
     * Keeps the selected combat style and its supplies subject to the account's existing XP and spell rules.
     */
    private suspend fun prepareAttack(target: Player, purpose: BotGearPurpose, selected: CombatLoadout): Boolean {
        if (purpose == BotGearPurpose.MAGIC) {
            val spell = selected.spell ?: return false
            if (spell.button != -1 && (!bot.combat.magic.isAutocasting || bot.combat.magic.autocastSpell != spell) &&
                !handler.combat.setAutocastSpell(spell)) return false
            if (spell.button == -1) {
                bot.combat.magic.isAutocasting = false
                bot.combat.magic.autocastSpell = CombatSpellDefinition.NONE
            }
            return magic.prepare(target, spell)
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
     * Walks out through the native fire wall and waiting-room door, letting the champion payout run normally.
     */
    private suspend fun leave(): Boolean {
        leaving = true
        bot.combat.target = null
        bot.combat.autoRetaliateTarget = null
        val doorId = if (FightPit.PIT.contains(bot.position)) FightPit.SOUTH_DOOR else FightPit.NORTH_DOOR
        val stand = if (doorId == FightPit.SOUTH_DOOR) FightPit.PIT_EXIT else FightPit.NORTH_INSIDE
        val exited = withTimeoutOrNull(20_000) {
            if (!handler.webWalkTo(stand, radius = 0, bank = BankAccess.NONE)) return@withTimeoutOrNull false
            val door = world.locator.findObjects(stand, 8) { it.id == doorId }.firstOrNull()
                ?: return@withTimeoutOrNull false
            handler.interactions.interact(1, door) { isActionSafe() && bot.controllers.primary === controller }
            waitFor(5.seconds) {
                if (doorId == FightPit.SOUTH_DOOR) FightPit.WAITING_ROOM.contains(bot.position)
                else !FightPit.inside(bot.position)
            }
        } == true
        failures = if (exited) 0 else failures + 1
        return !FightPit.inside(bot.position) || failures >= 3
    }

    /**
     * Disables automatic retaliatory pursuit so the owned script can choose a target or leave through a vent.
     */
    private fun ownRetaliation() {
        if (previousRetaliate == null) previousRetaliate = bot.varpManager.getValue(PersistentVarp.AUTO_RETALIATE)
        bot.sendVarp(PersistentVarp.AUTO_RETALIATE, 0)
    }

    override fun paused() {
        if (cleaned) return
        cleaned = true
        leaving = true
        bot.navigator.cancel()
        bot.walking.clear()
        bot.combat.target = null
        bot.combat.autoRetaliateTarget = null
        FightPitDoors.cancelTraversal(bot, traversalOwner)
        val native = controller ?: (bot.controllers.primary as? FightPitController)?.takeIf { entered }
        if (bot.controllers.primary === native) native?.withdraw()
        previousRetaliate?.let { bot.sendVarp(PersistentVarp.AUTO_RETALIATE, it) }
        if (discipline == BotGearPurpose.MAGIC) {
            bot.combat.magic.selectedSpell = CombatSpellDefinition.NONE
            bot.combat.magic.autocastSpell = CombatSpellDefinition.NONE
            bot.combat.magic.isAutocasting = false
        }
    }

    override suspend fun finish() { paused() }
    override fun allowsLoot(item: GroundItem): Boolean = false
    override fun snapshot() = PitData().also {
        it.entered = entered; it.started = started; it.leaving = leaving; it.roundId = roundId
        it.waitingUntil = waitingUntil; it.deadline = deadline; it.discipline = discipline
        it.previousRetaliate = previousRetaliate
    }
}
