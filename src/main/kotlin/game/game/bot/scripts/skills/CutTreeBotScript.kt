package game.bot.scripts.skills

import api.bot.script.BotScriptData
import api.bot.Suspendable.naturalMicroDelay
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.skill.SkillingBotScript
import api.bot.skill.SkillingTool
import api.bot.zone.SubZone
import api.predef.*
import com.google.gson.JsonObject
import game.skill.woodcutting.cutTree.Axe
import game.skill.woodcutting.cutTree.Tree
import game.skill.woodcutting.cutTree.TreeStump
import game.skill.woodcutting.searchNest.Nest
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import java.util.*
import kotlin.time.Duration

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
 * @author lare96
 */
class CutTreeBotScript(bot: Bot, val trees: Set<Tree>, duration: Duration, zones: MutableList<SubZone>) :
    SkillingBotScript<GameObject>(bot, duration, zones, bot.woodcutting) {

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

            override fun load(data: JsonObject) {
                super.load(data)
                trees = loadEnumSet("trees", data) { Tree.valueOf(it) }
            }

            override fun save(data: JsonObject) {
                super.save(data)
                saveEnumSet("trees", data, trees)
            }
        }
    }

    /**
     * Creates a [CutTreeBotScript] from persisted script data.
     *
     * @param bot The bot running this script.
     * @param data The saved Woodcutting script data to restore from.
     */
    constructor(bot: Bot, data: CutTreeData) : this(bot, data.trees, data.duration, data.zones)

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

    override fun requirements(): Boolean {
        return treeIds.isNotEmpty()
    }

    override suspend fun onExecuteSkilling(searching: Boolean, focus: GameObject?) {
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

    override fun find(searchBase: Position, searchRadius: Int): MutableCollection<GameObject> {
        return world.locator.findObjects(searchBase, searchRadius) { it.id in treeIds }
    }

    override fun tools(): SortedSet<SkillingTool> =
        Axe.VALUES.values.filter { skill.staticLevel >= it.level }
            .mapTo(TreeSet()) { SkillingTool(it.id, it.level) }


    override fun emergencyTool(): SkillingTool = SkillingTool(Axe.BRONZE.id, Axe.BRONZE.level)

    override fun levelRequired(): Int = trees.maxOfOrNull { it.level } ?: 0

    override fun snapshot(): BotScriptData {
        val data = CutTreeData()
        data.duration = duration
        data.zones = originalZones.toMutableList()
        data.trees = trees
        return data
    }
}