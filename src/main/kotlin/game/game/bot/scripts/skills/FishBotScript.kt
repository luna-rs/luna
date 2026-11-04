package game.bot.scripts.skills

import api.bot.Suspendable.waitFor
import api.bot.action.trading.BotTradeIntentions
import api.bot.script.BotScriptData
import api.bot.social.SocialBotScript
import engine.bot.speech.BotConversationPool.ConversationSpeech
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.skill.SkillingBotScript
import api.bot.skill.SkillingTool
import api.bot.webwalk.plan.BankAccess
import api.bot.zone.SubZone
import api.predef.*
import com.google.common.collect.ImmutableSetMultimap
import com.google.gson.JsonObject
import engine.bot.coordinator.skill.CookingScriptFactory
import game.bot.scripts.GiantMoleTrip
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.skill.fishing.catchFish.Fish
import game.skill.fishing.catchFish.CatchFishAction
import game.skill.fishing.catchFish.FishingSpot
import game.skill.fishing.catchFish.Tool
import game.skill.fishing.fishingSpot.FishingSpotHandler
import game.content.sailing.Entrana
import game.obj.entrances.SwampCaveEntrance.swampCavesRoped
import game.bot.scripts.RandomEventReflexScript
import io.luna.game.action.ActionType
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import java.util.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Bot script for Fishing.
 *
 * The script fishes using a specific [selectedTool], searches for matching fishing spots in the active zone, handles
 * bait withdrawal when required, and interacts with the correct fishing option based on the spot definition.
 *
 * Fishing spots are resolved from both first-click and second-click spot maps, allowing the same tool to support NPCs
 * that require different interaction options. Guild trips recheck current Fishing 68 before every work-zone
 * trip, including returns from banking. The normal player fishing action retains its per-cast level checks.
 * Whirlpool avoidance reuses the random-event intelligence/dexterity policy. A decision belongs
 * to one hazard lifetime; bots that ignore it can reclick the spot and lose their fishing tool.
 *
 * @author lare96
 */
class FishBotScript(
    bot: Bot,

    /**
     * The fishing tool this script should use.
     *
     * This determines:
     * - The required Fishing level.
     * - The required tool item.
     * - The required bait, if any.
     * - The valid fishing spot NPC ids.
     * - Whether the spot should be interacted with using first-click or second-click.
     */
    val selectedTool: Tool,

    duration: Duration,
    zones: MutableList<SubZone>,

    /**
     * A bounded karambwanji-gathering detour which resumes vessel fishing with the remaining session time.
     */
    val gatherKarambwanBait: Boolean = false
) : SkillingBotScript<Npc>(bot, duration, zones, bot.fishing), SocialBotScript {

    override fun ambientConversationTopic(): ConversationSpeech = ConversationSpeech.FISHING

    override fun canSocialize(): Boolean = canTalkWhileWorking()

    override fun canTalkWhileWorking(): Boolean = canContinueConversation() &&
        bot.actions.first(CatchFishAction::class.java) != null

    override fun canContinueConversation(): Boolean = !forceBanking && !bot.inventory.isFull &&
        duration > Duration.ZERO && activeZone?.area?.contains(bot.position) == true &&
        bot.walking.isEmpty && !bot.navigator.isActive && !bot.combat.inCombat() && bot.combat.target == null

    companion object {
        /**
         * Current Fishing level required by the Fishing Guild entrance in the supplied Lost City source.
         */
        const val FISHING_GUILD_LEVEL = 68

        /**
         * Bait for several ordinary bank trips, gathered from the native pond rather than supplied for free.
         */
        const val KARAMBWAN_BAIT_TARGET = 250

        /**
         * How many times a bot clicks its fishing spot without starting to fish before it looks for another spot.
         */
        private const val SPOT_CLICK_ATTEMPTS = 3

        /**
         * Maps each fishing tool to the NPC fishing spots that use it as their first-click action.
         */
        val FIRST_CLICK_SPOTS: ImmutableSetMultimap<Tool, Int> = buildSpots { it.firstOption }

        /**
         * Maps each fishing tool to the NPC fishing spots that use it as their second-click action.
         */
        val SECOND_CLICK_SPOTS: ImmutableSetMultimap<Tool, Int> = buildSpots { it.secondOption }

        /**
         * Maps each tool returned by [option] to the NPC fishing spots that use it. Bots don't fish in Miscellania,
         * where the catch goes to Fisherman Frodi.
         */
        private fun buildSpots(option: (FishingSpot) -> Tool?): ImmutableSetMultimap<Tool, Int> {
            val spots = ImmutableSetMultimap.builder<Tool, Int>()
            for (spot in FishingSpot.entries) {
                val tool = option(spot)
                if (tool != null && spot != FishingSpot.MISCELLANIA) {
                    spots.putAll(tool, spot.npcs + listOfNotNull(spot.whirlpoolId))
                }
            }
            return spots.build()
        }

        /**
         * Serializable data for restoring a Fishing script.
         *
         * Extends [ZonedBotScriptData] with the selected fishing [Tool], allowing the script to resume with the same
         * tool, duration, and zones after it has been snapshotted.
         */
        class FishData : ZonedBotScriptData() {

            /**
             * The fishing tool used by this script.
             *
             * Defaults to [Tool.SMALL_NET] when no saved value is available.
             */
            var tool = Tool.SMALL_NET

            var gatherKarambwanBait = false

            override fun load(data: JsonObject) {
                super.load(data)
                tool = Tool.valueOf(data.get("tool").asString)
                gatherKarambwanBait = data.get("gatherKarambwanBait")?.asBoolean ?: false
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("tool", tool.name)
                data.addProperty("gatherKarambwanBait", gatherKarambwanBait)
            }
        }
    }

    /**
     * Restores a Fishing script from saved script data.
     */
    constructor(bot: Bot, data: FishData) : this(bot, data.tool, data.duration, data.zones, data.gatherKarambwanBait)

    /**
     * All NPC ids that can be fished using [selectedTool].
     *
     * This combines both first-click and second-click fishing spots so the script can search for every valid
     * target for the selected tool.
     */
    private val fishingSpotIds = lazyVal {
        val ids = HashSet<Int>()

        for (spotId in FIRST_CLICK_SPOTS[selectedTool] + SECOND_CLICK_SPOTS[selectedTool]) {
            ids += spotId
        }

        ids
    }

    /**
     * The fishing spot that [spotClicks] counts clicks for.
     */
    private var clickedSpot: Npc? = null

    /**
     * How many times in a row the bot has clicked [clickedSpot] without starting to fish.
     */
    private var spotClicks = 0

    private var continuationQueued = false

    private val caveFishing = selectedTool == Tool.CAVE_EEL_FISHING_ROD

    /**
     * One ignore decision per live whirlpool. Weak keys do not retain removed fishing NPCs.
     * A later transformation of the same NPC receives a new decision through its expiry token.
     */
    private val whirlpoolDecisions = WeakHashMap<Npc, Pair<Long, Boolean>>()

    /**
     * Rechecks live spot types while searching, before clicking and during navigation. Dexterous
     * bots always avoid whirlpools; lower intelligence and dexterity increase deliberate ignorance.
     */
    internal fun canFishSpot(npc: Npc): Boolean {
        if (npc.id !in fishingSpotIds.value) return false
        val expiry = FishingSpotHandler.whirlpoolExpiry(npc) ?: return true
        val decision = whirlpoolDecisions[npc]?.takeIf { it.first == expiry }?.second ?: run {
            val chance = RandomEventReflexScript.ignoreChance(bot.personality.intelligence, bot.personality.dexterity)
            val ignore = kotlin.random.Random.nextDouble() < chance
            whirlpoolDecisions[npc] = expiry to ignore
            ignore
        }
        return decision
    }

    /**
     * Vessel bait is loaded before casting, rather than consumed by the catch action.
     */
    private val baitId = if (selectedTool == Tool.KARAMBWAN_VESSEL) Fish.KARAMBWANJI.id else selectedTool.bait

    /**
     * Acquire an empty vessel; its loaded form is temporary and must not be provisioned as a replacement tool.
     */
    private val toolId = if (selectedTool == Tool.KARAMBWAN_VESSEL) Tool.EMPTY_KARAMBWAN_VESSEL else selectedTool.id

    override fun toolIds(id: Int): List<Int> {
        return if (selectedTool == Tool.KARAMBWAN_VESSEL && id == Tool.EMPTY_KARAMBWAN_VESSEL)
            listOf(id, Tool.KARAMBWAN_VESSEL.id) else super.toolIds(id)
    }

    override fun requirements(): Boolean {
        return fishingSpotIds.value.isNotEmpty() && (!caveFishing || GiantMoleTrip.lightOwned(bot)) &&
            (selectedTool != Tool.LAVA_EEL_FISHING_ROD || bot.itemTracker.contains(toolId))
    }

    override suspend fun onBankOpenSkilling(initial: Boolean) {
        if (caveFishing) {
            if (!GiantMoleTrip.collectLight(bot) || !bot.swampCavesRoped &&
                (!handler.banking.withdraw(Item(954)) || 954 !in bot.inventory)) {
                stop()
                return
            }
        }
        val baitId = baitId ?: return

        if (baitId !in bot.itemTracker) {
            bot.log("We do not have the required bait to use this tool.")
            stop()
            if (selectedTool == Tool.KARAMBWAN_VESSEL) {
                queueBaitGathering()
                return
            }
            bot.preferences.addWantedItem(baitId, 1_000)
            BotTradeIntentions.recordSupplies(bot,
                listOf(Item(baitId, 28)), stockTarget = 1_000)
            return
        }

        if (!handler.banking.withdraw(Item(baitId, minOf(250, bot.bank.computeAmountForId(baitId)))) ||
            baitId !in bot.inventory) stop()
    }

    override suspend fun beforeZoneWork(): Boolean {
        if (caveFishing && !GiantMoleTrip.lightCarried(bot) && !GiantMoleTrip.ensureLight(bot)) forceBanking = true
        if (caveFishing && !bot.swampCavesRoped && 954 !in bot.inventory) forceBanking = true
        if (baitId != null && baitId !in bot.inventory &&
            !(selectedTool == Tool.KARAMBWAN_VESSEL && selectedTool.id in bot.inventory)) forceBanking = true
        return false
    }

    override suspend fun travelToWorkZone(zone: SubZone, bank: BankAccess): Boolean {
        if (zone == SubZone.FISHING_GUILD && bot.fishing.level < FISHING_GUILD_LEVEL) return false
        if (caveFishing && (!GiantMoleTrip.ensureLight(bot) || !bot.swampCavesRoped && 954 !in bot.inventory)) return false
        if (zone in setOf(SubZone.ENTRANA_SEA_FISHING, SubZone.ENTRANA_RIVER_SOUTH_FISHING,
                SubZone.ENTRANA_RIVER_NORTH_FISHING) && Entrana.isCarryingForbidden(bot)) return false
        return super.travelToWorkZone(zone, bank)
    }

    override suspend fun onExecuteSkilling(searching: Boolean) {
        if (gatherKarambwanBait && selectedTool == Tool.KARAMBWANJI_NET &&
            bot.itemTracker.count(Fish.KARAMBWANJI.id) >= KARAMBWAN_BAIT_TARGET) {
            queueVesselFishing()
            bot.actions.first(CatchFishAction::class.java)?.interrupt()
            stop()
            return
        }
        val bait = baitId
        if (bait != null && bait !in bot.inventory &&
            !(selectedTool == Tool.KARAMBWAN_VESSEL && selectedTool.id in bot.inventory)) {
            forceBanking = true
        }
    }

    override suspend fun find(searchBase: Position, searchRadius: Int): MutableCollection<Npc> {
        return world.locator.findNpcs(searchBase, searchRadius) {
            canFishSpot(it)
        }
    }

    override val targetSearchTimeout: Duration = 180.seconds

    override fun tools(): SortedSet<SkillingTool> {
        return sortedSetOf(SkillingTool(toolId, selectedTool.level))
    }

    override fun emergencyTool(): SkillingTool? {
        return if (selectedTool == Tool.LAVA_EEL_FISHING_ROD) null else SkillingTool(toolId, selectedTool.level)
    }

    override suspend fun interactionOption(target: Npc): Int? {
        if (!canFishSpot(target)) return null
        if (selectedTool == Tool.KARAMBWAN_VESSEL && selectedTool.id !in bot.inventory) {
            if (Fish.KARAMBWANJI.id !in bot.inventory || Tool.EMPTY_KARAMBWAN_VESSEL !in bot.inventory) {
                forceBanking = true
                return null
            }
            if (!handler.inventory.useItem(Fish.KARAMBWANJI.id).onItem(Tool.EMPTY_KARAMBWAN_VESSEL)) return null
            if (!waitFor(3.seconds) { selectedTool.id in bot.inventory }) return null
        }
        if (target.id in SECOND_CLICK_SPOTS[selectedTool]) {
            return 2
        }

        return 1
    }

    override fun levelRequired(): Int {
        return selectedTool.level
    }

    override suspend fun refocus(): Boolean {
        val spot = focus
        if (spot != null && !canFishSpot(spot)) return true
        if (spot !== clickedSpot || bot.actions.size(ActionType.WEAK) > 0) {
            clickedSpot = spot
            spotClicks = 0
        } else if (spot?.state == EntityState.ACTIVE && ++spotClicks > SPOT_CLICK_ATTEMPTS) {
            // TODO Remove once the web walker (#617) handles navigation. Walking to an NPC only tries the side of it
            //  nearest the bot, which is often water once a fishing spot has moved, so the bot would keep clicking a
            //  spot it can't reach until the spot moved again. Drop it and search for another spot instead.
            bot.log("Couldn't start fishing at ${spot.position}, finding another spot.")
            spotClicks = 0
            return true
        }
        return super.refocus()
    }

    override suspend fun onAssignFocus(newFocus: Npc): Boolean = canFishSpot(newFocus)

    override suspend fun interactWithTarget(option: Int, target: Npc): Boolean =
        handler.interactions.interact(option, target) { canFishSpot(target) }

    override fun snapshot(): BotScriptData {
        val data = FishData()
        data.duration = duration
        data.zones = originalZones.toMutableList()
        data.tool = selectedTool
        data.gatherKarambwanBait = gatherKarambwanBait
        return data
    }

    /**
     * Reuses net fishing for bait and keeps the original activity's remaining time budget across both legs.
     */
    private fun queueBaitGathering() {
        val remaining = remainingDuration()
        if (continuationQueued || remaining <= Duration.ZERO) return
        continuationQueued = true
        bot.scriptStack.softPushHead(FishBotScript(bot, Tool.KARAMBWANJI_NET, remaining,
            mutableListOf(SubZone.KARAMJA_KARAMBWANJI_FISHING), gatherKarambwanBait = true))
    }

    /**
     * Resumes only after real bait was caught; a failed or empty detour cannot recursively repeat itself.
     */
    private fun queueVesselFishing() {
        val remaining = remainingDuration()
        if (continuationQueued || remaining <= Duration.ZERO || bot.itemTracker.count(Fish.KARAMBWANJI.id) == 0) return
        continuationQueued = true
        bot.scriptStack.softPushHead(FishBotScript(bot, Tool.KARAMBWAN_VESSEL, remaining,
            mutableListOf(SubZone.KARAMJA_KARAMBWAN_FISHING)))
    }

    override suspend fun completed() {
        if (gatherKarambwanBait) {
            queueVesselFishing()
            return
        }
        // Chance to queue a fletching script.
        if (rand(bot.personality.intelligence) || bot.personality.isDextrous) {
            val script = CookingScriptFactory.getScript(bot, bot.cooking.staticLevel, mutableListOf(), randBoolean())
            bot.scriptStack.pushTail(script, 2)
        }
    }
}
