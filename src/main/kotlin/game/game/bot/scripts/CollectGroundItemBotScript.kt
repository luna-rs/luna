package game.bot.scripts

import api.bot.script.TargetingZonedBotScript
import api.bot.script.ZonedBotScript.Companion.ZonedBotScriptData
import api.bot.zone.SubZone
import api.predef.world
import com.google.gson.JsonObject
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Collects an existing ground resource without combat, using ordinary pickups and the shared zone/bank lifecycle.
 * Native visibility and inventory rules apply; disappearing drops only count when carried stock increases.
 * An empty or repeatedly unreachable field is abandoned through the inherited search timeout.
 *
 * The goal counts inventory, equipment and bank stock rather than adding another batch on every visit.
 * Collection never creates supplies, drops belongings for space, or attacks an NPC. Reaching the stock goal requests
 * a bank visit before completion. Cowhide preparation uses this runner when the permanent build disallows combat.
 *
 * @property itemId The unnoted resource to collect.
 * @property stockTarget The total owned quantity at which collection ends.
 * @author lare96
 */
class CollectGroundItemBotScript(
    bot: Bot,
    val itemId: Int,
    val stockTarget: Int,
    duration: Duration,
    zones: MutableList<SubZone>
) : TargetingZonedBotScript<GroundItem>(bot, duration, zones) {

    /**
     * Serializable resource goal and inherited zone/session data; live ground references are never saved.
     *
     * @author lare96
     */
    class CollectionData : ZonedBotScriptData() {
        /**
         * Resource identifier, invalid until a complete collection snapshot is loaded.
         */
        var itemId = -1

        /**
         * Total owned stock goal, retained across pauses and reloads.
         */
        var stockTarget = 0

        override fun load(data: JsonObject) {
            super.load(data)
            itemId = data.get("item_id")?.asInt ?: -1
            stockTarget = data.get("stock_target")?.asInt ?: 0
        }

        override fun save(data: JsonObject) {
            super.save(data)
            data.addProperty("item_id", itemId)
            data.addProperty("stock_target", stockTarget)
        }
    }

    /**
     * Restores the same owned-stock goal and candidate fields after persistence.
     */
    constructor(bot: Bot, data: CollectionData) : this(bot, data.itemId, data.stockTarget, data.duration, data.zones)

    override val targetSearchTimeout = 2.minutes

    /**
     * Checks current ownership so other pickups and banking cannot restart or inflate the collection goal.
     */
    private fun goalReached(): Boolean = bot.itemTracker.count(itemId) >= stockTarget

    override suspend fun onInit(resumed: Boolean): Boolean =
        ItemDefinition.isIdValid(itemId) && stockTarget > 0 &&
                (!goalReached() || bot.inventory.contains(itemId))

    override suspend fun equipment() = null

    override fun allowsLoot(item: GroundItem): Boolean = item.id == itemId && !goalReached()

    override suspend fun beforeZoneWork(): Boolean {
        if (!goalReached()) return false
        if (bot.inventory.contains(itemId)) {
            forceBanking = true
            return false
        }
        stop()
        return true
    }

    override suspend fun onBankOpen(initial: Boolean) {
        if (goalReached()) stop()
    }

    override suspend fun onBankRequestedTargeting(initial: Boolean): Boolean {
        val zone = activeZone
        if (zone != null && zone in bot.subZones &&
            !zone.leave(bot, zone.parent(bot), zone.outside(bot))) return false
        return true
    }

    override suspend fun find(searchBase: Position, searchRadius: Int): MutableCollection<GroundItem> {
        val zone = activeZone ?: return mutableListOf()
        if (goalReached()) return mutableListOf()
        return world.locator.findItems(searchBase, searchRadius) {
            it.id == itemId && it.state == EntityState.ACTIVE && it.position in zone.area && it.isVisibleTo(bot)
        }
    }

    override suspend fun onAssignFocus(newFocus: GroundItem): Boolean {
        if (bot.inventory.isFull || goalReached()) {
            forceBanking = true
            return false
        }
        return newFocus.id == itemId && newFocus.position in activeZone!!.area && handler.loot.canPickup(newFocus)
    }

    override suspend fun interactWithTarget(option: Int, target: GroundItem): Boolean {
        val zone = activeZone ?: return false
        return handler.loot.pickup(target) {
            isActionSafe() && activeZone == zone && !goalReached() && target.position in zone.area
        }
    }

    override suspend fun refocus(): Boolean = true

    override fun snapshot(): CollectionData = CollectionData().also {
        it.itemId = itemId
        it.stockTarget = stockTarget
        it.duration = duration
        it.zones = originalZones.toMutableList()
    }
}
