package game.bot.scripts

import api.bot.Suspendable.delay
import api.bot.Suspendable.waitFor
import api.bot.action.BotCombatActionHandler
import api.bot.action.CombatLoadout
import api.bot.action.PvmTactics
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.webwalk.plan.BankAccess
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import engine.obj.StairDestination
import game.content.barrows.*
import game.player.item.consume.food.Food
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.game.model.mob.varp.PersistentVarp
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Completes one native Barrows run using owned combat equipment, food, prayer doses and a spade.
 * Strong single-style accounts may attend; compatible weapon and torso switches counter Ahrim when available.
 * Native varps own brother defeats, maze locks and chest rewards. Digging, coffins, doors, puzzle answers,
 * attacks and rewards use ordinary player interactions. Internal walks disable recovery teleports and bank detours.
 * Saved visits retain their absolute budget and return intent, then re-read native progress and physical supplies.
 *
 * @property deadline Absolute end of this visit, retained when its script is saved.
 * @property failures Consecutive failed interactions before returning to the bank.
 * @property returning Whether only retreat and banking remain for this visit.
 * @property runStarted Distinguishes this visit's rewarded run from an earlier collapsing run.
 * @property previousRetaliate Setting to restore after this script relinquishes combat control.
 * @author lare96
 */
class BarrowsBotScript(bot: Bot, private var attempted: Boolean = false,
                       private val deadline: Long = System.currentTimeMillis() + 2_400_000,
                       private var failures: Int = 0, private var returning: Boolean = false,
                       private var runStarted: Boolean = false, private var previousRetaliate: Int? = null) : BotScript(bot) {
    companion object {
        const val SPADE = 952
        private const val CHEST = 10284
        private const val FOOD_COUNT = 16
        private const val MAX_FAILURES = 5
        private val PRAYER_DOSES = linkedMapOf(2434 to 4, 139 to 3, 141 to 2, 143 to 1)

        /**
         * Native mound landings from KnownStairs, inside the corresponding SpadeDigSpot footprints.
         */
        internal val mounds = linkedMapOf(
            BarrowsBrother.DHAROK to Position(3575, 3298), BarrowsBrother.AHRIM to Position(3566, 3289),
            BarrowsBrother.KARIL to Position(3566, 3275), BarrowsBrother.GUTHAN to Position(3577, 3283),
            BarrowsBrother.TORAG to Position(3553, 3283), BarrowsBrother.VERAC to Position(3557, 3298))

        /**
         * Available styles remain account-compatible; a single supplied discipline is a legitimate fallback.
         */
        internal fun preferredPurpose(brother: BarrowsBrother?, available: Set<BotGearPurpose>): BotGearPurpose? {
            val order = if (brother == BarrowsBrother.AHRIM)
                listOf(BotGearPurpose.RANGED, BotGearPurpose.MELEE, BotGearPurpose.MAGIC)
            else listOf(BotGearPurpose.MAGIC, BotGearPurpose.MELEE, BotGearPurpose.RANGED)
            return order.firstOrNull { it in available }
        }

        /**
         * One worn outfit and compatible weapon switches. Counts are admission reserves, never item grants.
         *
         * @property base Shared armour and the initially equipped weapon and ammunition.
         * @property styles Attacks supported by the shared armour and each compact torso switch.
         * @property requirements Inventory supplies to withdraw after equipping the shared outfit.
         * @author lare96
         */
        internal data class Plan(val base: Array<Int?>, val styles: Map<BotGearPurpose, CombatLoadout>,
                                 val requirements: Map<Int, Int>)

        /**
         * Requires substantial permanent and current stats before considering the equipment itself.
         */
        internal fun levelsReady(bot: Bot): Boolean = !bot.preferences.isSkiller && bot.combatLevel >= 95 &&
            bot.hitpoints.staticLevel >= 75 && bot.defence.level >= 70 && bot.prayer.staticLevel >= 50 &&
            BotCombatActionHandler.mayGainExperience(bot, Skill.HITPOINTS)

        /**
         * Counts physical unnoted stock, including smaller remaining prayer doses after an earlier visit.
         */
        private fun count(bot: Bot, id: Int, carriedOnly: Boolean): Int = if (carriedOnly)
            CombatLoadout.carried(bot, id) else bot.itemTracker.count(id)

        internal fun prayerDoses(bot: Bot, carriedOnly: Boolean): Int = PRAYER_DOSES.entries.sumOf { (id, doses) ->
            count(bot, id, carriedOnly).coerceAtMost(1000) * doses
        }

        internal fun food(bot: Bot, carriedOnly: Boolean): Int = Food.ID_TO_FOOD.entries
            .filter { it.value.heal >= 16 }.sumOf { count(bot, it.key, carriedOnly).coerceAtMost(1000) }

        /**
         * Carried armour is not worn protection. Recheck the complete shared outfit after pauses and equipment loss.
         */
        internal fun armourMatches(bot: Bot, plan: Plan): Boolean = plan.base.withIndex()
            .filter { it.index !in setOf(Equipment.WEAPON, Equipment.AMMUNITION) }
            .all { (slot, id) -> if (slot == Equipment.CHEST)
                bot.equipment[slot]?.id in plan.styles.values.map { it.gear[slot] }
                else bot.equipment[slot]?.id == id }

        /**
         * Generic gear tags include tribrid dragonhide. Prefer actual owned magic bonuses for the robe outfit,
         * leaving detrimental spare slots empty rather than trusting a higher armour tier's purpose tag.
         */
        private fun magicArmour(bot: Bot, selected: CombatLoadout, carriedOnly: Boolean): CombatLoadout {
            val owned = (bot.inventory.filterNotNull() + bot.equipment.filterNotNull() +
                if (carriedOnly) emptyList() else bot.bank.filterNotNull()).map { it.id }.distinct()
            val gear = selected.gear.copyOf()
            for (slot in gear.indices.filter { it !in setOf(Equipment.WEAPON, Equipment.AMMUNITION) }) {
                val positive = owned.mapNotNull { EquipmentDefinition.ALL[it].orElse(null) }
                    .filter { it.index == slot && it.meetsAllRequirements(bot) && it.getBonus(EquipmentBonus.MAGIC_ATTACK.index) > 0 }
                    .maxByOrNull { it.getBonus(EquipmentBonus.MAGIC_ATTACK.index) }
                if (positive != null) gear[slot] = positive.id()
                else if (gear[slot]?.let { equipDef(it).getBonus(EquipmentBonus.MAGIC_ATTACK.index) < 0 } == true) gear[slot] = null
            }
            return selected.copy(gear = gear)
        }

        /**
         * Selects only stock already owned. Mixed outfits must still support the actual switched attack roll.
         * Partial doses are reused, within four potion slots, rather than requiring fresh four-dose bottles forever.
         */
        internal fun plan(bot: Bot, carriedOnly: Boolean = false): Plan? {
            if (!levelsReady(bot)) return null
            val choices = linkedMapOf<BotGearPurpose, CombatLoadout>()
            for (purpose in listOf(BotGearPurpose.MAGIC, BotGearPurpose.RANGED, BotGearPurpose.MELEE)) {
                val level = when (purpose) {
                    BotGearPurpose.MAGIC -> bot.magic.level >= 80
                    BotGearPurpose.RANGED -> bot.ranged.level >= 85
                    else -> bot.attack.level >= 85 && bot.strength.level >= 85
                }
                if (!level) continue
                var selected = CombatLoadout.find(bot, purpose, carriedOnly = carriedOnly) ?: continue
                if (purpose == BotGearPurpose.MAGIC) selected = magicArmour(bot, selected, carriedOnly)
                else if (choices.isNotEmpty()) {
                    // Compact switches reuse the primary legs instead of carrying another complete outfit.
                    val shared = choices.values.first().gear
                    selected = selected.copy(gear = selected.gear.copyOf().also { gear ->
                        for (slot in listOf(Equipment.CHEST, Equipment.LEGS))
                            if (gear[slot] == null) gear[slot] = shared[slot]
                    })
                }
                if (selected.gear[Equipment.CHEST] == null || selected.gear[Equipment.LEGS] == null) continue
                val weapon = selected.gear[Equipment.WEAPON] ?: continue
                if (purpose == BotGearPurpose.MAGIC && (selected.spell?.maxHit ?: 0) < 15) continue
                if (purpose == BotGearPurpose.MELEE && !KalphiteQueenTrip.gearReady(selected.gear)) continue
                val attacks = if (carriedOnly) 20 else 500
                if (selected.runes.any { (id, amount) -> count(bot, id, carriedOnly) < amount * attacks }) continue
                if (purpose == BotGearPurpose.RANGED && count(bot, selected.gear[Equipment.AMMUNITION] ?: weapon,
                        carriedOnly) < if (carriedOnly) 50 else 1000) continue
                choices[purpose] = selected
            }
            val base = choices.values.firstOrNull()?.gear?.copyOf() ?: return null
            if (base[Equipment.CHEST] == null || base[Equipment.LEGS] == null) return null
            // A two-handed switch must never displace a shield that the shared outfit depends on.
            if (choices.values.any { equipDef(it.gear[Equipment.WEAPON]!!).isTwoHanded }) base[Equipment.SHIELD] = null
            choices[BotGearPurpose.RANGED]?.gear?.get(Equipment.AMMUNITION)?.let { base[Equipment.AMMUNITION] = it }
            choices.entries.removeIf { (purpose, outfit) ->
                val mixed = base.copyOf().also {
                    it[Equipment.WEAPON] = outfit.gear[Equipment.WEAPON]
                    it[Equipment.CHEST] = outfit.gear[Equipment.CHEST]
                }
                fun bonus(index: Int) = mixed.filterNotNull().sumOf { equipDef(it).getBonus(index) }
                when (purpose) {
                    BotGearPurpose.MAGIC -> bonus(EquipmentBonus.MAGIC_ATTACK.index) < 0
                    BotGearPurpose.RANGED -> bonus(EquipmentBonus.RANGED_ATTACK.index) < 20
                    else -> !KalphiteQueenTrip.gearReady(mixed)
                }
            }
            if (choices.isEmpty() || count(bot, SPADE, carriedOnly) == 0) return null
            base[Equipment.WEAPON] = choices.values.first().gear[Equipment.WEAPON]
            val required = linkedMapOf(SPADE to 1)
            choices.values.forEach { outfit ->
                val weapon = outfit.gear[Equipment.WEAPON]!!
                if (weapon != base[Equipment.WEAPON]) required[weapon] = 1
                outfit.gear[Equipment.CHEST]?.takeIf { it != base[Equipment.CHEST] }?.let { required[it] = 1 }
                outfit.runes.forEach { (id, amount) -> required[id] = maxOf(required[id] ?: 0, amount * 500) }
            }
            choices[BotGearPurpose.RANGED]?.let { ranged ->
                required[ranged.gear[Equipment.AMMUNITION] ?: ranged.gear[Equipment.WEAPON]!!] = 1000
            }
            if (carriedOnly) {
                if (food(bot, true) < 4 || prayerDoses(bot, true) < 1) return null
            } else {
                var slots = 4
                var doses = 0
                for ((id, size) in PRAYER_DOSES) {
                    val amount = minOf(slots, count(bot, id, false))
                    if (amount > 0) { required[id] = amount; slots -= amount; doses += amount * size }
                }
                if (doses < 12 || food(bot, false) < FOOD_COUNT) return null
                if (required.entries.sumOf { (id, amount) -> if (itemDef(id).isStackable) 1 else amount } + FOOD_COUNT > 28) return null
            }
            return Plan(base, choices, required)
        }

        /**
         * Both minigame and profit selection use the same owned-kit admission check.
         */
        fun eligible(bot: Bot): Boolean = ActionSafety.NON_COMBAT.allows(bot) && plan(bot) != null
    }

    /**
     * Run progress remains in the native saved varps; no NPC, interface or trusted equipment reference is persisted.
     *
     * @author lare96
     */
    class BarrowsData : BotScriptData() {
        var attempted = false
        var deadline = 0L
        var failures = 0
        var returning = false
        var runStarted = false
        var previousRetaliate: Int? = null
        override fun load(data: JsonObject) {
            attempted = data.get("attempted").asBoolean
            deadline = data.get("deadline").asLong
            failures = data.get("failures").asInt
            returning = data.get("returning").asBoolean
            runStarted = data.get("runStarted").asBoolean
            previousRetaliate = data.get("previousRetaliate")?.takeUnless { it.isJsonNull }?.asInt
        }
        override fun save(data: JsonObject) {
            data.addProperty("attempted", attempted); data.addProperty("deadline", deadline)
            data.addProperty("failures", failures); data.addProperty("returning", returning)
            data.addProperty("runStarted", runStarted)
            previousRetaliate?.let { data.addProperty("previousRetaliate", it) }
        }
    }

    constructor(bot: Bot, data: BarrowsData) : this(bot, data.attempted, data.deadline, data.failures,
        data.returning, data.runStarted, data.previousRetaliate)

    private var selected: Plan? = null
    private val tactics = PvmTactics(bot)
    private var mazeState = -1
    private val crossings = HashMap<BarrowsDoor, Int>()
    private var lastDoor: BarrowsDoor? = null
    private var lastProgress = System.currentTimeMillis()
    private var observed: Pair<Npc, Int>? = null
    override val actionSafety = ActionSafety.COMBAT
    override val allowsCombatResponse = false
    override fun allowsLoot(item: GroundItem): Boolean = false

    override suspend fun init(resumed: Boolean): Boolean {
        if (System.currentTimeMillis() >= deadline || !levelsReady(bot)) {
            returning = BarrowsAreaListener.inside(bot.position)
            return !returning
        }
        if (previousRetaliate == null) previousRetaliate = bot.varpManager.getValue(PersistentVarp.AUTO_RETALIATE)
        bot.sendVarp(PersistentVarp.AUTO_RETALIATE, 1)
        if (BarrowsAreaListener.inside(bot.position)) {
            runStarted = true
            selected = plan(bot, carriedOnly = true)
            if (selected?.let { armourMatches(bot, it) } == true) return false
        }
        if (returning) return false
        attempted = true
        return !prepare()
    }

    /**
     * Banking may happen between brothers; native defeat and maze flags survive that ordinary departure.
     */
    private suspend fun prepare(): Boolean {
        if (BarrowsAreaListener.inside(bot.position) && !leaveUnderground()) return false
        if (withTimeoutOrNull(30_000) {
                if (bot.combat.inCombat()) handler.combat.fleeCombat()
                awaitActionSafety(ActionSafety.NON_COMBAT)
            } != true) return false
        val planned = plan(bot) ?: return false
        val success = withTimeoutOrNull(180_000) {
            if (!BotGearLocator(bot, planned.base).locateAndEquip() || !handler.banking.travelToBankDepositAll())
                return@withTimeoutOrNull false
            val missing = planned.requirements.mapNotNull { (id, amount) ->
                (amount - CombatLoadout.carried(bot, id)).takeIf { it > 0 }?.let { Item(id, it) }
            }
            if (!handler.banking.withdrawAll(missing) ||
                !handler.banking.withdrawAnyFood(amount = FOOD_COUNT, minimumHeal = 16, retry = false) ||
                !handler.widgets.clickCloseInterface()) return@withTimeoutOrNull false
            planned.requirements.all { (id, amount) -> CombatLoadout.carried(bot, id) >= amount } &&
                food(bot, true) >= FOOD_COUNT
        } == true
        if (!success) return false
        selected = plan(bot, carriedOnly = true) ?: return false
        if (!armourMatches(bot, selected!!)) return false
        lastProgress = System.currentTimeMillis()
        return true
    }

    /**
     * Looks after prayer and food while a web-walker operation suspends. Only surface arrival permits the ordinary
     * travel fallback; crypt and maze interactions must use the native doors and stairs.
     */
    private suspend fun walk(destination: Position, radius: Int = 1, timeout: Long = 30_000,
                             fallback: Boolean = false): Boolean = attend(timeout) {
        handler.webWalkTo(destination, radius = radius, bank = BankAccess.NONE, fallback = fallback)
    }

    /**
     * Maintains supplies during suspended walks and interaction approaches, cancelling when the trip must retreat.
     */
    private suspend fun attend(timeout: Long, action: suspend () -> Boolean): Boolean = withTimeoutOrNull(timeout) {
        coroutineScope {
            val journey = async(start = CoroutineStart.UNDISPATCHED) { action() }
            try {
                while (!journey.isCompleted && isActive && bot.state == EntityState.ACTIVE) {
                    maintain()
                    if (!bot.isAlive || returning || food(bot, true) < 4) return@coroutineScope false
                    delay(1.seconds)
                }
                journey.await()
            } finally { journey.cancelAndJoin() }
        }
    } == true

    private suspend fun maintain() {
        if (!isActionSafe()) return
        tactics.check()
        if (bot.health <= maxOf(45, bot.totalHealth - 25)) handler.inventory.eatAnyFood(minimumHeal = 16)
        handler.potions.drink(fighting = bot.combat.inCombat(), dragons = false, prayerBelow = 25)
    }

    private fun clearMagic() {
        bot.combat.magic.selectedSpell = CombatSpellDefinition.NONE
        bot.combat.magic.autocastSpell = CombatSpellDefinition.NONE
        bot.combat.magic.isAutocasting = false
    }

    /**
     * Uses the supplied compact switch only when its physical weapon and ammunition or runes are still carried.
     */
    internal suspend fun configure(npc: Npc, plan: Plan): Boolean {
        val brother = (npc as? BarrowsBrotherNpc)?.brother
        val purpose = preferredPurpose(brother, plan.styles.keys) ?: return false
        var outfit = plan.styles.getValue(purpose)
        if (purpose == BotGearPurpose.MAGIC) {
            val live = CombatLoadout.find(bot, purpose, carriedOnly = true) ?: return false
            if (live.gear[Equipment.WEAPON] != outfit.gear[Equipment.WEAPON]) return false
            outfit = outfit.copy(spell = live.spell)
        }
        val weapon = outfit.gear[Equipment.WEAPON] ?: return false
        if (CombatLoadout.carried(bot, weapon) == 0) return false
        if (bot.equipment.weapon?.id != weapon && !handler.equipment.equip(weapon)) return false
        val torso = outfit.gear[Equipment.CHEST] ?: return false
        if (bot.equipment[Equipment.CHEST]?.id != torso && !handler.equipment.equip(torso)) return false
        if (purpose == BotGearPurpose.MAGIC) {
            if (outfit.runes.any { (id, amount) -> bot.inventory.computeAmountForId(id) < amount }) return false
            val spell = outfit.spell ?: return false
            return if (bot.combat.magic.isAutocasting && bot.combat.magic.autocastSpell == spell) true
                else handler.combat.setAutocastSpell(spell)
        }
        clearMagic()
        if (purpose == BotGearPurpose.RANGED) {
            val ammo = outfit.gear[Equipment.AMMUNITION]
            if (ammo != null && bot.equipment.computeAmountForId(ammo) == 0 && !handler.equipment.equip(ammo)) return false
        }
        val style = BotCombatActionHandler.allowedStyles(bot, bot.combat.weapon.typeDef.styles).firstOrNull() ?: return false
        if (bot.combat.weapon.styleDef != style) bot.combat.weapon.changeStyle(style.button)
        return true
    }

    override suspend fun run(): Boolean {
        if (!bot.isAlive || bot.state != EntityState.ACTIVE) return true
        maintain()
        val underground = BarrowsAreaListener.inside(bot.position)
        if (runStarted && BarrowsRun.state(bot) and BarrowsRun.COLLAPSING_MASK != 0) returning = true
        if (failures >= MAX_FAILURES || System.currentTimeMillis() >= deadline - 30_000 ||
            System.currentTimeMillis() - lastProgress > 240_000) returning = true
        if (returning) {
            if (underground && !leaveUnderground()) return ++failures >= MAX_FAILURES
            withTimeoutOrNull(30_000) {
                if (bot.combat.inCombat()) handler.combat.fleeCombat()
                if (awaitActionSafety(ActionSafety.NON_COMBAT)) handler.banking.travelToBankDepositAll()
            }
            return true
        }
        val enemies = world.locator.findNpcs(bot, Position.VIEWING_DISTANCE) {
            it.state == EntityState.ACTIVE && ((it is BarrowsBrotherNpc && it.owner === bot) ||
                (it is BarrowsTunnelNpc && it.combat.target === bot))
        }
        if (enemies.any { it is BarrowsBrotherNpc && !it.isAlive }) { delay(1.seconds); return false }
        val enemy = enemies.filter { it.isAlive }.minByOrNull { if (it is BarrowsBrotherNpc) 0 else 1 }
        if (enemy != null) {
            val current = selected
            if (current == null || !armourMatches(bot, current) || food(bot, true) < 4 ||
                (prayerDoses(bot, true) == 0 && bot.prayer.level < 10) || !configure(enemy, current)) {
                if (!prepare()) returning = true
                return false
            }
            val status = enemy to enemy.health
            if (status != observed) { observed = status; lastProgress = System.currentTimeMillis() }
            if (bot.combat.target !== enemy) {
                if (!handler.interactions.interact(3, enemy) { isActionSafe() && enemy.isAlive && !returning }) failures++
            }
            delay(1.seconds)
            return false
        }
        bot.combat.target = null
        if (food(bot, true) < 4 || prayerDoses(bot, true) == 0 || selected == null) {
            if (!prepare()) returning = true
            return false
        }
        val progressed = when {
            underground && bot.position.z == 0 -> tunnels()
            underground && bot.position.z == 3 -> crypt()
            !underground -> dig()
            else -> false
        }
        if (progressed) { failures = 0; lastProgress = System.currentTimeMillis() } else failures++
        delay(1.seconds)
        return false
    }

    /**
     * Visits the five non-tunnel crypts first, then the native hidden-tunnel brother.
     */
    internal fun nextBrother(): BarrowsBrother {
        val state = BarrowsRun.state(bot)
        if (state == 0 || state and BarrowsRun.COLLAPSING_MASK != 0) return BarrowsBrother.DHAROK
        val tunnel = BarrowsRun.tunnelBrother(bot)
        return mounds.keys.firstOrNull { it != tunnel && !BarrowsProgress.isKilled(bot, it) }
            ?: tunnel ?: BarrowsBrother.DHAROK
    }

    private suspend fun dig(): Boolean {
        val destination = mounds.getValue(nextBrother())
        if (!walk(destination, radius = 0, timeout = 180_000, fallback = true)) return false
        if (!isActionSafe(ActionSafety.NON_COMBAT) || !handler.inventory.clickItem(1, SPADE)) return false
        val entered = waitFor(5.seconds) { BarrowsAreaListener.inside(bot.position) && bot.position.z == 3 }
        if (entered) runStarted = true
        return entered
    }

    private suspend fun crypt(): Boolean {
        val brother = BarrowsBrother.entries.minBy { it.digSpot.landing.computeLongestDistance(bot.position) }
        if (brother != nextBrother()) {
            val stairs = world.locator.findObjects(bot, 16) {
                it.def().name in StairDestination.NAMES && it.def().actions.any { action -> action.equals("Climb-up", true) }
            }.sortedBy { it.position.computeLongestDistance(bot.position) }
            return stairs.firstOrNull()?.let { stair ->
                handler.interactions.interact(1, stair) && waitFor(5.seconds) { !BarrowsAreaListener.inside(bot.position) }
            } == true
        }
        if (handler.widgets.clickDialogueOption("Yeah I'm fearless!"))
            return waitFor(5.seconds) { bot.position.z == 0 }
        if (bot.dialogues != null) {
            val before = bot.overlays.getOverlay(DialogueInterface::class.java)
            output.sendContinueDialogue()
            return waitFor(3.seconds) {
                bot.dialogues == null || bot.overlays.getOverlay(DialogueInterface::class.java) !== before
            }
        }
        val coffin = world.locator.findObjects(bot, 18) { it.id == brother.sarcophagusId }.firstOrNull() ?: return false
        if (!handler.interactions.interact(1, coffin)) return false
        return waitFor(5.seconds) {
            bot.dialogues != null || world.locator.findNpcs(bot, 16) {
                it is BarrowsBrotherNpc && it.owner === bot && it.state == EntityState.ACTIVE
            }.isNotEmpty()
        }
    }

    /**
     * Explores unlocked native doors, preferring the least-used doors while permitting necessary backtracking.
     * Collision-checked approaches remain with the existing web-walker. Rerolled locks invalidate the visit counts.
     */
    private suspend fun tunnels(): Boolean {
        val state = BarrowsRun.state(bot)
        val layout = state and ((1 shl 26) - 1)
        if (layout != mazeState) { crossings.clear(); lastDoor = null; mazeState = layout }
        if (bot.overlays.has(BarrowsPuzzleInterface::class.java)) {
            val answer = BarrowsVarbit.PUZZLE.read(state)
            if (answer !in 0..2) return false
            // The active answer slot is supplied to the client in varbit485; the ordinary button handler validates it.
            if (!handler.widgets.clickButton(4543, 4550 + answer)) return false
            return waitFor(3.seconds) { !bot.overlays.has(BarrowsPuzzleInterface::class.java) }
        }
        if (bot.position.isWithinDistance(BarrowsChest.position, 3)) {
            while (bot.inventory.computeRemainingSize() < 8) {
                val expendable = bot.inventory.filterNotNull().firstOrNull { it.id == 229 }
                    ?: bot.inventory.filterNotNull().firstOrNull {
                        food(bot, true) > 4 && (Food.ID_TO_FOOD[it.id]?.heal ?: 0) >= 16
                    }
                    ?: break
                if (!handler.inventory.dropItem(expendable.id)) return false
            }
            if (bot.inventory.computeRemainingSize() < 8) return false
            val chest = world.locator.findObjectsOnTile(BarrowsChest.position) { it.id == CHEST }.firstOrNull() ?: return false
            return handler.interactions.interact(1, chest)
        }
        val options = world.locator.findObjects(BarrowsChest.position, 40) { obj ->
            BarrowsDoor.entries.any { (obj.id == it.leftId || obj.id == it.rightId) && it.locked.read(state) == 0 }
        }.groupBy { obj -> BarrowsDoor.entries.first { obj.id == it.leftId || obj.id == it.rightId } }
            .map { (door, leaves) -> door to leaves.minBy { it.position.computeLongestDistance(bot.position) } }
            .sortedWith(compareBy<Pair<BarrowsDoor, GameObject>> { crossings[it.first] ?: 0 }
                .thenBy { if (it.first == lastDoor) 1 else 0 }
                .thenBy { it.second.position.computeLongestDistance(bot.position) +
                    it.second.position.computeLongestDistance(BarrowsChest.position) })
        for ((door, objectDoor) in options) {
            if (!attend(15_000) { handler.interactions.approach(objectDoor, timeout = 15.seconds,
                    canContinue = { isActionSafe() && !returning }) }) {
                if (bot.overlays.has(BarrowsPuzzleInterface::class.java)) return true
                continue
            }
            val from = bot.position
            if (!handler.interactions.interact(1, objectDoor)) continue
            if (bot.overlays.has(BarrowsPuzzleInterface::class.java)) return true
            if (waitFor(3.seconds) { bot.position != from }) {
                crossings[door] = (crossings[door] ?: 0) + 1
                lastDoor = door
                return true
            }
        }
        return false
    }

    /**
     * Uses the same normal home command as other bot boss retreats; no progress, rewards or native locks are altered.
     */
    private suspend fun leaveUnderground(): Boolean {
        bot.combat.target = null
        clearMagic()
        output.sendCommand("home")
        return waitFor(8.seconds) { !BarrowsAreaListener.inside(bot.position) }
    }

    override fun paused() {
        bot.navigator.cancel(); bot.walking.clear()
        crossings.clear(); lastDoor = null
    }

    override suspend fun finish() {
        tactics.stopAttending()
        if (!isPaused()) {
            bot.combat.target = null
            clearMagic()
            bot.combat.prayers.deactivateAll()
            previousRetaliate?.let { bot.sendVarp(PersistentVarp.AUTO_RETALIATE, it) }
        }
    }

    override fun snapshot() = BarrowsData().also {
        it.attempted = attempted; it.deadline = deadline; it.failures = failures
        it.returning = returning; it.runStarted = runStarted; it.previousRetaliate = previousRetaliate
    }
}
