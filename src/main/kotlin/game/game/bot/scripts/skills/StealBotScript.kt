package game.bot.scripts.skills

import api.bot.script.BotScriptData
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.skill.SkillingBotScript
import api.bot.zone.SubZone
import api.predef.*
import com.google.common.collect.ImmutableList
import com.google.gson.JsonObject
import game.skill.thieving.stealFromStall.StealFromAction
import game.skill.thieving.stealFromStall.ThievingStallType
import io.luna.game.model.Position
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import kotlin.time.Duration

/**
 * A thieving bot script that steals from configured stall types inside selected subzones.
 *
 * This script uses the shared skilling/zoned-script lifecycle for zone selection, banking, duration tracking, and
 * target searching. It converts the configured [stalls] into the full-stall object ids that can be clicked, then searches
 * nearby objects for matching stalls during each skilling cycle.
 *
 * The script also adds simple survival behaviour. When the bot becomes nervous about its hitpoints, it attempts to eat
 * any food in its inventory. If no food is available, it forces a banking trip so more food can be withdrawn.
 *
 * Smarter low-combat bots avoid stealing while an attackable guard is watching them. This helps weak bots avoid
 * repeatedly triggering combat with nearby guards when they are likely to lose time or die.
 *
 * @param bot The bot running this script.
 * @param stalls The stall types this script may steal from.
 * @param duration The remaining amount of time this script should run before completing normally.
 * @param zones The candidate subzones this script may operate in.
 * @author lare96
 */
class StealBotScript(
    bot: Bot,
    val stalls: Set<ThievingStallType>,
    duration: Duration,
    zones: MutableList<SubZone>
) : SkillingBotScript<GameObject>(bot, duration, zones, bot.thieving) {

    companion object {

        /**
         * Serializable script data for [StealBotScript].
         *
         * This extends [ZonedBotScriptData] with the stall types selected for this thieving script. The inherited data
         * stores the remaining duration and candidate zones, while [stalls] stores the script-specific target types.
         */
        class StealData : ZonedBotScriptData() {

            /**
             * The stall types this script should restore when resumed or loaded.
             */
            var stalls = emptySet<ThievingStallType>()

            /**
             * Loads the saved zoned-script state and selected thieving stall types.
             *
             * @param data The JSON object containing this script's saved state.
             */
            override fun load(data: JsonObject) {
                super.load(data)
                stalls = loadEnumSet("stalls", data) { ThievingStallType.valueOf(it) }
            }

            /**
             * Saves the current zoned-script state and selected thieving stall types.
             *
             * @param data The JSON object to write this script's state into.
             */
            override fun save(data: JsonObject) {
                super.save(data)
                saveEnumSet("stalls", data, stalls)
            }
        }
    }

    /**
     * Creates a stealing script from saved script data.
     *
     * @param bot The bot running this script.
     * @param data The saved stealing script data to restore from.
     */
    constructor(bot: Bot, data: StealData) : this(bot, data.stalls, data.duration, data.zones)

    /**
     * The full-stall object ids this script can steal from.
     *
     * Each [ThievingStallType] can define multiple stall object states. Only the full object id is searched for because
     * empty/depleted stalls should not be clicked.
     */
    private val stallIds = run {
        val ids = HashSet<Int>()

        for (type in stalls) {
            for ((full, _) in type.stalls) {
                ids.add(full)
            }
        }

        ids
    }

    override suspend fun onExecuteSkilling(searching: Boolean, focus: GameObject?) {
        if (bot.emotions.isNervousAboutHp && !handler.inventory.eatAnyFood()) {
            bot.log("No food in inventory, banking for more.")
            forceBanking = true
        }
    }

    override suspend fun onBankOpenSkilling(initial: Boolean) {
        val remainingSpace = bot.inventory.computeRemainingSize()
        if (!handler.banking.withdrawAnyFood((remainingSpace * 0.15).toInt())) {
            bot.log("No food left, ending script.")
            stop()
        }
    }

    /**
     * Finds nearby full stalls that are safe enough for the bot to steal from.
     *
     * Intelligent low-combat bots avoid stealing while an attackable guard is watching them. This prevents weak bots from
     * repeatedly starting combat with guards when they are likely to lose or waste time. Less intelligent bots, stronger
     * bots, or bots that are not currently being watched will still attempt to steal.
     *
     * @param searchBase The position to search around.
     * @param searchRadius The maximum object-search radius.
     *
     * @return The nearby full-stall objects this script can attempt to steal from, or an empty collection if the bot
     * should avoid stealing right now.
     */
    override fun find(searchBase: Position, searchRadius: Int): MutableCollection<GameObject> {
        val isGuardWatching = world.locator.findViewableNpcs(searchBase) {
            it.combat.isAttackable &&
                    StealFromAction.GUARD_NAMES.contains(it.def().name) &&
                    !it.combat.inCombat() &&
                    it.inViewCone(bot)
        }.isNotEmpty()

        if (bot.personality.intelligence > 0.65 && isGuardWatching && bot.combatLevel < 30) {
            return ImmutableList.of()
        }

        return world.locator.findObjects(searchBase, searchRadius, true) { it.id in stallIds }
    }

    override fun interactionOption(): Int = 2

    override fun levelRequired(): Int = stalls.maxOfOrNull { it.level } ?: 0

    override fun snapshot(): BotScriptData {
        val data = StealData()
        data.stalls = stalls
        data.duration = duration
        data.zones = originalZones.toMutableList()
        return data
    }
}