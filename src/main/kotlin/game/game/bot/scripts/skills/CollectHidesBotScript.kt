package game.bot.scripts.skills

import api.bot.zone.SubZone
import com.google.common.collect.ImmutableSetMultimap
import com.google.common.collect.SetMultimap
import com.google.gson.JsonObject
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.bot.scripts.NpcCombatScript
import game.bot.scripts.NpcCombatScript.Companion.NpcCombatData
import io.luna.game.model.item.DeathGroundItem
import io.luna.game.model.item.GroundItem
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Kills cows and banks the cowhides they drop, so the bot can tan them later. The script ends once the bot owns
 * [hideTarget] cowhides.
 *
 * The bot ignores the raw beef and bones cows drop. When [pickUpHides] is `false` it leaves the hides on the ground too,
 * so it looks like it's training combat on cows.
 *
 * @param bot The bot running this script.
 * @param duration How long this script should run before completing normally.
 * @param zones The cow fields the bot may use.
 * @param pickUpHides If the bot picks up cowhides.
 * @author TheLining
 */
class CollectHidesBotScript(bot: Bot, duration: Duration, zones: MutableList<SubZone>, val pickUpHides: Boolean) :
    NpcCombatScript(bot, duration, zones, cows(zones)) {

    companion object {

        /**
         * The cowhide item id.
         */
        const val COWHIDE = 1739

        /**
         * Serializable script data for [CollectHidesBotScript].
         */
        class CollectHidesData : NpcCombatData() {

            /**
             * If the bot picks up cowhides.
             */
            var pickUpHides = true

            override fun load(data: JsonObject) {
                super.load(data)
                pickUpHides = data.get("pickUpHides").asBoolean
            }

            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("pickUpHides", pickUpHides)
            }
        }

        /**
         * How many cowhides [bot] collects before it moves on to tanning, between 500 and 1000.
         *
         * Derived from the username so it stays the same across sessions without being saved.
         *
         * @param bot The bot collecting hides.
         * @return The number of cowhides to own before tanning.
         */
        fun hideTarget(bot: Bot): Int {
            return 500 + Math.floorMod(bot.username.hashCode(), 501)
        }

        /**
         * Restricts the bot to cows and calves in every zone.
         */
        private fun cows(zones: List<SubZone>): SetMultimap<SubZone, String> {
            val names = ImmutableSetMultimap.builder<SubZone, String>()
            zones.forEach { names.putAll(it, "Cow", "Cow calf") }
            return names.build()
        }
    }

    /**
     * Recreates a hide-collecting script from saved script data.
     *
     * @param bot The bot running this script.
     * @param data The saved script data.
     */
    constructor(bot: Bot, data: CollectHidesData) : this(bot, data.duration, data.zones, data.pickUpHides)

    // Picking up hides between fights counts towards this, and other bots can leave minutes' worth on the ground.
    override val targetSearchTimeout: Duration = 10.minutes

    override fun isLoot(item: GroundItem): Boolean {
        return when {
            item.id == COWHIDE -> pickUpHides
            // Raw beef and bones would only take inventory space from hides.
            item is DeathGroundItem && item.origin is Npc -> false
            else -> super.isLoot(item)
        }
    }

    override suspend fun onAssignFocus(newFocus: Npc): Boolean {
        if (bot.inventory.isFull) {
            // Bank before attacking another cow. A fight started now cancels the walk out of the field.
            forceBanking = true
            return false
        }
        return super.onAssignFocus(newFocus)
    }

    override suspend fun onBankOpen(initial: Boolean) {
        if (pickUpHides && bot.itemTracker.count(COWHIDE) >= hideTarget(bot)) {
            bot.log("Collected ${hideTarget(bot)} cowhides. Ending script.")
            stop()
            return
        }
        super.onBankOpen(initial)
    }

    override suspend fun onBankRequestedTargeting(initial: Boolean): Boolean {
        // A gated field shares its region with the parent zone, so travelling to the parent's bank wouldn't leave
        // through the gate first.
        val zone = activeZone
        if (zone != null && bot.subZone == zone) {
            zone.leave(bot, zone.parent(bot), zone.outside(bot))
        }
        return super.onBankRequestedTargeting(initial)
    }

    override fun snapshot(): CollectHidesData {
        val data = CollectHidesData()
        data.duration = duration
        data.zones = originalZones.toMutableList()
        data.pickUpHides = pickUpHides
        return data
    }
}
