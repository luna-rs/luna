package game.bot.scripts.skills

import api.bot.script.InventoryBotScript
import api.bot.action.BotFarmingActionHandler
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.webwalk.plan.BankAccess
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import engine.bot.gear.BotGearLocator
import game.content.sailing.Entrana
import game.skill.farming.Farming
import game.skill.farming.allotment.AllotmentPatchLocation
import game.skill.farming.allotment.AllotmentSeed
import game.skill.farming.bush.BushPatch
import game.skill.farming.bush.BushPatchLocation
import game.skill.farming.flower.FlowerPatch
import game.skill.farming.flower.FlowerPatchLocation
import game.skill.farming.flower.FlowerSeed
import game.skill.farming.herb.HerbPatchLocation
import game.skill.farming.hops.HopsPatchLocation
import game.skill.farming.patch.CompostType
import game.skill.farming.patch.Crop
import game.skill.farming.patch.FarmingPatch
import game.skill.farming.patch.HealthCheckPatch
import game.skill.farming.patch.PatchLocation
import game.skill.farming.patch.PickablePatch
import game.skill.farming.special.BelladonnaPatch
import game.skill.farming.special.SpecialPatchLocation
import game.skill.farming.tree.AbstractTreePatch
import game.skill.farming.tree.ChopPatchTreeAction
import game.skill.farming.tree.TreePatchLocation
import game.skill.farming.tree.TreeSapling
import game.skill.farming.fruitTree.FruitTreePatch
import game.skill.farming.fruitTree.FruitTreePatchLocation
import game.skill.woodcutting.cutTree.Axe
import io.luna.game.model.EntityState
import io.luna.game.model.item.Equipment
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * Visits native farming plots through normal player packets, sharing the same inventory and travel lifecycle.
 * Dead crops are cleared, weeds raked, disease cured and mature crops harvested before replanting owned seeds.
 * Owned compost and charged watering cans are optional. Native handlers alone consume supplies, change patch
 * state and award experience. The visit ends when no useful work remains; it never waits for crops to grow.
 *
 * The inherited lifecycle supplies travel, verified banking and persistent failure budgets. Harvesting resumes
 * after banking a full inventory. Saved visits retain a patch key and seed choice, then re-read native patch state
 * and stock. Scarecrows and flowers protecting a growing allotment are preserved. Entrana visits require the
 * native equipment restriction. Trees use native pruning, one-time health checks, chopping and stump digging.
 * Fruit is picked before considering replacement; profit visits preserve healthy trees for produce regrowth.
 * Training can replace mature trees only with an owned, plantable sapling and usable tools. Raw seeds must first
 * become saplings through native pot preparation, which is a separate activity. Bushes and cactus have health checked
 * once, then are picked and left standing for native produce regrowth. Mushrooms and belladonna use normal finite
 * harvests. Belladonna visits preserve existing hand gear or equip suitable owned hand gear through the equipment API.
 * Stored tools and fertilizer are retrieved through native leprechaun exchanges before the usual banking lifecycle.
 * Completed plot visits can put carried farming tools away with a nearby leprechaun; seeds and harvests stay carried.
 *
 * @param bot The farmer.
 * @property location Native persistent patch identity, independent of its changing appearance.
 * @property seedId Optional owned seed selected for replanting; zero means tend or harvest only.
 * @param duration Maximum visit duration, including travel and banking.
 * @property replantTrees Whether to rotate mature trees when another owned sapling can be planted.
 * @author lare96
 */
class FarmingBotScript(
    bot: Bot,
    val location: PatchLocation,
    val seedId: Int,
    duration: Duration,
    val replantTrees: Boolean = false
) : InventoryBotScript(bot, duration, mutableListOf(SITES.getValue(location))) {
    override val trainingSkill = SKILL_FARMING
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * Native farming plots and their shared activity areas, using each area's parent travel and banking hub.
         */
        val SITES: Map<PatchLocation, SubZone> = linkedMapOf(
            AllotmentPatchLocation.FALADOR_NW to SubZone.FALADOR_FARM,
            AllotmentPatchLocation.FALADOR_SE to SubZone.FALADOR_FARM,
            HerbPatchLocation.FALADOR to SubZone.FALADOR_FARM,
            FlowerPatchLocation.FALADOR to SubZone.FALADOR_FARM,
            AllotmentPatchLocation.CATHERBY_NORTH to SubZone.CATHERBY_FARM,
            AllotmentPatchLocation.CATHERBY_SOUTH to SubZone.CATHERBY_FARM,
            HerbPatchLocation.CATHERBY to SubZone.CATHERBY_FARM,
            FlowerPatchLocation.CATHERBY to SubZone.CATHERBY_FARM,
            AllotmentPatchLocation.ARDOUGNE_NORTH to SubZone.ARDOUGNE_FARM,
            AllotmentPatchLocation.ARDOUGNE_SOUTH to SubZone.ARDOUGNE_FARM,
            HerbPatchLocation.ARDOUGNE to SubZone.ARDOUGNE_FARM,
            FlowerPatchLocation.ARDOUGNE to SubZone.ARDOUGNE_FARM,
            AllotmentPatchLocation.PORT_PHASMATYS_NORTH to SubZone.PORT_PHASMATYS_FARM,
            AllotmentPatchLocation.PORT_PHASMATYS_SOUTH to SubZone.PORT_PHASMATYS_FARM,
            HerbPatchLocation.PORT_PHATASMYS to SubZone.PORT_PHASMATYS_FARM,
            FlowerPatchLocation.PORT_PHASMATYS to SubZone.PORT_PHASMATYS_FARM,
            HopsPatchLocation.YANILLE to SubZone.YANILLE_HOPS,
            HopsPatchLocation.ENTRANA to SubZone.ENTRANA_HOPS,
            HopsPatchLocation.LUMBRIDGE to SubZone.LUMBRIDGE_HOPS,
            HopsPatchLocation.MCGRUBORS_WOOD to SubZone.MCGRUBORS_HOPS,
            TreePatchLocation.TAVERLEY to SubZone.TAVERLEY_TREE_PATCH,
            TreePatchLocation.FALADOR to SubZone.FALADOR_TREE_PATCH,
            TreePatchLocation.VARROCK to SubZone.VARROCK_TREE_PATCH,
            TreePatchLocation.LUMBRIDGE to SubZone.LUMBRIDGE_TREE_PATCH,
            FruitTreePatchLocation.GNOME_STRONGHOLD to SubZone.GNOME_STRONGHOLD_FRUIT_PATCH,
            FruitTreePatchLocation.GNOME_VILLAGE to SubZone.GNOME_VILLAGE_FRUIT_PATCH,
            FruitTreePatchLocation.BRIMHAVEN to SubZone.BRIMHAVEN_FRUIT_PATCH,
            FruitTreePatchLocation.CATHERBY to SubZone.CATHERBY_FRUIT_PATCH,
            BushPatchLocation.CHAMPIONS_GUILD to SubZone.CHAMPIONS_GUILD_BUSH_PATCH,
            BushPatchLocation.RIMMINGTON to SubZone.RIMMINGTON_BUSH_PATCH,
            BushPatchLocation.ARDOUGNE to SubZone.ARDOUGNE_BUSH_PATCH,
            BushPatchLocation.ETCETERIA to SubZone.ETCETERIA_BUSH_PATCH,
            SpecialPatchLocation.CACTUS to SubZone.AL_KHARID_CACTUS_PATCH,
            SpecialPatchLocation.MUSHROOM to SubZone.CANIFIS_MUSHROOM_PATCH,
            SpecialPatchLocation.BELLADONNA to SubZone.DRAYNOR_BELLADONNA_PATCH
        )

        /**
         * Saves the native patch key, optional seed and inherited duration and retry counters.
         * Crop state and growth timestamps remain in the player's existing Farming attributes.
         *
         * @author lare96
         */
        class FarmingData : InventoryScriptData() {
            /**
             * Native location key used by Farming's own persistence.
             */
            var patchKey = AllotmentPatchLocation.FALADOR_NW.key
            /**
             * Seed chosen for this visit, or zero for tending an existing crop only.
             */
            var seedId = 0
            /**
             * Preserves the factory's training/profit replacement policy across saved visits.
             */
            var replantTrees = false
            override fun load(data: JsonObject) {
                super.load(data)
                patchKey = data.get("patchKey").asString
                seedId = data.get("seedId")?.asInt ?: 0
                replantTrees = data.get("replantTrees")?.asBoolean ?: false
            }
            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("patchKey", patchKey)
                data.addProperty("seedId", seedId)
                data.addProperty("replantTrees", replantTrees)
            }
        }
    }

    /**
     * One player interaction and its exact inventory prerequisites. A null input selects the first object option.
     * State-only operations verify their native result; long-running tree chopping can acknowledge its native action.
     *
     * @author lare96
     */
    private data class Work(val input: Item?, val tool: Int? = null, val output: Item? = null,
                            val progressCondition: () -> Boolean = { false }) {
        /**
         * Minimum carried quantities for the native handler's tool and material checks.
         */
        val supplies: List<Item> get() = listOfNotNull(input, tool?.let { Item(it) })
    }

    /**
     * Reads the native per-player plot; no script snapshot duplicates or overwrites this state.
     */
    val patch: FarmingPatch get() = Farming.patches(bot).getValue(location)

    /**
     * Restores a visit without replenishing retry budgets or advancing crop timers.
     */
    constructor(bot: Bot, data: FarmingData) : this(bot,
        SITES.keys.first { it.key == data.patchKey }, data.seedId, data.duration, data.replantTrees) {
        restoreInventoryState(data)
    }

    /**
     * Counts portable unnoted stock and exact native leprechaun storage, excluding worn equipment.
     */
    private fun owns(item: Item): Boolean = BotFarmingActionHandler.stock(bot, item.id) >= item.amount

    /**
     * Replanting is optional and requires the native seed count, current level and planting tool.
     */
    private fun plantingCrop(): Crop? = patch.findCrop(seedId)?.takeIf {
        bot.farming.level >= it.level && owns(Item(it.seedId, patch.seedsRequired(it))) && owns(Item(patch.plantTool))
    }

    /**
     * Keeps a mature protective flower while any linked, matching allotment still needs disease protection.
     */
    private fun protectsAllotment(): Boolean {
        val flower = patch as? FlowerPatch ?: return false
        val protection = flower.protection() ?: return false
        return AllotmentPatchLocation.entries.filter { it.flowerPatch == location }.any {
            val allotment = Farming.patches(bot).getValue(it)
            allotment.isGrowing() && protection in ((allotment.crop as? AllotmentSeed)?.flowerProtection ?: emptySet())
        }
    }

    /**
     * Chooses the next useful action. Healthy growing crops need only optional compost or water; unavailable
     * cures leave living crops intact. Empty plots without plantable stock are not raked just for weed experience.
     */
    private fun nextWork(): Work? {
        val patch = patch
        val crop = plantingCrop()
        return when {
            patch is FlowerPatch && patch.scarecrow -> null
            patch.dead -> Work(Item(Farming.SPADE), progressCondition = { !patch.dead && !patch.isOccupied() })
            patch.diseased -> if (patch is AbstractTreePatch || patch is BushPatch) {
                listOf(Farming.MAGIC_SECATEURS, Farming.SECATEURS).firstOrNull { owns(Item(it)) }
                    ?.let { Work(Item(it), progressCondition = { !patch.diseased }) }
            } else if (owns(Item(Farming.PLANT_CURE))) Work(Item(Farming.PLANT_CURE)) else null
            patch is AbstractTreePatch && patch.harvestReady() -> treeWork(patch, crop)
            patch is HealthCheckPatch && patch.harvestReady() -> when {
                patch.canCheckHealth() -> Work(null, progressCondition = { !patch.canCheckHealth() })
                patch is PickablePatch && patch.canPick() -> Work(null, output = patch.crop?.produce)
                else -> null
            }
            patch.harvestReady() -> if (protectsAllotment()) null else {
                val planted = patch.crop ?: return null
                val amount = (planted as? FlowerSeed)?.amount(bot.farming.level) ?: planted.produce.amount
                Work(null, patch.harvestTool, Item(planted.produce.id, amount))
            }
            patch.needsRaking() -> if (crop != null) Work(Item(Farming.RAKE), output = Item(Farming.WEEDS)) else null
            patch.canApplyCompost() && (patch.hasPlant() || crop != null) -> {
                val compost = listOf(CompostType.SUPERCOMPOST, CompostType.COMPOST).firstOrNull { owns(Item(it.itemId)) }
                if (compost != null) Work(Item(compost.itemId)) else plantingOrWatering(crop)
            }
            else -> plantingOrWatering(crop)
        }
    }

    /**
     * Uses owned axes at the native Woodcutting requirement. Worn axes are left alone; inventory tools survive banking.
     */
    private fun treeAxe(): Axe? = Axe.entries.filter {
        bot.woodcutting.level >= it.level && (owns(Item(it.id)) || bot.equipment[Equipment.WEAPON]?.id == it.id)
    }
        .maxByOrNull { it.level }

    /**
     * Checks health once, picks available fruit, then optionally removes a tree for a supplied replacement.
     * Ordinary trees require their own Woodcutting level; fruit-tree removal follows the native axe-only rule.
     * Native chopping may take many unsuccessful swings, so a live native action counts as an accepted start while
     * InventoryBotScript defers further clicks until that action finishes. No tree state is changed here.
     */
    private fun treeWork(tree: AbstractTreePatch, replacement: Crop?): Work? = when {
        tree.canCheckHealth() -> Work(null, progressCondition = { tree.healthChecked })
        tree is FruitTreePatch && tree.canPick() -> Work(null, output = tree.crop?.produce)
        !replantTrees || replacement == null -> null
        tree.stump -> Work(Item(Farming.SPADE), output = (tree.crop as? TreeSapling)?.let { Item(it.roots) },
            progressCondition = { !tree.isOccupied() })
        tree.canChop() -> {
            val planted = tree.crop as? TreeSapling
            if (planted != null && bot.woodcutting.level < planted.tree.level) null else treeAxe()?.let {
                Work(null, it.id.takeIf { id -> owns(Item(id)) }, planted?.produce, progressCondition = {
                    tree.stump || bot.actions.first(ChopPatchTreeAction::class.java) != null
                })
            }
        }
        else -> null
    }

    /**
     * Uses a charged owned can after planting; a reduced-dose can remains usable on a later native growth stage.
     */
    private fun plantingOrWatering(crop: Crop?): Work? = when {
        !patch.isOccupied() && crop != null -> Work(Item(crop.seedId, patch.seedsRequired(crop)), patch.plantTool)
        patch.canWater() -> Farming.WATERING_CANS.asReversed().firstOrNull { owns(Item(it)) }?.let { Work(Item(it)) }
        else -> null
    }

    /**
     * Requires useful work and owned mandatory supplies before selecting or resuming this visit.
     */
    fun isEligible(): Boolean = (location != HopsPatchLocation.ENTRANA || !Entrana.isCarryingForbidden(bot)) &&
        (!needsGloves() || bot.equipment[Equipment.HANDS] != null || harvestGloves() != null) &&
        nextWork()?.supplies?.all(::owns) == true

    /**
     * Only picking mature, healthy belladonna needs worn hand gear; raking, planting and treatment do not.
     */
    private fun needsGloves(): Boolean = patch is BelladonnaPatch && patch.harvestReady() && !patch.diseased

    /**
     * Selects a carried or banked hand-slot item the bot meets the native equipment requirements to wear.
     * This follows belladonna's actual hand-slot rule rather than inventing a separate glove whitelist.
     */
    private fun harvestGloves(): Int? = sequenceOf(bot.inventory, bot.bank).flatMap { it.asSequence() }
        .filterNotNull().firstOrNull { item ->
            item.equipDef?.let { it.index == Equipment.HANDS && it.meetsAllRequirements(bot) } == true
        }?.id

    /**
     * Packs the next action plus an optional replant and care supplies, leaving ample room for native harvests.
     * Rebuilt after every deposit so depleted stock and crops changed during travel cannot cause repeated bank trips.
     */
    fun visitSupplies(): List<Item> {
        val work = nextWork() ?: return emptyList()
        return buildList {
            addAll(work.supplies)
            plantingCrop()?.let {
                if (patch.dead || !patch.isOccupied() ||
                    patch.harvestReady() && patch !is HealthCheckPatch && (patch !is AbstractTreePatch || replantTrees)) {
                    add(Item(it.seedId, patch.seedsRequired(it)))
                    add(Item(patch.plantTool))
                    if (patch.needsRaking()) add(Item(Farming.RAKE))
                    if (patch is AbstractTreePatch && patch.harvestReady()) treeAxe()?.let { axe ->
                        if (owns(Item(axe.id))) add(Item(axe.id))
                    }
                }
            }
            if (needsGloves() && bot.equipment[Equipment.HANDS] == null) harvestGloves()?.let { add(Item(it)) }
            if (!protectsAllotment()) {
                listOf(CompostType.SUPERCOMPOST.itemId, CompostType.COMPOST.itemId)
                    .firstOrNull { owns(Item(it)) }?.let { add(Item(it)) }
                if (patch.canBeWatered) Farming.WATERING_CANS.asReversed()
                    .firstOrNull { owns(Item(it)) }?.let { add(Item(it)) }
            }
        }.groupBy { it.id }.map { (id, items) -> Item(id, items.maxOf { it.amount }) }
    }

    override suspend fun equipment(): BotGearLocator? = null
    override suspend fun beforeInventoryInit(): Boolean {
        Farming.update(bot)
        if (!isEligible()) return false
        val items = visitSupplies()
        return !BotFarmingActionHandler.needsStorage(bot, items) ||
            handler.farming.retrieve(items, SITES.getValue(location)) { isActionSafe() }
    }
    override suspend fun travelToWorkZone(zone: SubZone, bank: BankAccess): Boolean =
        (location != HopsPatchLocation.ENTRANA || !Entrana.isCarryingForbidden(bot)) && super.travelToWorkZone(zone, bank)

    override fun withdraw(): List<Item> = productionWithdraw(visitSupplies(), requestMissing = false)
    override fun bankWithdraw(): List<Item> = visitSupplies().also { if (!isEligible()) stop() }

    override suspend fun withdrawBankItems(items: List<Item>): Boolean {
        if (!super.withdrawBankItems(items)) return false
        if (!needsGloves() || bot.equipment[Equipment.HANDS] != null) return true
        val gloves = harvestGloves()?.takeIf { bot.inventory.contains(it) } ?: return false
        return withTimeoutOrNull(15_000) {
            handler.widgets.clickCloseInterface() && isActionSafe() && handler.equipment.equip(gloves) &&
                bot.equipment[Equipment.HANDS] != null
        } == true
    }

    /**
     * Tests capacity for the whole flower yield, not only its first item; other native harvests produce one at a time.
     */
    private fun canWork(work: Work): Boolean = bot.inventory.containsAll(work.supplies) &&
        (!needsGloves() || bot.equipment[Equipment.HANDS] != null) &&
        (work.output == null || bot.inventory.hasSpaceFor(work.output))

    override suspend fun onInventoryBankRequested(): Boolean = nextWork()?.let {
        requestProductionBank(canWork(it))
    } ?: false

    override suspend fun onExecuteInZone(): Boolean {
        Farming.update(bot)
        val work = nextWork() ?: run {
            handler.farming.store(SITES.getValue(location)) { isActionSafe() }
            return false
        }
        if (!productionReady(canWork(work))) return true
        val zone = SITES.getValue(location)
        val target = world.locator.findObjects(zone.inside, zone.area.tileRadius) {
            it.id == location.objectId && it.state == EntityState.ACTIVE && it.position.z == 0 && it in zone.area
        }.minByOrNull { bot.position.computeLongestDistance(it.position) }
        attemptProduction(work.input?.id ?: work.output?.id ?: Farming.SPADE,
            available = target != null, outputId = work.output?.id, progressCondition = work.progressCondition) {
            target != null && if (work.input == null) handler.interactions.interact(1, target)
            else handler.inventory.useItem(work.input.id).onObject(target)
        }
        return true
    }

    override fun snapshot() = FarmingData().also {
        saveInventoryState(it)
        it.patchKey = location.key
        it.seedId = seedId
        it.replantTrees = replantTrees
    }
}
