package api.bot.action

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.Suspendable.waitFor
import api.bot.script.BotScript.ActionSafety
import api.predef.world
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.obj.doors.TaverleyJailDoor
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Obtains a requested dusty key through the existing Taverley jailer, jail door and Velrak dialogue.
 * The jailer's ordinary drop supplies the jail key; the combat reflex owns any permitted fight and its recovery.
 * Velrak's existing dialogue grants the dusty key without quest state. Failed attempts retain ordinary shop fallback.
 *
 * @author lare96
 */
object BotTaverleyKeys {
    /**
     * Native NPC identities and source key items.
     */
    const val JAILER = 201
    const val VELRAK = 798
    val DUSTY_KEY = TaverleyJailDoor.DUSTY_KEY_GATE.key
    private val JAIL_KEY = TaverleyJailDoor.JAIL_CELL.key

    /**
     * Selected 377-cache southern cell door and the existing NPC spawn anchors.
     * The keyed native passage moves south from the door tile into Velrak's cell.
     */
    private val DOOR = Position(2931, 9690)
    private val JAILER_ANCHOR = Position(2933, 9693)
    private val VELRAK_ANCHOR = Position(2931, 9685)

    /**
     * A failed or interrupted deliberate jailer fight cannot start another for ten minutes.
     * The real key drop can still be recovered during that interval.
     */
    private var Bot.nextJailerAttempt by Attr.long()

    /**
     * Admission without a coin remains possible for a non-skiller able to gain combat experience or owning a jail key.
     * Live danger, training style, supplies and native attack permission are rechecked at the jailer.
     */
    fun canAttempt(bot: Bot): Boolean = bot.itemTracker.contains(JAIL_KEY) ||
        !bot.preferences.isSkiller && System.currentTimeMillis() >= bot.nextJailerAttempt &&
            BotCombatActionHandler.wantedCombatSkills(bot).isNotEmpty()

    /**
     * Acquires at most one carried dusty key, preserving the caller's other supply basket during space recovery.
     * The caller's ordinary item-acquisition deadline also bounds this path.
     */
    suspend fun obtain(bot: Bot, basket: Set<Int>): Boolean = withTimeoutOrNull(90.seconds) {
        val handler = bot.actionHandler
        fun safe() = bot.state == EntityState.ACTIVE && ActionSafety.NON_COMBAT.allows(bot)
        fun inCell() = bot.position.z == 0 && bot.position.x in 2927..2935 && bot.position.y in 9681..9689
        fun door(): GameObject? = world.objects.findAll(DOOR)
            .filter { it.id == TaverleyJailDoor.JAIL_CELL.closed }.findFirst().orElse(null)
        if (bot.inventory.contains(DUSTY_KEY)) return@withTimeoutOrNull true
        if (!safe()) return@withTimeoutOrNull false
        if (bot.inventory.computeRemainingSize() < 2) {
            if (!handler.banking.travelToBankOpen() || !handler.banking.depositInventory(basket + JAIL_KEY + DUSTY_KEY))
                return@withTimeoutOrNull false
        }
        if (!safe() || !handler.widgets.clickCloseInterface()) return@withTimeoutOrNull false
        if (!inCell() && !bot.inventory.contains(JAIL_KEY)) {
            if (bot.bank.contains(JAIL_KEY)) {
                if (!handler.banking.travelToBankOpen() || !handler.banking.withdraw(Item(JAIL_KEY)) ||
                    !handler.widgets.clickCloseInterface()) return@withTimeoutOrNull false
            } else {
                if (!canAttempt(bot) || !handler.webWalkTo(JAILER_ANCHOR, radius = 2) || !safe())
                    return@withTimeoutOrNull false
                val dropped = world.locator.findItems(bot, 12) { it.id == JAIL_KEY && it.isVisibleTo(bot) }
                    .minByOrNull { bot.position.computeLongestDistance(it.position) }
                if (dropped != null) handler.loot.pickup(dropped) { safe() }
                if (!bot.inventory.contains(JAIL_KEY)) {
                    val jailer = world.locator.findNearestNpc(JAILER_ANCHOR, 12) {
                        it.id == JAILER && it.state == EntityState.ACTIVE && it.isAlive && !it.combat.inCombat()
                    } ?: return@withTimeoutOrNull false
                    if (!safe() || bot.preferences.isSkiller || bot.scriptStack.current()?.allowsCombatResponse == false ||
                        !handler.combat.prepareTrainingStyle() || handler.combat.isThreat(jailer, BotCombatThreat.Context.RECOVERY))
                        return@withTimeoutOrNull false
                    bot.preferences.raiseWantedItemTarget(JAIL_KEY, 1)
                    bot.nextJailerAttempt = System.currentTimeMillis() + 10 * 60_000L
                    // The ordinary combat reflex pauses and resumes this caller; no parallel combat loop is created.
                    if (handler.interactions.interact(3, jailer) { safe() && !bot.preferences.isSkiller }) {
                        waitFor(3.seconds) { bot.combat.inCombat() || bot.inventory.contains(JAIL_KEY) ||
                            !ActionSafety.COMBAT.allows(bot) }
                    }
                    return@withTimeoutOrNull false
                }
            }
        }
        if (!inCell()) {
            if (!safe() || !bot.inventory.contains(JAIL_KEY) || !handler.webWalkTo(DOOR)) return@withTimeoutOrNull false
            val closed = door() ?: return@withTimeoutOrNull false
            if (!handler.inventory.useItem(JAIL_KEY).onObject(closed) || !waitFor(6.seconds) { inCell() })
                return@withTimeoutOrNull false
        }
        if (!safe()) return@withTimeoutOrNull false
        val velrak = world.locator.findNearestNpc(VELRAK_ANCHOR, 12) {
            it.id == VELRAK && it.state == EntityState.ACTIVE
        } ?: return@withTimeoutOrNull false
        val obtained = BotNpcDialogues.respond(bot, velrak,
            listOf("So... do you know anywhere good to explore?", "Yes please!"),
            allowed = { safe() && inCell() }, completed = { bot.inventory.contains(DUSTY_KEY) }) {
            handler.interactions.interact(1, velrak) { safe() && inCell() }
        }
        if (inCell() && safe()) {
            val closed = door()
            if (closed != null && handler.interactions.interact(1, closed) { safe() }) {
                waitFor(6.seconds) { !inCell() }
            }
        }
        obtained && bot.inventory.contains(DUSTY_KEY) && !inCell()
    } == true
}
