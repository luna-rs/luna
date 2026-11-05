package game.bot.scripts

import api.bot.action.BotCombatActionHandler
import api.bot.action.CombatLoadout
import api.bot.script.BotScript
import api.bot.script.BotScriptData
import api.bot.webwalk.plan.BankAccess
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.gear.BotGearLocator
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import engine.combat.prayer.CombatPrayer
import game.combat.npcHooks.tzhaar.JadAttack
import game.combat.npcHooks.tzhaar.JadHealers
import game.content.fightCave.FightCave
import game.content.fightCave.FightCaveController
import game.player.item.consume.food.Food
import io.luna.game.model.EntityState
import io.luna.game.model.def.AmmoDefinition
import io.luna.game.model.def.CombatSpellDefinition
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Equipment.EquipmentBonus
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.block.UpdateBlockData
import io.luna.game.model.mob.controller.PlayerController
import io.luna.game.model.mob.varp.PersistentVarp
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Attempts one native Fight Cave run with an owned ranged outfit, sharks and prayer potions.
 * Entry, attacks, prayer changes, consumables and the exit use client actions. The cave controller owns all
 * waves, saved progress and rewards. A restored attempt continues only in its native resumed cave; it never
 * restocks or starts another run after losing one. Jad's visible tells are watched even during exit navigation.
 *
 * @author lare96
 */
class FightCaveBotScript(bot: Bot, private val data: FightCaveData = FightCaveData()) : BotScript(bot) {
    companion object {
        const val FOOD = 385
        const val PRAYER = 2434
        const val FOOD_COUNT = 12
        const val POTION_COUNT = 14
        const val AMMO_COUNT = 2500
        private val PRAYER_DOSES = setOf(2434, 139, 141, 143)
        private val PROTECTION = listOf(CombatPrayer.PROTECT_FROM_MAGIC, CombatPrayer.PROTECT_FROM_MISSILES,
            CombatPrayer.PROTECT_FROM_MELEE)

        /** Strong permanent levels and account-compatible experience are required before spending supplies. */
        internal fun levelsReady(bot: Bot): Boolean = !bot.preferences.isSkiller &&
            bot.ranged.staticLevel >= 85 && bot.defence.staticLevel >= 80 && bot.hitpoints.staticLevel >= 85 &&
            bot.prayer.staticLevel >= 60 && listOf(Skill.RANGED, Skill.HITPOINTS).all {
                BotCombatActionHandler.mayGainExperience(bot, it)
            }

        /** Only an owned bow and compatible strong ammunition qualify; no free-ammo locator fallback is used. */
        internal fun loadout(bot: Bot): CombatLoadout? {
            val outfit = CombatLoadout.find(bot, BotGearPurpose.RANGED) ?: return null
            val weapon = outfit.gear[Equipment.WEAPON] ?: return null
            val ammo = outfit.gear[Equipment.AMMUNITION] ?: return null
            val definition = AmmoDefinition.AMMO_REQUIRING_WEAPONS[ammo] ?: return null
            if (weapon !in definition.weapons || definition.strength < 30 || bot.itemTracker.count(ammo) < AMMO_COUNT)
                return null
            if (outfit.gear.filterNotNull().sumOf { equipDef(it).getBonus(EquipmentBonus.RANGED_ATTACK.index) } < 80)
                return null
            return outfit
        }

        /** Both activity coordinators use the same admission rule; repeat cape farming is deliberately excluded. */
        fun canStart(bot: Bot): Boolean = bot.state == EntityState.ACTIVE && ActionSafety.NON_COMBAT.allows(bot) &&
            bot.controllers.primary.javaClass == PlayerController::class.java && !bot.overlays.hasWindow() &&
            !FightCave.hasFireCape(bot) && levelsReady(bot) && bot.itemTracker.count(FOOD) >= FOOD_COUNT &&
            bot.itemTracker.count(PRAYER) >= POTION_COUNT && bot.itemTracker.count(995) >= 3000 && loadout(bot) != null

        /** Jad protection follows the visible animation rather than waiting for its potentially lethal hit. */
        internal fun tellPrayer(animation: Int?): CombatPrayer? = when (animation) {
            JadAttack.MAGIC_ANIMATION -> CombatPrayer.PROTECT_FROM_MAGIC
            JadAttack.RANGED_ANIMATION -> CombatPrayer.PROTECT_FROM_MISSILES
            2655 -> CombatPrayer.PROTECT_FROM_MELEE
            else -> null
        }

        /** Kill prayer-draining bats, then remove damage the current overhead cannot block. */
        internal fun priority(id: Int): Int = when (id) {
            2734, 2735 -> 0
            2739, 2740 -> 1
            2743, 2744 -> 2
            2741, 2742 -> 3
            else -> 4
        }
    }

    /** Absolute budgets and attempt ownership survive saves; native player attributes retain the wave. @author lare96 */
    class FightCaveData : BotScriptData() {
        var attempted = false
        var retreating = false
        var deadline = System.currentTimeMillis() + 5_400_000
        var lastProgress = System.currentTimeMillis()
        var previousRetaliate: Int? = null
        override fun load(data: JsonObject) {
            attempted = data.get("attempted").asBoolean
            retreating = data.get("retreating").asBoolean
            deadline = data.get("deadline").asLong
            lastProgress = data.get("lastProgress").asLong
            previousRetaliate = data.get("previousRetaliate")?.takeUnless { it.isJsonNull }?.asInt
        }
        override fun save(data: JsonObject) {
            data.addProperty("attempted", attempted)
            data.addProperty("retreating", retreating)
            data.addProperty("deadline", deadline)
            data.addProperty("lastProgress", lastProgress)
            previousRetaliate?.let { data.addProperty("previousRetaliate", it) }
        }
    }

    override val actionSafety = ActionSafety.COMBAT
    override val allowsCombatResponse = false
    override fun allowsLoot(item: GroundItem): Boolean = cave == null && data.attempted &&
        item.id in setOf(FightCave.FIRE_CAPE, FightCave.TOKKUL) && item.position.isWithinDistance(bot.position, 2)
    private val cave get() = bot.controllers.primary as? FightCaveController
    private var nextFood = 0L
    private var nextPotion = 0L
    private var nextAttack = 0L
    private var lastTell: UpdateBlockData? = null
    private var jadPrayer = CombatPrayer.PROTECT_FROM_MAGIC
    private var observed: List<Pair<Npc, Int>> = emptyList()
    private var observedWave = -1
    private var exitFailures = 0
    private var protectedUntil = -1L
    private var jadProtectedUntil = -1L
    private var mageProtectedUntil = -1L

    override suspend fun init(resumed: Boolean): Boolean {
        if (data.attempted) {
            if (cave == null) return true
            data.retreating = data.retreating || !levelsReady(bot) || !rangedReady()
            takeCombat()
            return false
        }
        if (System.currentTimeMillis() >= data.deadline || !canStart(bot)) return true
        val outfit = loadout(bot) ?: return true
        val ammo = outfit.gear[Equipment.AMMUNITION]!!
        val prepared = withTimeoutOrNull(240_000) {
            if (!BotGearLocator(bot, outfit.gear).locateAndEquip() || !handler.banking.travelToBankDepositAll())
                return@withTimeoutOrNull false
            val extraAmmo = AMMO_COUNT - bot.equipment.computeAmountForId(ammo)
            val supplies = listOf(Item(FOOD, FOOD_COUNT), Item(PRAYER, POTION_COUNT), Item(995, 3000)) +
                if (extraAmmo > 0) listOf(Item(ammo, extraAmmo)) else emptyList()
            if (!handler.banking.withdrawAll(supplies) || !handler.widgets.clickCloseInterface())
                return@withTimeoutOrNull false
            if (extraAmmo > 0 && !handler.equipment.equip(ammo)) return@withTimeoutOrNull false
            bot.inventory.computeAmountForId(FOOD) >= FOOD_COUNT &&
                bot.inventory.computeAmountForId(PRAYER) >= POTION_COUNT &&
                bot.equipment.computeAmountForId(ammo) >= AMMO_COUNT && rangedReady()
        } == true
        if (!prepared) return true
        takeCombat()
        if (withTimeoutOrNull(240_000) {
                handler.webWalkTo(FightCave.OUTSIDE, radius = 2, bank = BankAccess.NONE, fallback = false)
            } != true) return true
        val entrance = bot.world.locator.findObjects(bot, 16) { it.id == FightCave.ENTRANCE }.firstOrNull()
            ?: return true
        if (!handler.interactions.approach(entrance)) return true
        // Mark ownership before submitting the click, so cancellation after entry cannot authorize a fresh attempt.
        data.attempted = true
        if (!output.sendObjectInteraction(1, entrance)) return true
        val entered = withTimeoutOrNull(5000) {
            while (cave == null) delay(100)
            true
        } == true
        data.lastProgress = System.currentTimeMillis()
        return !entered
    }

    private fun takeCombat() {
        if (data.previousRetaliate == null)
            data.previousRetaliate = bot.varpManager.getValue(PersistentVarp.AUTO_RETALIATE)
        bot.sendVarp(PersistentVarp.AUTO_RETALIATE, 1)
        bot.combat.magic.selectedSpell = CombatSpellDefinition.NONE
        bot.combat.magic.autocastSpell = CombatSpellDefinition.NONE
        bot.combat.magic.isAutocasting = false
        val style = BotCombatActionHandler.allowedStyles(bot, bot.combat.weapon.typeDef.styles)
            .firstOrNull { Skill.RANGED in it.exp }
        if (style != null) bot.combat.weapon.changeStyle(style.button)
    }

    private fun rangedReady(): Boolean {
        val weapon = bot.equipment.weapon?.id ?: return false
        val ammo = bot.equipment[Equipment.AMMUNITION]?.id ?: return false
        return bot.combat.weapon.isRanged && AmmoDefinition.AMMO_REQUIRING_WEAPONS[ammo]?.weapons?.contains(weapon) == true &&
            bot.equipment.computeAmountForId(ammo) > 0 &&
            BotCombatActionHandler.allowedStyles(bot, bot.combat.weapon.typeDef.styles).any { Skill.RANGED in it.exp }
    }

    private fun enemies(): List<Npc> {
        val map = bot.dynamicMap ?: return emptyList()
        return bot.world.locator.findNpcs(bot, 64) {
            it.state == EntityState.ACTIVE && it.isAlive && map.contains(it.position) &&
                (it.id in FightCaveController.WAVE_MONSTERS || it.id == JadHealers.HEALER)
        }.toList()
    }

    /** Non-suspending maintenance keeps Jad's tells observable while moving, eating and fighting healers. */
    internal fun maintain(monsters: List<Npc>) {
        if (!isActionSafe() || cave == null) return
        val tick = bot.world.currentTick
        val jad = monsters.firstOrNull { it.id == 2745 && it.isViewableFrom(bot) }
        if (monsters.any { it.id in 2743..2744 }) mageProtectedUntil = tick + 4
        val protection = if (jad != null) {
            jadProtectedUntil = tick + 6
            val tell = jad.blockData
            if (tell !== lastTell) {
                tellPrayer(tell?.animation?.id)?.let { jadPrayer = it }
                lastTell = tell
            }
            jadPrayer
        } else when {
            tick <= jadProtectedUntil -> jadPrayer
            tick <= mageProtectedUntil -> CombatPrayer.PROTECT_FROM_MAGIC
            monsters.any { it.id in 2739..2740 } -> CombatPrayer.PROTECT_FROM_MISSILES
            monsters.isNotEmpty() -> CombatPrayer.PROTECT_FROM_MELEE
            else -> null
        }
        if (protection != null) {
            protectedUntil = bot.world.currentTick + 6
            if (!bot.combat.prayers.isActive(protection)) output.clickButton(protection.button)
        } else if (bot.world.currentTick > protectedUntil)
            PROTECTION.filter { bot.combat.prayers.isActive(it) }.forEach { output.clickButton(it.button) }

        if (bot.health <= bot.totalHealth - 20 && tick >= nextFood) {
            val food = bot.inventory.withIndex().firstOrNull { (Food.ID_TO_FOOD[it.value?.id]?.heal ?: 0) >= 16 }
            if (food != null && handler.inventory.clickItem(1, food.value!!.id, food.index)) nextFood = tick + 3
        }
        if (bot.prayer.level <= 25 && tick >= nextPotion) {
            val potion = bot.inventory.withIndex().firstOrNull { it.value?.id in PRAYER_DOSES }
            if (potion != null && handler.inventory.clickItem(1, potion.value!!.id, potion.index)) nextPotion = tick + 3
        }
    }

    override suspend fun run(): Boolean {
        val controller = cave ?: return collectRewards()
        if (bot.state != EntityState.ACTIVE || !bot.isAlive) return true
        val monsters = enemies()
        maintain(monsters)
        val status = monsters.map { it to it.health }
        if (status != observed || controller.wave != observedWave) {
            observed = status; observedWave = controller.wave
            data.lastProgress = System.currentTimeMillis()
        }
        if (System.currentTimeMillis() >= data.deadline || System.currentTimeMillis() - data.lastProgress > 180_000 ||
            !rangedReady() || bot.prayer.level <= 5 && PRAYER_DOSES.none { bot.inventory.contains(it) } ||
            bot.health < 40 && !handler.inventory.hasAnyFood(minimumHeal = 16)) data.retreating = true
        if (data.retreating) return leave()
        if (bot.dialogues != null) output.sendContinueDialogue()
        // Ket-Zek, like Jad, can melee through our magic protection when we stand next to its body.
        val closeThreat = monsters.filter { it.id in 2743..2745 && JadHealers.gap(bot, it) < 2 }
            .maxByOrNull { it.id }
        if (closeThreat != null) {
            bot.combat.target = null
            bot.navigator.cancel()
            bot.walking.clear()
            handler.combat.stepAwayFrom(closeThreat) { bot.dynamicMap.contains(it) }
        } else {
            val healer = monsters.filterIsInstance<JadHealers.YtHurKot>().filter { !it.provoked && it.isViewableFrom(bot) }
                .minByOrNull { it.position.computeLongestDistance(bot.position) }
            val target = healer ?: monsters.filter { it.id != JadHealers.HEALER && it.isViewableFrom(bot) }
                .minWithOrNull(compareBy<Npc> { priority(it.id) }.thenBy { it.position.computeLongestDistance(bot.position) })
            if (target != null && bot.world.currentTick >= nextAttack &&
                (bot.combat.target !== target || !bot.combat.inCombat())) {
                // A native attack click owns approach/pursuit. Never wait for it while Jad can start another tell.
                output.sendNpcInteraction(3, target)
                nextAttack = bot.world.currentTick + 3
            }
        }
        delay(200)
        return false
    }

    /** Continues attending while the ordinary exit interaction navigates around cave rocks. */
    private suspend fun leave(): Boolean {
        bot.combat.target = null
        val exit = bot.world.locator.findObjects(bot, 64) { it.id == FightCave.EXIT }.firstOrNull()
        val left = withTimeoutOrNull(30_000) {
            coroutineScope {
                val walk = async(start = CoroutineStart.UNDISPATCHED) {
                    handler.interactions.interact(1, exit) { cave != null && isActionSafe() }
                }
                try {
                    while (!walk.isCompleted && cave != null) { maintain(enemies()); delay(200) }
                    walk.await()
                    while (cave != null) delay(100)
                    true
                } finally { walk.cancelAndJoin() }
            }
        } == true
        if (left) return true
        // Normal home command is the bounded fallback, with the controller forfeiting the run normally.
        if (++exitFailures >= 3) output.sendCommand("home")
        delay(600)
        return cave == null
    }

    private suspend fun collectRewards(): Boolean {
        if (!data.attempted) return true
        for (reward in bot.world.locator.findViewableItems(bot) {
                it.id in setOf(FightCave.FIRE_CAPE, FightCave.TOKKUL) && it.isVisibleTo(bot) &&
                    it.position.isWithinDistance(bot.position, 2)
            }) {
            if (!bot.inventory.hasSpaceFor(reward.toItem()) && bot.inventory.contains(229)) handler.inventory.dropItem(229)
            if (bot.inventory.hasSpaceFor(reward.toItem())) handler.loot.pickup(reward)
        }
        return true
    }

    override fun paused() { bot.navigator.cancel(); bot.walking.clear() }

    override suspend fun finish() {
        if (!isPaused() && data.previousRetaliate != null) {
            bot.combat.target = null
            PROTECTION.filter { bot.combat.prayers.isActive(it) }.forEach { output.clickButton(it.button) }
            bot.sendVarp(PersistentVarp.AUTO_RETALIATE, data.previousRetaliate!!)
        }
    }

    override fun snapshot(): FightCaveData = data
}
