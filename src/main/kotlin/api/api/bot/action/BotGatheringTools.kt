package api.bot.action

import api.bot.Suspendable.waitFor
import api.bot.script.BotScript.ActionSafety
import api.predef.world
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration.Companion.seconds

/**
 * Recovers detached gathering-tool heads with real ground pickup and native item-on-handle assembly.
 * The skill supplies source-backed identities and may permit dropping gathered resources for space.
 * No replacement item is created; failure retains the normal tool-acquisition fallback.
 *
 * @author lare96
 */
object BotGatheringTools {
    /**
     * Recovers a carried or nearby visible head before its personal drop expires. A worn handle is unequipped normally.
     * Only caller-approved resources may be dropped for space; equipment, supplies and the handle are retained.
     */
    suspend fun recoverHead(bot: Bot, handle: Int, heads: Map<Int, Int>, disposable: Set<Int> = emptySet()): Boolean {
        fun safe() = ActionSafety.NON_COMBAT.allows(bot)
        if (!safe() || !bot.inventory.contains(handle) && bot.equipment[Equipment.WEAPON]?.id != handle) return false
        fun carriedHead() = heads.keys.firstOrNull { bot.inventory.contains(it) }
        fun nearbyHead() = world.locator.findItems(bot, 7) { it.id in heads && it.isVisibleTo(bot) }
            .minByOrNull { bot.position.computeLongestDistance(it.position) }
        var head = carriedHead()
        var ground = if (head == null) nearbyHead() else null
        if (head == null && ground == null) {
            // The native projectile can land after the tool was replaced by its handle.
            waitFor(2.seconds) { !safe() || nearbyHead() != null }
            ground = nearbyHead()
        }
        if (!safe() || head == null && ground == null) return false
        val spaces = (if (bot.inventory.contains(handle)) 0 else 1) + (if (head == null) 1 else 0)
        while (bot.inventory.computeRemainingSize() < spaces) {
            val resource = disposable.firstOrNull { bot.inventory.contains(it) } ?: return false
            if (!safe() || !bot.actionHandler.inventory.dropItem(resource)) return false
        }
        if (!bot.inventory.contains(handle) && !bot.actionHandler.equipment.unequip(Equipment.WEAPON)) return false
        if (!safe() || !bot.inventory.contains(handle)) return false
        if (head == null) {
            val item = ground ?: return false
            if (!bot.actionHandler.loot.pickup(item) { safe() && bot.inventory.contains(handle) }) return false
            head = item.id
        }
        val headId = head ?: return false
        val tool = heads[headId] ?: return false
        if (!safe() || !bot.inventory.contains(headId)) return false
        val beforeHead = bot.inventory.computeAmountForId(headId)
        val beforeTool = bot.inventory.computeAmountForId(tool)
        if (!bot.actionHandler.inventory.useItem(headId).onItem(handle)) return false
        return waitFor(3.seconds) {
            bot.inventory.computeAmountForId(tool) > beforeTool && bot.inventory.computeAmountForId(headId) < beforeHead
        }
    }
}
