package game.bot.scripts.skills

import api.bot.Suspendable.naturalDecisionDelay
import api.bot.Suspendable.waitFor
import api.bot.Suspendable.delay
import api.bot.action.PkRiskBudget
import api.bot.webwalk.plan.BankAccess
import api.bot.zone.SubZone
import api.bot.zone.Zone
import api.combat.death.PlayerDeathItems
import io.luna.game.model.mob.bot.Bot
import api.bot.script.BotScriptData
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import engine.bot.gear.BotGearLocator
import engine.controllers.Controllers.inWilderness
import game.content.abyss.*
import game.content.sailing.Entrana
import game.player.item.consume.food.Food
import game.skill.mining.Pickaxe
import game.skill.woodcutting.cutTree.Axe
import game.skill.runecrafting.craftRune.CraftableRune
import game.skill.runecrafting.enterAltar.Altar
import game.skill.runecrafting.essencePouch.EssencePouch
import io.luna.game.model.item.Item
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.GroundItem
import io.luna.game.model.EntityState
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.OptionDialogue
import io.luna.Luna
import io.luna.game.model.`object`.GameObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable

/**
 * Crafts runes at a specific Runecrafting altar.
 *
 * It withdraws the required essence and talisman, finds the inside altar object, and interacts with it until the
 * inventory and owned, level-eligible essence pouches run out of essence. Pouches are filled through their native item
 * options at the bank, followed by another loose-essence withdrawal. Their contents remain native persisted player
 * attributes, so pausing or restarting never fabricates, clears, or duplicates stored essence.
 *
 * @param bot The bot running this script.
 * @param altar The altar this script crafts runes at.
 * @param duration How long this script should run before completing normally.
 * @param viaAbyss Whether trips use the native Wilderness mage, an outer-ring passage and an altar rift. This mode
 * banks worn gear, carries owned food/tools, budgets the forced skull and prayer drain, and repairs pouches en route.
 * @author lare96
 */
class CraftRuneBotScript(bot: Bot, val altar: Altar, duration: Duration, val viaAbyss: Boolean = false) :
    InventoryBotScript(bot, duration, mutableListOf(altar.zone)) {

    companion object {

        /**
         * The item id for normal rune essence.
         */
        const val RUNE_ESSENCE = 1436

        /**
         * The item id for pure essence.
         */
        const val PURE_ESSENCE = 7936

        /**
         * Serializable data for restoring a [CraftRuneBotScript].
         *
         * This stores the selected [altar] and the shared production retry state. Older saves default the counters to zero.
         *
         * @author lare96
         */
        class CraftRuneData : InventoryScriptData() {

            /**
             * The altar this script should craft runes at.
             */
            var altar = Altar.AIR

            /**
             * The chosen entrance persists across pauses and logouts; older saves retain ordinary altar trips.
             */
            var viaAbyss = false

            override fun load(data: JsonObject) {
                super.load(data)
                altar = Altar.valueOf(data.get("altar").asString)
                viaAbyss = data.get("viaAbyss")?.asBoolean ?: false
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("altar", altar.name)
                data.addProperty("viaAbyss", viaAbyss)
            }
        }
    }

    /**
     * Creates a rune-crafting script from saved script data.
     *
     * @param bot The bot running this script.
     * @param data The saved rune-crafting script data.
     */
    constructor(bot: Bot, data: CraftRuneData) : this(bot, data.altar, data.duration, data.viaAbyss) {
        restoreInventoryState(data)
    }

    override val trainingSkill = SKILL_RUNECRAFTING

    override val verifiedProductionWithdrawals = true

    override val actionSafety = if (viaAbyss) ActionSafety.COMBAT else ActionSafety.NON_COMBAT

    override val allowsCombatResponse: Boolean get() = !viaAbyss

    override fun allowsLoot(item: GroundItem): Boolean = !viaAbyss

    /**
     * Native attack suppression is retained during pauses and restored when this activity finally exits.
     */
    private var guardsInstalled = false
    private var previousCombatDisabled = false

    /**
     * Only dialogues opened for the Dark Mage belong to this script's cleanup.
     */
    private var ownsDialogue = false

    override suspend fun beforeInventoryInit(): Boolean {
        if (!viaAbyss) return true
        if (!guardsInstalled) {
            previousCombatDisabled = bot.combat.isDisabled
            bot.combat.isDisabled = true
            guardsInstalled = true
        }
        bot.combat.target = null
        return if (inAbyss() || bot.inWilderness() || bot.combat.inCombat()) retreat() else true
    }

    override suspend fun equipment(): BotGearLocator? {
        if (!viaAbyss) return super.equipment()
        if (!handler.banking.travelToBankOpen() || !handler.banking.depositInventory() ||
            !handler.equipment.unequipAll() || !handler.banking.depositInventory()) stop()
        return null
    }

    /**
     * The cached inside altar object.
     *
     * This is resolved from [CraftableRune.insideAltarObject] and cleared when the script is paused.
     */
    private var altarObject: GameObject? = null

    /**
     * The rune being crafted by this script.
     *
     * This is resolved from [altar] during banking so execution can use the matching inside altar object.
     */
    private var rune: CraftableRune? = null

    /**
     * The single essence type selected for this trip, including compatible contents already held in owned pouches.
     */
    private var essence: Int? = null

    /**
     * Pouches selected afresh at each bank visit; no pouch or contents are generated for this activity.
     */
    private var pouches: List<EssencePouch> = emptyList()

    /**
     * Counts compatible native pouch contents, including banked pouches when planning a resumed trip.
     */
    private fun storedEssence(id: Int): Int = EssencePouch.entries.filter {
        it.level <= bot.runecrafting.staticLevel && it.isOwnedBy(bot) && bot.attributes()[it.essenceId] == id
    }.sumOf { bot.attributes()[it.essence] }

    /**
     * Prevents banking between altar batches while any carried pouch still contains usable essence.
     */
    private fun hasEssence(): Boolean = essence?.let { id ->
        bot.inventory.contains(id) || pouches.any { pouch ->
            pouch.ids.any { bot.inventory.contains(it) } && bot.attributes()[pouch.essenceId] == id &&
                    bot.attributes()[pouch.essence] > 0
        }
    } == true

    override fun withdraw(): List<Item> {
        rune = CraftableRune.ALTAR_TO_RUNE[altar]
        val craftingRune = rune
        if (craftingRune == null || bot.runecrafting.level < craftingRune.level) {
            bot.log("No rune mapping for altar [$altar].")
            stop()
            return listOf()
        }

        val requiresPureEssence = craftingRune.level > 20
        essence = when {
            requiresPureEssence && bot.itemTracker.count(PURE_ESSENCE).toLong() + storedEssence(PURE_ESSENCE) > 0 -> PURE_ESSENCE
            requiresPureEssence -> null
            bot.itemTracker.count(RUNE_ESSENCE).toLong() + storedEssence(RUNE_ESSENCE) > 0 -> RUNE_ESSENCE
            bot.itemTracker.count(PURE_ESSENCE).toLong() + storedEssence(PURE_ESSENCE) > 0 -> PURE_ESSENCE
            else -> null
        }

        if (essence == null) {
            bot.log("No valid essence available for $craftingRune.")
            stop()
            return listOf()
        }

        if (!viaAbyss && !bot.itemTracker.contains(altar.talisman)) {
            // Emergency spawn talisman if needed.
            bot.bank.add(altar.talisman)
        }

        pouches = EssencePouch.entries.filter { pouch ->
            pouch.level <= bot.runecrafting.staticLevel && pouch.isOwnedBy(bot) &&
                    (bot.attributes()[pouch.essence] == 0 || bot.attributes()[pouch.essenceId] == essence) &&
                    (viaAbyss || pouch.capacity(bot.attributes()[pouch.wear]) > 0)
        }
        val tools = (if (viaAbyss) abyssSupplies() else listOf(Item(altar.talisman))) + pouches.map { pouch ->
            Item(pouch.ids.first { bot.itemTracker.contains(it) })
        }
        if (isTerminated()) return emptyList()
        val amount = minOf(bot.itemTracker.count(essence!!), bot.inventory.capacity() - tools.sumOf { it.amount })
        return tools + if (amount > 0) listOf(Item(essence!!, amount)) else emptyList()
    }

    override fun bankWithdraw(): List<Item> = withdraw()

    override suspend fun withdrawBankItems(items: List<Item>): Boolean = withTimeoutOrNull(90.seconds) {
        if (!super.withdrawBankItems(items)) return@withTimeoutOrNull false
        val id = essence ?: return@withTimeoutOrNull false
        for (pouch in pouches) {
            if (!isActionSafe(ActionSafety.NON_COMBAT)) return@withTimeoutOrNull false
            val held = bot.attributes()[pouch.essence]
            val wear = bot.attributes()[pouch.wear]
            val capacity = pouch.capacity(wear)
            // Ordinary altar trips cannot repair. Don't fill across a degradation boundary or destroy a worn pouch.
            if (held >= capacity || pouch.capacity(wear + (capacity - held) * 3) < capacity) continue
            if (!refillEssence(id)) return@withTimeoutOrNull false
            if (!bot.inventory.contains(id)) continue
            val pouchId = pouch.ids.firstOrNull { bot.inventory.contains(it) } ?: return@withTimeoutOrNull false
            if (!handler.widgets.clickCloseInterface() || !isActionSafe(ActionSafety.NON_COMBAT) ||
                !handler.inventory.clickItem(1, pouchId) ||
                !waitFor(5.seconds) { bot.attributes()[pouch.essence] > held }) return@withTimeoutOrNull false
        }
        refillEssence(id) && hasEssence() && (!viaAbyss || withinAbyssBudget())
    } == true

    /**
     * Tops up only free inventory slots after native pouch filling; exhausted bank stock permits a final partial trip.
     */
    private suspend fun refillEssence(id: Int): Boolean {
        if (!isActionSafe(ActionSafety.NON_COMBAT)) return false
        val amount = minOf(bot.inventory.computeRemainingSize(), bot.bank.computeAmountForId(id))
        if (amount <= 0) return true
        if (!handler.banking.travelToBankOpen()) return false
        handler.banking.clickBankingMode(false)
        val before = bot.inventory.computeAmountForId(id)
        return handler.banking.withdraw(Item(id, amount)) && bot.inventory.computeAmountForId(id) == before + amount
    }

    override suspend fun onInventoryBankRequested(): Boolean = requestProductionBank(hasEssence())

    override fun bankParent(zone: SubZone): Zone = if (viaAbyss) Zone.EDGEVILLE else super.bankParent(zone)

    override suspend fun onExecuteInZone(): Boolean {
        val zone = activeZone!!
        val craftingRune = rune
        if (craftingRune == null) {
            bot.log("Invalid rune.")
            stop()
            return true
        }
        if(essence == null) {
            bot.log("Invalid essence.")
            stop()
            return true
        }

        if (!bot.inventory.contains(essence!!)) {
            val pouch = pouches.firstOrNull { bot.attributes()[it.essence] > 0 &&
                    bot.attributes()[it.essenceId] == essence && it.ids.any { id -> bot.inventory.contains(id) } }
            if (pouch == null) {
                forceBanking = true
                return true
            }
            val pouchId = pouch.ids.first { bot.inventory.contains(it) }
            // Pouch emptying consumes no input id, so use stored essence as its progress check.
            val held = bot.attributes()[pouch.essence]
            if (!handler.widgets.clickCloseInterface() || !handler.inventory.clickItem(2, pouchId) ||
                !waitFor(5.seconds) { bot.attributes()[pouch.essence] < held }) stop()
            return true
        }

        if (altarObject == null) {
            altarObject = world.locator
                .findObjects(zone.area.centerPosition, zone.area.tileRadius) { it.id == craftingRune.insideAltarObject }
                .firstOrNull()
        }

        bot.log("Attempting to interact with Runecrafting altar.")
        val altarObj = altarObject
        if (altarObj == null) {
            bot.log("No Runecrafting altar object found in zone area.")
            stop()
            return true
        }

        attemptProduction(essence!!, dexterityDelay = false) {
            handler.interactions.interact(1, altarObj)
        }

        bot.naturalDecisionDelay()
        return true
    }

    override fun snapshot(): BotScriptData {
        val data = CraftRuneData()
        saveInventoryState(data)
        data.altar = altar
        data.viaAbyss = viaAbyss
        return data
    }

    override fun onPaused() {
        altarObject = null
        if (ownsDialogue) {
            bot.overlays.closeWindows()
            ownsDialogue = false
        }
    }

    /**
     * Packs four owned healing foods and optional owned passage tools. Law trips leave prohibited tools banked.
     * No armour is required, so tool-free eyes and gaps remain available for every altar.
     */
    private fun abyssSupplies(): List<Item> {
        val food = Food.values().filter { it.heal >= 8 && bot.itemTracker.count(it.id) >= 4 }
            .maxByOrNull { it.heal }
        if (food == null || bot.health < 30) {
            stop()
            return emptyList()
        }
        val tools = listOfNotNull(
            Pickaxe.values().filter { it.level <= bot.mining.level && bot.itemTracker.contains(it.id) }
                .maxByOrNull { it.level }?.id,
            Axe.values().filter { it.level <= bot.woodcutting.level && bot.itemTracker.contains(it.id) }
                .maxByOrNull { it.level }?.id,
            ObstacleAttempt.TINDERBOX.takeIf { bot.itemTracker.contains(it) }
        ).distinct().map { Item(it) }.filter { altar != Altar.LAW || !forbiddenOnLawRift(it) }
        return listOf(Item(food.id, 4)) + tools
    }

    /**
     * Uses the law rift's native Entrana search policy, including its hand-armour exception.
     */
    private fun forbiddenOnLawRift(item: Item): Boolean =
        Entrana.isForbidden(item) && item.equipDef?.index != Equipment.HANDS

    /**
     * Budgets the entire carried loss before entry, since the mage applies a skull and drains protection prayer.
     * Skillers can Runecraft without acquiring a PK identity; their personality still bounds the same 5–25% risk.
     */
    private fun withinAbyssBudget(): Boolean {
        val share = PkRiskBudget.basisPoints(bot.personality.confidence, bot.personality.intelligence,
            bot.preferences.activities[io.luna.game.model.mob.bot.brain.BotActivity.PKING] ?: 0.0)
        return PkRiskBudget.assessor(bot, PlayerDeathItems.losesItems(bot, Luna.settings().game().betaMode()),
            shareOverride = share, keepCountOverride = 0)(
            bot.inventory.filterNotNull(), bot.equipment.filterNotNull(), false).withinBudget
    }

    /**
     * Uses exact supplied containment rather than cached subzones, which update on the movement tick.
     */
    private fun inAbyss(): Boolean = bot.position.z == 0 && SubZone.ABYSS.area.contains(bot.position)

    /**
     * Rechecked while approaching native interactions and waiting for their effects. NPC aggression does not turn
     * a Runecrafting trip into combat training; player attacks, low health or excess loss abort the trip.
     */
    private fun journeySafe(): Boolean = bot.state == EntityState.ACTIVE && bot.isAlive && bot.healthPercent >= 45 &&
        !(bot.combat.inCombat() && handler.combat.opponent() is Player)

    override suspend fun beforeZoneWork(): Boolean {
        if (!viaAbyss) return false
        if (bot.healthPercent < 70) handler.inventory.eatAnyFood(minimumHeal = 8)
        if (journeySafe() && withinAbyssBudget()) return false
        retreat()
        stop()
        return true
    }

    override suspend fun travelToWorkZone(zone: SubZone, bank: BankAccess): Boolean {
        if (!viaAbyss) return super.travelToWorkZone(zone, bank)
        if (zone.area.contains(bot.position)) return true
        val arrived = withTimeoutOrNull(180.seconds) {
            if (!journeySafe() || !withinAbyssBudget()) return@withTimeoutOrNull false
            // Always approach the mage through the configured travel system; never teleport directly into the Abyss.
            if (!inAbyss()) {
                // The prepared pack fills the inventory. Prevent travel from banking it to make room for jewellery.
                if (!handler.webWalkTo(zone = SubZone.ABYSS_ENTRY_MAGE, bank = BankAccess.NONE) || !journeySafe() ||
                    !hasEssence() || !withinAbyssBudget()) return@withTimeoutOrNull false
                val mage = world.locator.findNpcs(SubZone.ABYSS_ENTRY_MAGE.inside, 12) { it.id == 2257 }.firstOrNull()
                if (!handler.interactions.interact(3, mage) { journeySafe() } ||
                    !waitFor(10.seconds) { (inAbyss() && !bot.isLocked) || !journeySafe() } ||
                    !inAbyss() || bot.isLocked) return@withTimeoutOrNull false
            }
            if (!crossPassage() || !repairPouches() || !journeySafe() || !withinAbyssBudget()) return@withTimeoutOrNull false
            if (altar == Altar.LAW && (bot.inventory.filterNotNull() + bot.equipment.filterNotNull())
                    .any { forbiddenOnLawRift(it) }) return@withTimeoutOrNull false
            val rift = AbyssRift.valueOf(altar.name)
            val obj = world.locator.findObjects(SubZone.ABYSS.inside, 64) { it.id == rift.id }.firstOrNull()
            handler.interactions.interact(1, obj) { journeySafe() } && waitFor(5.seconds) {
                zone.area.contains(bot.position) || !journeySafe()
            } && zone.area.contains(bot.position)
        } == true
        if (!arrived) {
            retreat()
            stop()
        }
        return arrived
    }

    /**
     * Chooses a usable native morph, favouring open passages and stronger obstacle skills, then proximity.
     * Each unsuccessful attempt consumes the shared script's configured retry allowance.
     */
    private suspend fun crossPassage(): Boolean {
        repeat(maxFailures) {
            if (!journeySafe()) return false
            val options = world.locator.findObjects(SubZone.ABYSS.inside, 64) { obj ->
                AbyssPassage.entries.any { it.id == obj.id }
            }.mapNotNull { obj ->
                val score = when (ObstacleAttempt.obstacleFor(bot, obj)) {
                    AbyssObstacle.PASSAGE -> 1000
                    AbyssObstacle.ROCK -> if (Pickaxe.computePickType(bot) != null) bot.mining.level else -1
                    AbyssObstacle.TENDRILS -> if (Axe.computeAxeType(bot) != null) bot.woodcutting.level else -1
                    AbyssObstacle.BOIL -> if (bot.inventory.contains(ObstacleAttempt.TINDERBOX)) bot.firemaking.level else -1
                    AbyssObstacle.EYES -> bot.thieving.level
                    AbyssObstacle.GAP -> bot.agility.level
                    else -> -1
                }
                if (score < 0) null else obj to score
            }.sortedWith(compareByDescending<Pair<GameObject, Int>> { it.second }
                .thenBy { bot.position.computeLongestDistance(it.first.position) })
            val obj = options.firstOrNull()?.first ?: return false
            val passage = AbyssPassage.entries.first { it.id == obj.id }
            if (handler.interactions.interact(1, obj) { journeySafe() } && waitFor(20.seconds) {
                    (!bot.isLocked && bot.position.isWithinDistance(passage.inner, 1)) || !journeySafe()
                } && !bot.isLocked && bot.position.isWithinDistance(passage.inner, 1)) return true
        }
        return false
    }

    /**
     * Requests repairs through the Dark Mage's ordinary dialogue. Repairing even light wear here prevents the next
     * bank fill from destroying a pouch; native dialogue owns all item-id and attribute changes.
     */
    private suspend fun repairPouches(): Boolean {
        if (!EssencePouch.needsRepair(bot)) return true
        val mage = world.locator.findNpcs(SubZone.ABYSS.inside, 64) { it.id == 2262 }.firstOrNull() ?: return false
        ownsDialogue = true
        try {
            if (!handler.interactions.interact(1, mage) { journeySafe() }) return false
            return withTimeoutOrNull(60.seconds) {
                while (journeySafe() && EssencePouch.needsRepair(bot)) {
                    val options = bot.overlays[OptionDialogue::class]
                    if (options != null) {
                        val response = listOf("I need your help with something...", "Can you repair my pouches?")
                            .firstOrNull { it in options.options } ?: return@withTimeoutOrNull false
                        if (!handler.widgets.clickDialogueOption(response)) return@withTimeoutOrNull false
                    }
                    delay(1.seconds)
                }
                journeySafe() && !EssencePouch.needsRepair(bot)
            } == true
        } finally {
            bot.overlays.closeWindows()
            ownsDialogue = false
        }
    }

    /**
     * Uses native escape commands/checks and the existing Wilderness walker fallback, never direct player movement.
     */
    private suspend fun retreat(): Boolean = withTimeoutOrNull(30.seconds) {
        if (!awaitActionSafety(ActionSafety.COMBAT)) return@withTimeoutOrNull false
        if (bot.inWilderness()) return@withTimeoutOrNull handler.combat.fleeWilderness()
        if (inAbyss() || bot.combat.inCombat()) {
            bot.output.sendCommand("home")
            return@withTimeoutOrNull waitFor(10.seconds) {
                !inAbyss() && !bot.inWilderness() && !bot.combat.inCombat()
            }
        }
        true
    } == true

    override suspend fun finish() {
        if (isPaused()) return
        try {
            if (viaAbyss && bot.state == EntityState.ACTIVE && bot.isAlive) {
                withContext(NonCancellable) { retreat() }
            }
        } finally {
            if (guardsInstalled) {
                bot.combat.isDisabled = previousCombatDisabled
                guardsInstalled = false
            }
        }
    }
}
