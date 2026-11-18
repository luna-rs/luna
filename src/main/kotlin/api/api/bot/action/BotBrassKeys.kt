package api.bot.action

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.Suspendable.waitFor
import api.bot.script.BotScript.ActionSafety
import engine.spawn.PersistentGroundItem
import game.obj.doors.BrassKeyDoor
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Collects a needed brass key from its ordinary Edgeville Dungeon spawn.
 *
 * @author lare96
 */
object BotBrassKeys {

    private val spawn = Position(3131, 9862)
    private var Bot.nextBrassKeyTrip by Attr.long()

    /**
     * Makes one bounded trip for one key, preserving other requested supplies when banking for inventory space.
     * A missing or contested spawn is left for another visit.
     */
    suspend fun obtain(bot: Bot, basket: Set<Int>): Boolean = withTimeoutOrNull(150.seconds) {
        val handler = bot.actionHandler
        val key = Item(BrassKeyDoor.KEY)
        val caller = bot.scriptStack.current()
        fun hasKey(): Boolean = bot.inventory.contains(key.id)
        fun safe(): Boolean = bot.state == EntityState.ACTIVE && bot.scriptStack.current() === caller &&
            ActionSafety.NON_COMBAT.allows(bot)
        fun findKey() = bot.world.locator.findItemsOnTile(spawn) {
            it is PersistentGroundItem && it.id == key.id && it.state == EntityState.ACTIVE && it.isVisibleTo(bot)
        }.firstOrNull()

        if (hasKey()) return@withTimeoutOrNull true
        val now = System.currentTimeMillis()
        if (!safe() || now < bot.nextBrassKeyTrip) return@withTimeoutOrNull false
        bot.nextBrassKeyTrip = now + 180.seconds.inWholeMilliseconds

        if (!bot.inventory.hasSpaceFor(key)) {
            if (!bot.bank.isOpen) {
                val bank = handler.webWalkToNearestBank(fallback = false) ?: return@withTimeoutOrNull false
                if (!safe() || !handler.banking.open(bank)) return@withTimeoutOrNull false
            }
            if (!safe() || !handler.banking.depositInventory(basket + key.id) ||
                !bot.inventory.hasSpaceFor(key)) return@withTimeoutOrNull false
        }
        if (!safe() || !handler.widgets.clickCloseInterface() || !safe()) return@withTimeoutOrNull false
        if (!bot.position.isWithinDistance(spawn, 2) && !handler.webWalkToAny(listOf(spawn)))
            return@withTimeoutOrNull false
        if (!safe()) return@withTimeoutOrNull false

        var groundKey = findKey()
        if (groundKey == null && !hasKey()) {
            waitFor(10.seconds) {
                groundKey = findKey()
                groundKey != null || hasKey() || !safe()
            }
        }
        if (hasKey()) return@withTimeoutOrNull true
        val target = groundKey ?: return@withTimeoutOrNull false
        handler.loot.pickup(target) { safe() && !hasKey() } && hasKey()
    } == true
}
