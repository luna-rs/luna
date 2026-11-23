package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotCombatActionHandler
import api.bot.action.BotSupplySources
import api.bot.action.PkRiskBudget
import api.bot.action.trading.BotTradeIntentions
import api.bot.boss.BossFood
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.webwalk.plan.BankAccess
import api.combat.death.PlayerDeathItems
import api.predef.ext.totalItemAmount
import com.google.gson.JsonObject
import engine.combat.prayer.CombatPrayer
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import engine.controllers.Controllers.inWilderness
import game.content.mageArena.MageArena
import game.content.mageArena.MageArenaNpc
import game.bot.scripts.trading.TradeBotScript
import game.player.item.consume.food.Food
import game.skill.magic.Magic
import game.skill.magic.RuneRequirement
import game.skill.magic.SpellCost
import game.skill.magic.Staff
import io.luna.Luna
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Prepares and makes one bounded Mage Arena visit through native purchases, banking and combat.
 * Completes Kolodion, claims one god's equipment, trains its spell and returns to the safe cave.
 * Stage and cast counters belong to the player; saved deadlines prevent repeated free attempts after a pause.
 *
 * @author lare96
 */
class MageArenaBotScript(bot: Bot, private val data: ArenaData = ArenaData()) : BotScript(bot) {
    companion object {
        private const val AIR_STAFF = 1381
        private const val PRAYER_POTION = 2434
        private const val CHALLENGE_CASTS = 200
        private const val MAXIMUM_FOOD_SLOTS = 20
        private val foodPolicy = BossFood.Policy(minimumBiteHealing = 10, requiredHealing = 240)
        private var Bot.arenaRetryAt by Attr.long().persist("bot_mage_arena_retry_at")

        fun eligible(bot: Bot): Boolean = bot.state == EntityState.ACTIVE && bot.isAlive &&
            bot.spellbook == Spellbook.REGULAR && bot.skill(Skill.MAGIC).staticLevel >= 60 && bot.skill(Skill.PRAYER).staticLevel >= 37 &&
            bot.skill(Skill.HITPOINTS).staticLevel >= 60 && BotCombatActionHandler.mayGainExperience(bot, Skill.MAGIC) &&
            bot.controllers.primary.javaClass == PlayerController::class.java && !bot.overlays.hasWindow() &&
            !bot.combat.inCombat() && !bot.inWilderness() && System.currentTimeMillis() >= bot.arenaRetryAt &&
            (MageArena.stage(bot) < MageArena.STAFF_GIVEN || unfinishedGod(bot) != null)

        private fun unfinishedGod(bot: Bot): MageArena.God? = MageArena.ownedGod(bot)?.takeIf {
            bot.itemTracker.count(it.staff) > 0 && MageArena.casts(bot, it) < MageArena.SPELL_CASTS
        }
    }

    /**
     * Saves attempt ownership, selected god and real time limits, independent of player progression.
     *
     * @author lare96
     */
    class ArenaData : BotScriptData() {
        var attempted = false
        var leaving = false
        var god = 0
        var deadline = System.currentTimeMillis() + 1_200_000
        var lastProgress = System.currentTimeMillis()
        var progress = -1
        var failures = 0
        override fun load(data: JsonObject) {
            attempted = data.get("attempted").asBoolean; leaving = data.get("leaving").asBoolean
            god = data.get("god").asInt.coerceIn(0, 2); deadline = data.get("deadline").asLong
            lastProgress = data.get("lastProgress").asLong; progress = data.get("progress").asInt
            failures = data.get("failures").asInt
        }
        override fun save(data: JsonObject) {
            data.addProperty("attempted", attempted); data.addProperty("leaving", leaving)
            data.addProperty("god", god); data.addProperty("deadline", deadline)
            data.addProperty("lastProgress", lastProgress); data.addProperty("progress", progress)
            data.addProperty("failures", failures)
        }
    }

    private val god get() = MageArena.God.entries[data.god]
    private var castAt = 0L
    private var priorProtection = false
    private suspend fun equip(id: Int): Boolean = bot.equipment.contains(id) || handler.equipment.equip(id)
    override val actionSafety = ActionSafety.COMBAT
    override val allowsCombatResponse = false
    override fun allowsLoot(item: GroundItem): Boolean = item.id == god.cape || item.id == god.staff
    private fun live() = bot.state == EntityState.ACTIVE && bot.isAlive && bot.controllers.primary.javaClass == PlayerController::class.java
    private fun safe() = live() && !bot.isLocked
    private fun objectAt(id: Int): GameObject? = bot.world.locator.findObjects(bot.position, 16) {
        it.id == id && it.state == EntityState.ACTIVE
    }.minByOrNull { it.position.computeLongestDistance(bot.position) }

    private suspend fun await(timeout: Long = 15_000, condition: () -> Boolean): Boolean = withTimeoutOrNull(timeout) {
        while (live() && !condition()) delay(100)
        live() && condition()
    } == true

    private suspend fun click(id: Int, condition: () -> Boolean): Boolean {
        val obj = objectAt(id) ?: return false
        if (!handler.interactions.interact(1, obj) { live() }) return false
        return await { safe() && condition() }
    }

    /**
     * Advances only this NPC conversation, choosing its explicit fight response when it appears.
     */
    private suspend fun talk(npcId: Int, fight: Boolean = false, done: () -> Boolean): Boolean {
        val npc = bot.world.locator.findNpcs(bot.position, 16) { it.id == npcId }.firstOrNull() ?: return false
        if (!handler.interactions.interact(1, npc) { live() }) return false
        return withTimeoutOrNull(20_000) {
            var advances = 0
            while (live() && !done() && advances++ < 30) {
                val options = bot.overlays.getOverlay(OptionDialogue::class.java)
                if (options != null) {
                    if (!fight || !handler.widgets.clickDialogueOption("Okay, let's fight.")) break
                } else bot.output.sendContinueDialogue()
                delay(500)
            }
            live() && done()
        } == true
    }

    override suspend fun init(resumed: Boolean): Boolean {
        priorProtection = bot.combat.prayers.isActive(CombatPrayer.PROTECT_FROM_MAGIC)
        if (!live()) return true
        if (data.attempted) {
            data.leaving = true
            return false
        }
        if (bot.isEventBot) {
            if (!MageArena.cave(bot.position) || !MageArena.canStart(bot)) return true
            data.god = MageArena.God.SARADOMIN.ordinal
            data.attempted = true
            data.lastProgress = System.currentTimeMillis()
            return false
        }
        if (!eligible(bot) || System.currentTimeMillis() >= data.deadline) return true
        data.god = (MageArena.ownedGod(bot) ?: MageArena.God.SARADOMIN).ordinal
        if (withTimeoutOrNull(minOf(180_000L, data.deadline - System.currentTimeMillis())) { prepare() } != true) {
            bot.log("Mage Arena: could not pack the required supplies and healing.")
            return true
        }
        if (!withinRiskBudget()) {
            bot.log("Mage Arena: the carried kit exceeds the Wilderness loss budget.")
            return true
        }
        data.attempted = true
        data.leaving = withTimeoutOrNull(minOf(180_000L, data.deadline - System.currentTimeMillis())) {
            handler.webWalkTo(Position(3091, 3956), radius = 2, bank = BankAccess.NONE, fallback = false) &&
                click(5959) { MageArena.cave(bot.position) }
        } != true
        data.lastProgress = System.currentTimeMillis()
        return false
    }

    /**
     * Buys shortages while home or at shops, then packs a fresh loadout from real bank stock.
     * A trading child may pause preparation. On return, only quantities still missing are requested.
     */
    private suspend fun prepare(): Boolean {
        if (!handler.banking.travelToBankDepositAll() || !handler.equipment.unequipAll() ||
            !handler.banking.depositInventory()) return false
        val stage = MageArena.stage(bot)
        val supplies = buildMap {
            put(PRAYER_POTION, 3)
            if (stage < MageArena.COMPLETE) {
                put(AIR_STAFF, 1)
                addRunes(CombatSpell.FIRE_BLAST, CHALLENGE_CASTS, AIR_STAFF)
            }
            addRunes(god.spell, MageArena.SPELL_CASTS - MageArena.casts(bot, god), god.staff)
        }
        if (!obtainSupplies(supplies)) return false
        val food = foodPurchases() ?: return false
        if (!obtainSupplies(food) || !handler.banking.travelToBankDepositAll()) return false
        handler.banking.clickBankingMode(noted = false)

        val cape = MageArena.ownedGod(bot)?.cape
        val equipment = listOfNotNull(
            AIR_STAFF.takeIf { stage < MageArena.COMPLETE },
            cape,
            god.staff.takeIf { stage >= MageArena.STAFF_GIVEN })
        val loadout = supplies + equipment.associateWith { 1 }
        for ((id, amount) in loadout) {
            if (!handler.banking.withdraw(Item(id, amount)) || bot.inventory.computeAmountForId(id) < amount) return false
        }
        return BossFood.withdraw(bot, foodPolicy) && handler.widgets.clickCloseInterface() &&
            equipment.all { equip(it) } && live() && !bot.combat.inCombat() && !bot.inWilderness()
    }

    private fun MutableMap<Int, Int>.addRunes(spell: CombatSpell, casts: Int, staff: Int) {
        if (casts <= 0) return
        val supplied = Staff.ID_TO_STAFF[staff]?.represents.orEmpty()
        for (requirement in spell.def.required.filterIsInstance<RuneRequirement>()) {
            if (requirement.rune !in supplied) merge(requirement.rune.id, requirement.amount * casts, Int::plus)
        }
    }

    private suspend fun obtainSupplies(targets: Map<Int, Int>): Boolean {
        val missing = targets.filter { (id, amount) -> bot.totalItemAmount(id) < amount }
        if (missing.isEmpty()) return true
        BotTradeIntentions.recordSupplies(bot, missing.map { (id, amount) -> Item(id, amount) }, stockTarget = 1)
        handler.shop.obtainWantedItems(missing)
        return missing.all { (id, amount) -> bot.totalItemAmount(id) >= amount }
    }

    /**
     * Uses banked mixed or partial meals first, then considers affordable food shown by native shops or the market.
     * Requests only the quantities needed by the shared healing plan within the trip's physical food slots.
     */
    private fun foodPurchases(): Map<Int, Int>? {
        val owned = BossFood.stock(bot.inventory, bot.bank)
        if (BossFood.plan(owned, foodPolicy) != null) return emptyMap()
        val coins = bot.totalItemAmount(995)
        if (coins == 0) return null
        val ids = Food.ID_TO_FOOD.keys.filterTo(linkedSetOf()) { id ->
            (BossFood.portion(id)?.biteHealing ?: 0) >= foodPolicy.minimumBiteHealing
        }
        val shops = BotSupplySources.quotes(bot, ids, MAXIMUM_FOOD_SLOTS).map { (id, quote) ->
            Triple(id, quote.quantity, quote.maximumUnitPrice)
        }
        val trades = TradeBotScript.carriedSupply(bot, ids).map { Triple(it.id, it.quantity, it.unitPrice) }
        val sources = (shops + trades).sortedWith(compareBy<Triple<Int, Int, Long>> { (id, _, price) ->
            price.toDouble() / BossFood.portion(id)!!.healing
        }
            .thenBy { it.first })
        val available = owned.toMutableMap()
        for ((id, quantity, unitPrice) in sources) {
            if (unitPrice <= 0) continue
            val additional = minOf(quantity.toLong(), coins / unitPrice, MAXIMUM_FOOD_SLOTS.toLong()).toInt()
            if (additional <= 0) continue
            val stocked = (available[id] ?: 0).coerceAtMost(MAXIMUM_FOOD_SLOTS)
            for (amount in stocked + 1..minOf(MAXIMUM_FOOD_SLOTS, stocked + additional)) {
                available[id] = amount
                val plan = BossFood.plan(available, foodPolicy) ?: continue
                return plan.filter { it.amount > (owned[it.id] ?: 0) }.associate { it.id to it.amount }
            }
        }
        return null
    }

    private fun withinRiskBudget(): Boolean {
        if (bot.preferences.pk != null) return handler.combat.pkRiskBudget().withinBudget
        val share = PkRiskBudget.basisPoints(bot.personality.confidence, bot.personality.intelligence,
            bot.preferences.activities[BotActivity.PKING] ?: 0.0)
        val losesItems = PlayerDeathItems.losesItems(bot, Luna.settings().game().betaMode())
        return PkRiskBudget.assessor(bot, losesItems, shareOverride = share)(
            bot.inventory.filterNotNull(), bot.equipment.filterNotNull(), false).withinBudget
    }

    /**
     * Sends native manual spell selections and attack clicks, then observes progression instead of awarding it.
     */
    private suspend fun fight(target: Npc, spell: CombatSpell): Boolean {
        if (!safe() || bot.healthPercent < 35 || BossFood.carriedHealing(bot, foodPolicy) <= foodPolicy.minimumBiteHealing * 2 ||
            Magic.spellCost(bot, spell.def.level, spell.def.required) !is SpellCost.Castable) return false
        bot.combat.prayers.activateIfOff(CombatPrayer.PROTECT_FROM_MAGIC)
        handler.potions.drink(true, false, prayerBelow = 15)
        if (bot.healthPercent < 70) BossFood.eat(bot, foodPolicy)
        if (System.currentTimeMillis() - castAt >= 3_200 && safe()) {
            if (!handler.interactions.approach(target, spellPolicy)) return false
            if (!handler.combat.selectAndUseSpell(spell)) return false
            if (!bot.output.sendNpcInteraction(3, target)) return false
            castAt = System.currentTimeMillis()
        }
        return true
    }

    private val spellPolicy = io.luna.game.model.mob.interact.InteractionPolicy.STANDARD_LINE_OF_SIGHT

    private suspend fun rewards(): Boolean {
        if (MageArena.cave(bot.position) && !click(2878) { MageArena.chamber(bot.position) }) return false
        if (!MageArena.chamber(bot.position)) return false
        if (MageArena.ownedGod(bot) == null && bot.isEventBot) {
            if (!handler.webWalkTo(god.capeTile, radius = 1, bank = BankAccess.NONE, fallback = false) ||
                !click(god.statue) { MageArena.ownedGod(bot) == god }) return false
        } else if (MageArena.ownedGod(bot) == null) {
            val drops = bot.world.locator.findItems(god.capeTile, 1, true) {
                it.id == god.cape && it.state == EntityState.ACTIVE && it.isVisibleTo(bot)
            }
            if (drops.isEmpty()) {
                if (!handler.webWalkTo(god.capeTile, radius = 1, bank = BankAccess.NONE, fallback = false) ||
                    !click(god.statue) { bot.world.locator.findItems(god.capeTile, 0, true) { it.id == god.cape }.isNotEmpty() }) return false
            }
            val cape = bot.world.locator.findItems(god.capeTile, 1, true) { it.id == god.cape && it.isVisibleTo(bot) }.firstOrNull()
                ?: return false
            if (!handler.loot.pickup(cape)) return false
        }
        if (MageArena.stage(bot) == 7 &&
            (!equip(god.cape) || !talk(904, done = { MageArena.stage(bot) == MageArena.STAFF_GIVEN }))) return false
        return MageArena.stage(bot) >= MageArena.STAFF_GIVEN
    }

    private suspend fun enterArena(): Boolean {
        if (MageArena.chamber(bot.position) && !click(2879) { MageArena.cave(bot.position) }) return false
        if (MageArena.cave(bot.position) && !click(5960) { !MageArena.cave(bot.position) }) return false
        if (!handler.webWalkTo(Position(3105, 3956), radius = 1, bank = BankAccess.NONE, fallback = false)) return false
        return click(9706) { MageArena.arena(bot.position) }
    }

    private suspend fun escape(): Boolean {
        bot.combat.target = null
        if (MageArena.chamber(bot.position)) return click(2879) { MageArena.cave(bot.position) }
        if (MageArena.cave(bot.position)) return true
        if (MageArena.arena(bot.position) && !click(9707) { !MageArena.arena(bot.position) }) return false
        if (bot.inWilderness()) {
            if (handler.webWalkTo(Position(3091, 3956), radius = 2, bank = BankAccess.NONE, fallback = false) &&
                click(5959) { MageArena.cave(bot.position) }) return true
            return handler.combat.fleeWilderness()
        }
        return true
    }

    override suspend fun run(): Boolean {
        if (!live()) return true
        val progress = MageArena.stage(bot) * 1000 + MageArena.casts(bot, god)
        if (progress != data.progress) { data.progress = progress; data.lastProgress = System.currentTimeMillis() }
        if (System.currentTimeMillis() >= data.deadline || System.currentTimeMillis() - data.lastProgress > 120_000 ||
            data.failures >= 3 || bot.healthPercent < 35 ||
            bot.combat.inCombat() && bot.combat.lastAttackReceived?.attacker is io.luna.game.model.mob.Player) data.leaving = true
        if (data.leaving) {
            data.failures++
            return withTimeoutOrNull(90_000) { escape() } == true || data.failures >= 5
        }
        if (bot.isLocked) { delay(300); return false }
        val okay = when {
            MageArena.stage(bot) < MageArena.COMPLETE -> {
                if (MageArena.cave(bot.position)) talk(905, fight = true) { MageArena.encounter(bot) != null }
                else if (MageArena.arena(bot.position)) {
                    MageArena.encounter(bot)?.let { fight(it, CombatSpell.FIRE_BLAST) } ?: run {
                        delay(2_000); true
                    }
                } else false
            }
            MageArena.stage(bot) < MageArena.STAFF_GIVEN -> rewards()
            MageArena.casts(bot, god) >= MageArena.SPELL_CASTS -> { data.leaving = true; true }
            !MageArena.arena(bot.position) -> {
                equip(god.cape) && equip(god.staff) && enterArena()
            }
            else -> {
                val target = bot.world.locator.findNpcs(bot.position, 16) {
                    it.id in 912..914 && it.isAlive && MageArena.arena(it.position)
                }.minByOrNull { it.position.computeLongestDistance(bot.position) }
                target != null && fight(target, god.spell)
            }
        }
        if (!okay) data.failures++
        delay(500)
        return false
    }

    override suspend fun finish() {
        if (!data.attempted) return
        data.leaving = true
        if (bot.combat.target is MageArenaNpc || (bot.combat.target as? Npc)?.id in 912..914) bot.combat.target = null
        if (!priorProtection) bot.combat.prayers.deactivate(CombatPrayer.PROTECT_FROM_MAGIC, true)
    }

    override suspend fun completed() {
        if (!data.attempted) bot.arenaRetryAt = System.currentTimeMillis() + 600_000L
    }

    override fun snapshot(): ArenaData = data
}
