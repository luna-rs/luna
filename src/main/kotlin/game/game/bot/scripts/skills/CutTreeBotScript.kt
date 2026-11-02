package game.bot.scripts.skills

import api.bot.Suspendable.naturalMicroDelay
import io.luna.game.model.mob.bot.Bot
import api.bot.script.BotScriptData
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
import game.skill.woodcutting.cutTree.CutTreeAction
import game.skill.woodcutting.cutTree.Tree
import game.skill.woodcutting.cutTree.TreeStump
import game.skill.woodcutting.searchNest.Nest
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
    SkillingBotScript<GameObject>(bot, duration, zones, bot.woodcutting) {

    override val targetSearchTimeout: Duration = 180.seconds

    companion object {

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

    override fun requirements(): Boolean {
        return treeIds.isNotEmpty()
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
        return world.locator.findObjects(searchBase, searchRadius) { it.id in treeIds }
    }

    override fun tools(): SortedSet<SkillingTool> =
        Axe.VALUES.values
            .mapTo(TreeSet()) { SkillingTool(it.id, it.level) }


    override fun emergencyTool(): SkillingTool = SkillingTool(Axe.BRONZE.id, Axe.BRONZE.level)

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
