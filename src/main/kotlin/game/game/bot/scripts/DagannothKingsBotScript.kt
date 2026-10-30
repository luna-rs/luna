package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.action.BotCombatActionHandler
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.webwalk.plan.BankAccess
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.gear.BotGearLocator
import engine.combat.prayer.CombatPrayer
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.varp.PersistentVarp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Makes one bounded, three-style Dagannoth Kings profit visit with owned equipment and supplies.
 * Boats, doors, supports, ladders, attacks and pickups use the normal game interactions. Route recovery teleports
 * and bank detours are disabled after preparation. A safety watcher eats and drinks during long native travel.
 * Saves retain the absolute visit budget; restoration revalidates carried gear and never grants a fresh supply set.
 *
 * @author lare96
 */
class DagannothKingsBotScript(bot: Bot, private var attempted: Boolean = false,
                             private val deadline: Long = System.currentTimeMillis() + 1_800_000,
                             private var failures: Int = 0, private var retreating: Boolean = false,
                             private var previousRetaliate: Int? = null) : BotScript(bot) {
    companion object {
        val ENTRANCE = Position(2899, 4449)
        private val OUTSIDE_LAIR = Position(1911, 4367)
        private val RETURN_BANK = Position(2726, 3493)
        private val KINGS = setOf(2881, 2882, 2883)

        /**
         * Uses the existing shared lair, without creating a private copy or teleporting into the encounter.
         */
        internal fun inside(position: Position): Boolean = position.z == 0 &&
            position.x in 2894..2933 && position.y in 4430..4468
    }

    /**
     * Persists the one-visit budget and retreat intent, without live NPC references or trusted equipment state.
     *
     * @author lare96
     */
    class DagannothKingsData : BotScriptData() {
        var attempted = false
        var deadline = 0L
        var failures = 0
        var retreating = false
        var previousRetaliate: Int? = null
        override fun load(data: JsonObject) {
            attempted = data.get("attempted").asBoolean
            deadline = data.get("deadline").asLong
            failures = data.get("failures").asInt
            retreating = data.get("retreating").asBoolean
            previousRetaliate = data.get("previousRetaliate")?.takeUnless { it.isJsonNull }?.asInt
        }
        override fun save(data: JsonObject) {
            data.addProperty("attempted", attempted)
            data.addProperty("deadline", deadline)
            data.addProperty("failures", failures)
            data.addProperty("retreating", retreating)
            previousRetaliate?.let { data.addProperty("previousRetaliate", it) }
        }
    }

    constructor(bot: Bot, data: DagannothKingsData) : this(bot, data.attempted, data.deadline,
        data.failures, data.retreating, data.previousRetaliate)

    private var plan: DagannothKingsTrip.Plan? = null
    private var lastProgress = System.currentTimeMillis()
    private var observedTarget: Pair<Npc, Int>? = null
    private var cleanup = false
    private var looting = false
    private var ownsCombat = false
    override val actionSafety = ActionSafety.COMBAT
    override val allowsCombatResponse = false
    override fun allowsLoot(item: GroundItem): Boolean = looting && !retreating && inside(bot.position) &&
        item in bot.viewableItems && item.isVisibleTo(bot)

    override suspend fun init(resumed: Boolean): Boolean {
        cleanup = false
        if (attempted) {
            takeCombat()
            plan = DagannothKingsTrip.plan(bot, carriedOnly = true)
            retreating = retreating || plan == null || !inside(bot.position) ||
                !DagannothKingsTrip.armourMatches(bot, plan!!)
            return false
        }
        if (System.currentTimeMillis() >= deadline || !DagannothKingsTrip.ready(bot)) return true
        attempted = true
        val selected = DagannothKingsTrip.plan(bot) ?: return true
        if (DagannothKingsTrip.preparationSlots(selected) > 28) return true
        plan = selected
        val prepared = withTimeoutOrNull(180_000) {
            if (!BotGearLocator(bot, selected.baseGear).locateAndEquip()) return@withTimeoutOrNull false
            val requested = selected.carriedRequirements()
            if (!handler.banking.withdrawAll(requested.map { (id, amount) -> Item(id, amount) }) ||
                !handler.widgets.clickCloseInterface()) return@withTimeoutOrNull false
            requested.all { (id, amount) -> bot.inventory.computeAmountForId(id) >= amount }
        } == true
        if (!prepared) return true
        takeCombat()
        // Waterbirth's central supports require a real ranged attack before reaching the kings.
        if (!handler.equipment.equip(selected.ranged.gear[Equipment.WEAPON]!!)) return true
        clearMagic()
        val travelStyle = BotCombatActionHandler.allowedStyles(bot, bot.combat.weapon.typeDef.styles).firstOrNull()
            ?: return true
        bot.combat.weapon.changeStyle(travelStyle.button)
        bot.combat.prayers.activateIfOff(CombatPrayer.PROTECT_FROM_MAGIC)
        val arrived = guardedTravel(ENTRANCE, 600_000) { DagannothKingsTrip.canFight(bot, selected) }
        retreating = !arrived || !inside(bot.position)
        return false
    }

    /**
     * Runs health, poison and prayer decisions while a bounded native journey is suspended.
     * An outbound reserve check cancels the route before another leg when supplies cease to qualify.
     * The return walk omits that check so it can spend the reserved food and doses on escaping.
     */
    internal suspend fun guardedTravel(destination: Position, timeout: Long,
                                       reserve: (() -> Boolean)? = null): Boolean = withTimeoutOrNull(timeout) {
        coroutineScope {
            fun reserveLost(): Boolean = (reserve?.invoke() == false).also { if (it) retreating = true }
            if (reserveLost()) return@coroutineScope false
            maintainSupplies()
            if (reserveLost()) return@coroutineScope false
            val journey = async(start = CoroutineStart.UNDISPATCHED) {
                handler.webWalkTo(destination, radius = 1, bank = BankAccess.NONE, fallback = false)
            }
            try {
                while (!journey.isCompleted && isActive && bot.state == EntityState.ACTIVE) {
                    maintainSupplies()
                    if (reserveLost()) return@coroutineScope false
                    delay(1.seconds)
                }
                if (reserveLost() || bot.state != EntityState.ACTIVE || !bot.isAlive) false else journey.await()
            } finally { journey.cancelAndJoin() }
        }
    } == true

    /**
     * Keeps a lethal-hit buffer and uses only doses physically carried by this visit.
     */
    private suspend fun maintainSupplies() {
        if (!isActionSafe()) return
        if (bot.health <= maxOf(60, bot.totalHealth - 25)) {
            val slot = bot.inventory.computeIndexForId(DagannothKingsTrip.FOOD)
            if (slot >= 0) handler.inventory.eat(slot, DagannothKingsTrip.FOOD)
        }
        handler.potions.drink(fighting = false, dragons = false, prayerBelow = 40)
    }

    /**
     * Prime's lethal magic takes precedence over Supreme's ranged attack and Rex's melee when several attack us.
     */
    private fun protect(kings: List<Npc>) {
        val threats = kings.filter { it.combat.target === bot }
        val prayer = when {
            threats.any { it.id == 2882 } -> CombatPrayer.PROTECT_FROM_MAGIC
            threats.any { it.id == 2881 } -> CombatPrayer.PROTECT_FROM_MISSILES
            threats.any { it.id == 2883 } -> CombatPrayer.PROTECT_FROM_MELEE
            else -> CombatPrayer.PROTECT_FROM_MAGIC
        }
        bot.combat.prayers.activateIfOff(prayer)
    }

    /**
     * Switches weapons through inventory clicks and configures only native, account-compatible combat styles.
     */
    internal suspend fun configure(npc: Npc, plan: DagannothKingsTrip.Plan): Boolean {
        val loadout = when (npc.id) { 2881 -> plan.melee; 2882 -> plan.ranged; else -> plan.magic }
        val weapon = loadout.gear[Equipment.WEAPON] ?: return false
        if (bot.equipment.weapon?.id != weapon) {
            bot.combat.target = null
            if (!handler.equipment.equip(weapon)) return false
        }
        if (npc.id == 2883) {
            val spell = loadout.spell ?: return false
            return if (bot.combat.magic.isAutocasting && bot.combat.magic.autocastSpell == spell) true
                else handler.combat.setAutocastSpell(spell)
        }
        clearMagic()
        val allowed = BotCombatActionHandler.allowedStyles(bot, bot.combat.weapon.typeDef.styles)
        val style = allowed.firstOrNull() ?: return false
        if (bot.combat.weapon.styleDef != style) bot.combat.weapon.changeStyle(style.button)
        return true
    }

    override suspend fun run(): Boolean {
        if (!bot.isAlive || bot.state != EntityState.ACTIVE) return true
        val selected = plan
        if (retreating || selected == null || !inside(bot.position) || failures >= 4 ||
            System.currentTimeMillis() >= deadline - 120_000 ||
            System.currentTimeMillis() - lastProgress >= 150_000 || !DagannothKingsTrip.canFight(bot, selected)) {
            retreating = true
            bot.combat.target = null
            clearMagic()
            // The return reserve is spent on leaving by the ladder, never on another kill.
            if (inside(bot.position) && !guardedTravel(OUTSIDE_LAIR, 60_000)) return true
            if (guardedTravel(RETURN_BANK, 480_000) && isActionSafe()) handler.banking.travelToBankDepositAll()
            return true
        }
        val kings = world.locator.findNpcs(bot, 20) {
            it.id in KINGS && it.state == EntityState.ACTIVE && it.isAlive && inside(it.position)
        }.toList()
        protect(kings)
        maintainSupplies()
        if (!isActionSafe() || !DagannothKingsTrip.canFight(bot, selected)) return false
        val target = (bot.combat.target as? Npc)?.takeIf { it in kings } ?: kings.minWithOrNull(
            compareBy<Npc> { if (it.combat.target === bot) 0 else 1 }
                .thenBy { when (it.id) { 2881 -> 0; 2882 -> 1; else -> 2 } }
                .thenBy { it.position.computeLongestDistance(bot.position) })
        if (kings.none { it.combat.target === bot }) {
            val loot = handler.loot.ordered(bot.viewableItems.filter {
                it.position.isWithinDistance(bot.position, 6) && it.isVisibleTo(bot) &&
                    it.state == EntityState.ACTIVE &&
                    (it.id in setOf(6731, 6733, 6735, 6737, 6739, 6729, 6155) || handler.loot.value(it) >= 10_000)
            }).firstOrNull { bot.inventory.hasSpaceFor(it.toItem()) }
            if (loot != null) {
                looting = true
                try {
                    if (handler.loot.pickup(loot) { inside(bot.position) && !retreating && isActionSafe() &&
                            bot.health > 60 && kings.none { it.combat.target === bot }
                        })
                        lastProgress = System.currentTimeMillis()
                } finally { looting = false }
            }
        }
        if (target == null) { delay(2.seconds); return false }
        val state = target to target.health
        if (state != observedTarget) { observedTarget = state; lastProgress = System.currentTimeMillis() }
        // A restored or externally selected target must never authorize the currently held, unrelated weapon.
        if (!configure(target, selected)) { failures++; delay(1.seconds); return false }
        if (bot.combat.target !== target) {
            val success = handler.interactions.interact(3, target) {
                isActionSafe() && inside(bot.position) && target.isAlive && !retreating &&
                    DagannothKingsTrip.canFight(bot, selected)
            }
            failures = if (success) 0 else failures + 1
        }
        delay(1.seconds)
        return false
    }

    private fun clearMagic() {
        bot.combat.magic.selectedSpell = CombatSpellDefinition.NONE
        bot.combat.magic.autocastSpell = CombatSpellDefinition.NONE
        bot.combat.magic.isAutocasting = false
    }

    private fun takeCombat() {
        if (previousRetaliate == null) previousRetaliate = bot.varpManager.getValue(PersistentVarp.AUTO_RETALIATE)
        ownsCombat = true
        bot.sendVarp(PersistentVarp.AUTO_RETALIATE, 1)
    }

    override fun paused() {
        if (!ownsCombat) return
        retreating = true
        bot.combat.target = null
        clearMagic()
    }

    override suspend fun finish() {
        if (cleanup || !ownsCombat) return
        cleanup = true
        bot.combat.target = null
        clearMagic()
        bot.combat.prayers.deactivateAll()
        previousRetaliate?.let { bot.sendVarp(PersistentVarp.AUTO_RETALIATE, it) }
    }

    override fun snapshot() = DagannothKingsData().also {
        it.attempted = attempted
        it.deadline = deadline
        it.failures = failures
        it.retreating = retreating
        it.previousRetaliate = previousRetaliate
    }
}
