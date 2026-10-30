package game.bot.scripts

import api.bot.script.BotScript
import api.bot.action.BotCombatActionHandler
import api.bot.script.BotScriptData
import api.bot.webwalk.plan.BankAccess
import com.google.gson.JsonObject
import engine.combat.prayer.CombatPrayer
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import engine.controllers.Controllers.inWilderness
import game.content.mageArena.MageArena
import game.content.mageArena.MageArenaNpc
import game.player.item.consume.food.Food
import game.skill.magic.Magic
import game.skill.magic.SpellCost
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.game.model.mob.combat.CombatSpell
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Makes one bounded Mage Arena visit using owned supplies and native interactions.
 * Completes Kolodion, claims one god's equipment, trains its spell and returns to the safe cave.
 * Stage and cast counters belong to the player; saved deadlines prevent repeated free attempts after a pause.
 *
 * @author lare96
 */
class MageArenaBotScript(bot: Bot, private val data: ArenaData = ArenaData()) : BotScript(bot) {
    companion object {
        const val FOOD = 385
        const val STAFF = 1381
        val supplies = mapOf(FOOD to 16, 2434 to 3, 560 to 200, 554 to 1198, 556 to 396, 565 to 198)

        fun eligible(bot: Bot): Boolean = bot.state == EntityState.ACTIVE && bot.isAlive &&
            bot.spellbook == Spellbook.REGULAR && bot.skill(Skill.MAGIC).staticLevel >= 60 && bot.skill(Skill.PRAYER).staticLevel >= 37 &&
            bot.skill(Skill.HITPOINTS).staticLevel >= 60 && BotCombatActionHandler.mayGainExperience(bot, Skill.MAGIC) &&
            bot.controllers.primary.javaClass == PlayerController::class.java && !bot.overlays.hasWindow() &&
            !bot.combat.inCombat() && !bot.inWilderness() &&
            (MageArena.stage(bot) < MageArena.STAFF_GIVEN || MageArena.God.entries.any {
                bot.itemTracker.count(it.cape) > 0 && bot.itemTracker.count(it.staff) > 0 &&
                    MageArena.casts(bot, it) < MageArena.SPELL_CASTS
            }) && bot.itemTracker.count(STAFF) > 0 && supplies.all { (id, amount) -> bot.itemTracker.count(id) >= amount }
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
        if (resumed || data.attempted) { data.leaving = true; return false }
        if (!eligible(bot)) return true
        data.attempted = true
        data.god = (MageArena.ownedGod(bot) ?: MageArena.God.entries.firstOrNull {
            bot.itemTracker.count(it.cape) > 0 && bot.itemTracker.count(it.staff) > 0 && MageArena.casts(bot, it) < MageArena.SPELL_CASTS
        } ?: MageArena.God.SARADOMIN).ordinal
        return withTimeoutOrNull(180_000) {
            if (!handler.banking.travelToBankDepositAll() || !handler.equipment.unequipAll() ||
                !handler.banking.depositInventory()) return@withTimeoutOrNull true
            for ((id, amount) in supplies) if (!handler.banking.withdraw(Item(id, amount))) return@withTimeoutOrNull true
            if (!handler.banking.withdraw(Item(STAFF)) || !handler.widgets.clickCloseInterface() ||
                !handler.equipment.equip(STAFF)) return@withTimeoutOrNull true
            if (MageArena.stage(bot) >= MageArena.STAFF_GIVEN) {
                // Existing equipment must already be owned; no reward or supply is synthesized.
                if (!handler.banking.travelToBankOpen() || !handler.banking.withdraw(Item(god.cape)) ||
                    !handler.banking.withdraw(Item(god.staff)) || !handler.widgets.clickCloseInterface()) return@withTimeoutOrNull true
            }
            if (!handler.combat.pkRiskBudget().withinBudget ||
                !handler.webWalkTo(Position(3091, 3956), radius = 2, bank = BankAccess.NONE, fallback = false)) return@withTimeoutOrNull true
            !click(5959) { MageArena.cave(bot.position) }
        } ?: true
    }

    private fun foodCount(): Int = bot.inventory.filterNotNull().count { it.id in Food.ID_TO_FOOD }

    /**
     * Sends native manual spell selections and attack clicks, then observes progression instead of awarding it.
     */
    private suspend fun fight(target: Npc, spell: CombatSpell): Boolean {
        if (!safe() || bot.healthPercent < 35 || foodCount() <= 2 ||
            Magic.spellCost(bot, spell.def.level, spell.def.required) !is SpellCost.Castable) return false
        bot.combat.prayers.activateIfOff(CombatPrayer.PROTECT_FROM_MAGIC)
        handler.potions.drink(true, false, prayerBelow = 15)
        if (bot.healthPercent < 70) {
            val food = bot.inventory.withIndex().firstOrNull { it.value?.id in Food.ID_TO_FOOD }
            if (food != null) handler.inventory.eat(food.index, food.value!!.id)
        }
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
        if (MageArena.ownedGod(bot) == null) {
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
        if (MageArena.stage(bot) == 7 && !talk(904, done = { MageArena.stage(bot) == MageArena.STAFF_GIVEN })) return false
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
        data.leaving = true
        if (bot.combat.target is MageArenaNpc || (bot.combat.target as? Npc)?.id in 912..914) bot.combat.target = null
        if (!priorProtection) bot.combat.prayers.deactivate(CombatPrayer.PROTECT_FROM_MAGIC, true)
    }
    override fun snapshot(): ArenaData = data
}
