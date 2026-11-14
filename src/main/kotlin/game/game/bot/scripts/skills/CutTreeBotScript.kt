package game.bot.scripts.skills

import api.bot.Suspendable.naturalMicroDelay
import api.bot.Suspendable.waitFor
import api.bot.webwalk.plan.BankAccess
import api.bot.action.BotGatheringTools
import io.luna.game.model.mob.bot.Bot
import api.bot.script.BotScriptData
import api.bot.social.SocialBotScript
import engine.bot.speech.BotConversationPool.ConversationSpeech
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.skill.SkillingBotScript
import api.bot.skill.SkillingTool
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.FiremakingScriptFactory
import engine.bot.coordinator.skill.FletchingScriptFactory
import engine.bot.coordinator.skill.SkillingCoordinator.Companion.profitMode
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.woodcutting.cutTree.Axe
import game.content.randomEvents.WoodcuttingRandomEvents
import game.skill.woodcutting.cutTree.CutTreeAction
import game.skill.woodcutting.cutTree.Tree
import game.skill.woodcutting.cutTree.TreeStump
import game.skill.woodcutting.searchNest.Nest
import game.content.hardwoodGrove.HardwoodGrove
import io.luna.game.model.item.Item
import io.luna.game.model.item.shop.ShopInterface
import io.luna.game.model.Position
import io.luna.game.model.`object`.GameObject
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A [SkillingBotScript] that trains Woodcutting by chopping configured tree types inside selected zones.
 *
 * This script supplies the Woodcutting-specific pieces required by [SkillingBotScript], including valid tree object
 * ids, axe selection, emergency axe fallback, and bird nest side behavior.
 *
 * While running, the bot searches for nearby matching tree objects and interacts with them using the normal targeting
 * flow inherited from the skilling script hierarchy. It may also pick up nearby bird nests when there is enough
 * inventory space, then open them after a short natural delay.
 *
 * @param bot The bot running this script.
 * @param trees The tree types this bot is allowed to chop.
 * @param duration How long this script should continue running before completing normally.
 * @param zones The candidate zones this script may operate in.
 * @property firemakingTarget A bounded owned-log target for Firemaking preparation, or zero for ordinary Woodcutting.
 * @author lare96
 */
class CutTreeBotScript(bot: Bot, val trees: Set<Tree>, duration: Duration, zones: MutableList<SubZone>,
                       val firemakingTarget: Int = 0) :
    SkillingBotScript<GameObject>(bot, duration, zones, bot.woodcutting), SocialBotScript {

    override fun wildernessNotingItems(): Set<Int> =
        if (firemakingTarget == 0) trees.mapTo(HashSet()) { it.logId } else emptySet()

    override fun ambientConversationTopic(): ConversationSpeech = ConversationSpeech.WOODCUTTING

    override fun canSocialize(): Boolean = canTalkWhileWorking()

    override fun canTalkWhileWorking(): Boolean = canContinueConversation() &&
        bot.actions.first(CutTreeAction::class.java) != null

    override fun canContinueConversation(): Boolean = !forceBanking && !bot.inventory.isFull &&
        duration > Duration.ZERO && activeZone?.area?.contains(bot.position) == true &&
        bot.walking.isEmpty && !bot.navigator.isActive && !bot.combat.inCombat() && bot.combat.target == null

    override val targetSearchTimeout: Duration = 180.seconds

    companion object {

        /**
         * Native cooperative gems, with a twelve-gem ceiling per currency visit. Existing trading sticks cover up
         * to three entries; ordinary shop sales determine the actual proceeds and stop once one entry is funded.
         */
        private val GROVE_GEMS = listOf(1625, 1609, 1627, 1611, 1629, 1613)
        private const val GROVE_GEM_LIMIT = 12
        private const val GROVE_STOCK_TARGET = HardwoodGrove.FEE * 3

        /**
         * Requires owned currency or a bounded real gem trade. Merchant accounts retain the existing prohibition
         * on NPC-shop sales and can use the grove when they already own its entry fee.
         */
        internal fun groveSuppliesOwned(bot: Bot): Boolean =
            bot.itemTracker.count(HardwoodGrove.STICKS) >= HardwoodGrove.FEE ||
                !bot.preferences.isMerchant && GROVE_GEMS.any { bot.itemTracker.contains(it) }

        /**
         * Serializable data used to save and restore a [CutTreeBotScript].
         *
         * This extends [ZonedBotScriptData] with the configured Woodcutting tree set, allowing the script to resume with
         * the same target trees, remaining duration, and candidate zones.
         */
        class CutTreeData : ZonedBotScriptData() {

            /**
             * The tree types this script is allowed to chop.
             */
            var trees = emptySet<Tree>()

            /**
             * The Firemaking preparation stock target; old Woodcutting snapshots remain ordinary sessions.
             */
            var firemakingTarget = 0

            override fun load(data: JsonObject) {
                super.load(data)
                trees = loadEnumSet("trees", data) { Tree.valueOf(it) }
                firemakingTarget = (data.get("firemakingTarget")?.asInt ?: 0)
                    .coerceIn(0, FiremakingScriptFactory.GATHER_TARGET)
            }

            override fun save(data: JsonObject) {
                super.save(data)
                saveEnumSet("trees", data, trees)
                data.addProperty("firemakingTarget", firemakingTarget)
            }
        }
    }

    /**
     * Creates a [CutTreeBotScript] from persisted script data.
     *
     * @param bot The bot running this script.
     * @param data The saved Woodcutting script data to restore from.
     */
    constructor(bot: Bot, data: CutTreeData) :
        this(bot, data.trees, data.duration, data.zones, data.firemakingTarget)

    /**
     * The live tree object ids that match the configured [trees].
     *
     * These ids are precomputed once so [find] can quickly test nearby objects without rebuilding the target id set on
     * every search cycle.
     */
    private val treeIds = run {
        val ids = HashSet<Int>()
        for (tree in trees) {
            ids.addAll(TreeStump.ALIVE_TREE_MAP[tree])
        }
        ids
    }

    /**
     * The item id of a bird nest that was recently picked up and should be opened from the bot's inventory.
     *
     * This is used as a small handoff between the ground-item pickup and inventory-click step. It is cleared after the
     * script attempts to open the nest.
     */
    private var pickedUp: Int? = null

    /**
     * Prevents a stock-target exit and a completion callback from dispatching the same continuation twice.
     */
    private var firemakingQueued = false

    private val hardwood = trees.any { it == Tree.TEAK || it == Tree.MAHOGANY }

    override fun requirements(): Boolean {
        return treeIds.isNotEmpty() && (!hardwood || groveSuppliesOwned(bot) ||
            SubZone.TAI_BWO_WANNAI_HARDWOOD_GROVE.area.contains(bot.position))
    }

    override suspend fun onBankOpenSkilling(initial: Boolean) {
        if (!hardwood) return
        val sticks = minOf(GROVE_STOCK_TARGET, bot.bank.computeAmountForId(HardwoodGrove.STICKS))
        if (sticks > 0 && !handler.banking.withdraw(Item(HardwoodGrove.STICKS, sticks))) {
            stop()
            return
        }
        if (bot.inventory.computeAmountForId(HardwoodGrove.STICKS) >= HardwoodGrove.FEE) return
        var remaining = GROVE_GEM_LIMIT
        if (!bot.preferences.isMerchant) for (id in GROVE_GEMS) {
            val amount = minOf(remaining, bot.bank.computeAmountForId(id))
            if (amount > 0 && handler.banking.withdraw(Item(id, amount))) {
                remaining -= amount
            }
        }
        if (GROVE_GEMS.none { it in bot.inventory }) missingGroveFee()
    }

    override suspend fun beforeZoneWork(): Boolean {
        if (hardwood && !SubZone.TAI_BWO_WANNAI_HARDWOOD_GROVE.area.contains(bot.position) &&
            bot.inventory.computeAmountForId(HardwoodGrove.STICKS) < HardwoodGrove.FEE &&
            GROVE_GEMS.none { it in bot.inventory }) {
            if (groveSuppliesOwned(bot)) forceBanking = true else missingGroveFee()
        }
        return false
    }

    override suspend fun travelToWorkZone(zone: SubZone, bank: BankAccess): Boolean {
        if (zone != SubZone.TAI_BWO_WANNAI_HARDWOOD_GROVE) return super.travelToWorkZone(zone, bank)
        if (zone.area.contains(bot.position)) return true
        if (!prepareGroveFee()) return false
        // Recovery can reach the public gate's outside; the final crossing must pay through the real native gate.
        if (!handler.webWalkTo(HardwoodGrove.MURCAILY, bank = bank)) return false
        return handler.webWalkTo(zone.inside, radius = 0, bank = BankAccess.NONE, fallback = false) &&
            zone.area.contains(bot.position)
    }

    /**
     * Funds the next entry with ordinary sales to Gabooty's real cooperative, never with generated sticks or logs.
     * Each sale checks the open shop and actual inventory proceeds; interruption leaves the owned items intact.
     */
    private suspend fun prepareGroveFee(): Boolean {
        if (bot.inventory.computeAmountForId(HardwoodGrove.STICKS) >= HardwoodGrove.FEE) return true
        if (bot.preferences.isMerchant || GROVE_GEMS.none { it in bot.inventory }) return false
        if (!handler.webWalkTo(HardwoodGrove.GABOOTY)) return false
        val gabooty = world.locator.findNpcs(HardwoodGrove.GABOOTY, 8) { it.id in 2519..2521 }.firstOrNull()
            ?: return false
        if (!handler.interactions.interact(4, gabooty)) return false
        if (!waitFor(5.seconds) { bot.overlays.getOverlay(ShopInterface::class.java)?.shop?.name == HardwoodGrove.COOPERATIVE })
            return false
        val shop = bot.overlays.getOverlay(ShopInterface::class.java) ?: return false
        try {
            repeat(GROVE_GEM_LIMIT) {
                if (!isActionSafe() || bot.overlays.getOverlay(ShopInterface::class.java) !== shop)
                    return false
                if (bot.inventory.computeAmountForId(HardwoodGrove.STICKS) >= HardwoodGrove.FEE) return true
                val id = GROVE_GEMS.firstOrNull { it in bot.inventory } ?: return false
                val before = bot.inventory.computeAmountForId(HardwoodGrove.STICKS)
                if (!handler.shop.sell1(id).await() || bot.inventory.computeAmountForId(HardwoodGrove.STICKS) <= before)
                    return false
            }
            return bot.inventory.computeAmountForId(HardwoodGrove.STICKS) >= HardwoodGrove.FEE
        } finally {
            if (bot.overlays.getOverlay(ShopInterface::class.java) === shop) bot.overlays.closeWindows()
        }
    }

    /**
     * Keeps admission requests bounded and lets another activity run when no owned fee source remains.
     */
    private fun missingGroveFee() {
        bot.preferences.addWantedItem(HardwoodGrove.STICKS, GROVE_STOCK_TARGET)
        stop()
    }

    override suspend fun onExecuteSkilling(searching: Boolean) {
        if (firemakingTarget > 0 && trees.sumOf { bot.itemTracker.count(it.logId).toLong() } >= firemakingTarget) {
            queueFiremaking()
            bot.actions.first(CutTreeAction::class.java)?.interrupt()
            stop()
            return
        }
        // Search nearby for birds nests and loot if we have enough inventory space.
        if (bot.walking.isEmpty && bot.inventory.computeRemainingSize() >= 2) {
            val nests = world.locator.findItems(bot, 5) { it.view.isViewableFor(bot) && Nest.isNest(it.id) }
            for (item in nests) {
                if (handler.interactions.interact(1, item)) {
                    pickedUp = item.id
                    bot.naturalMicroDelay()
                    break
                }
            }

            if (pickedUp != null) {
                handler.inventory.clickItem(1, pickedUp!!)
                pickedUp = null
            }
        }
    }

    override suspend fun find(searchBase: Position, searchRadius: Int): MutableCollection<GameObject> {
        // The radius search can extend beyond a rectangular stand into its neighbouring maple stands.
        val mapleArea = if (Tree.MAPLE in trees) activeZone?.area else null
        return world.locator.findObjects(searchBase, searchRadius) {
            it.id in treeIds && (mapleArea == null || mapleArea.contains(it.position))
        }
    }

    override fun tools(): SortedSet<SkillingTool> =
        Axe.VALUES.values
            .mapTo(TreeSet()) { SkillingTool(it.id, it.level) }


    override fun emergencyTool(): SkillingTool = SkillingTool(Axe.BRONZE.id, Axe.BRONZE.level)

    override suspend fun recoverMissingTool() {
        BotGatheringTools.recoverHead(bot, WoodcuttingRandomEvents.HANDLE,
            WoodcuttingRandomEvents.Parts.entries.associate { it.head to it.axe.id }, trees.mapTo(mutableSetOf()) { it.logId })
    }

    override fun levelRequired(): Int = trees.maxOfOrNull { it.level } ?: 0

    override fun snapshot(): BotScriptData {
        val data = CutTreeData()
        data.duration = duration
        data.zones = originalZones.toMutableList()
        data.trees = trees
        data.firemakingTarget = firemakingTarget
        return data
    }

    override suspend fun finish() {
        if (firemakingTarget > 0) bot.actions.first(CutTreeAction::class.java)?.interrupt()
    }

    override suspend fun completed() {
        if (firemakingTarget > 0) {
            queueFiremaking()
            return
        }
        // Chance to burn the logs we chopped, more likely for bots that like Firemaking. Burning logs never makes
        // money, so bots chopping for profit keep them.
        val firemakingChance = if (SKILL_FIREMAKING in bot.preferences.skills) 0.5 else 0.2
        if (!bot.profitMode && rand(firemakingChance)) {
            val script = FiremakingScriptFactory.getBurnScript(bot)
            if (script != null) {
                bot.scriptStack.pushTail(script, 2)
                return
            }
        }
        if (rand(bot.personality.intelligence) || bot.personality.isDextrous) {
            val script =
                FletchingScriptFactory.getScript(bot, bot.fletching.staticLevel, mutableListOf(), randBoolean())
            bot.scriptStack.pushTail(script, 2)
        }
    }

    /**
     * Burns actual owned fuel with the remaining session time; failed gathering cannot recursively gather again.
     */
    private fun queueFiremaking() {
        if (firemakingQueued) return
        val remaining = remainingDuration()
        if (remaining <= Duration.ZERO) return
        firemakingQueued = true
        FiremakingScriptFactory.getBurnScript(bot, duration = remaining)?.let { bot.scriptStack.softPushHead(it) }
    }
}
