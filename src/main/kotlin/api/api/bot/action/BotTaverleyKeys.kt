package api.bot.action

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.Suspendable.waitFor
import api.bot.script.BotScript.ActionSafety
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.obj.doors.TaverleyJailDoor
import io.luna.game.model.EntityState
import io.luna.game.model.Position
import io.luna.game.model.item.GroundItem
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.`object`.GameObject
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the native Taverley jailer, cell and Velrak interactions for a dusty-key visit.
 *
 * @author lare96
 */
object BotTaverleyKeys {
    private const val JAILER = 201
    private const val VELRAK = 798
    val DUSTY_KEY = TaverleyJailDoor.DUSTY_KEY_GATE.key
    private val JAIL_KEY = TaverleyJailDoor.JAIL_CELL.key
    private val DOOR = Position(2931, 9690)
    private val JAILER_ANCHOR = Position(2933, 9693)
    private val VELRAK_ANCHOR = Position(2931, 9685)
    private var Bot.nextJailerAttempt by Attr.long()

    /**
     * Tells the activity whether a visit can finish or must resume after the combat reflex.
     *
     * @author lare96
     */
    internal enum class VisitResult {
        OBTAINED, WAIT_FOR_FIGHT, UNAVAILABLE
    }

    /**
     * Checks for a usable jail key or an allowed, ready jailer attempt.
     */
    fun canAttempt(bot: Bot): Boolean = inCell(bot) || bot.itemTracker.contains(JAIL_KEY) ||
        bot.position.isWithinDistance(JAILER_ANCHOR, 12) && droppedJailKey(bot) != null || canAttackJailer(bot)

    /**
     * Makes one pass through the native visit. Offering [onAttack] permits one jailer attack, and the
     * callback records the real attack submission before its combat reflex can pause this activity.
     */
    internal suspend fun visit(bot: Bot, basket: Set<Int>, current: () -> Boolean,
                               onAttack: (() -> Unit)?): VisitResult {
        val handler = bot.actionHandler
        fun safe() = current() && bot.state == EntityState.ACTIVE && ActionSafety.NON_COMBAT.allows(bot)
        fun hasJailKey() = bot.inventory.contains(JAIL_KEY)
        fun hasDustyKey() = bot.inventory.contains(DUSTY_KEY)
        fun requiredSlots() = if (inCell(bot) || hasJailKey()) 1 else 2

        if (!safe()) return VisitResult.UNAVAILABLE
        if (hasDustyKey()) return if (leaveCell(bot, ::safe)) VisitResult.OBTAINED else VisitResult.UNAVAILABLE

        val keyInBank = !inCell(bot) && !hasJailKey() && bot.bank.contains(JAIL_KEY)
        if (keyInBank || bot.inventory.computeRemainingSize() < requiredSlots()) {
            if (!leaveCell(bot, ::safe)) return VisitResult.UNAVAILABLE
            if (!bot.bank.isOpen) {
                val bank = handler.webWalkToNearestBank(fallback = false) ?: return VisitResult.UNAVAILABLE
                if (!safe() || !handler.banking.open(bank)) return VisitResult.UNAVAILABLE
            }
            if (!safe()) return VisitResult.UNAVAILABLE
            if (bot.inventory.computeRemainingSize() < requiredSlots() &&
                (!handler.banking.depositInventory(basket + JAIL_KEY + DUSTY_KEY) ||
                    bot.inventory.computeRemainingSize() < requiredSlots())) return VisitResult.UNAVAILABLE
            if (!hasJailKey() && bot.bank.contains(JAIL_KEY) &&
                (!handler.banking.withdraw(Item(JAIL_KEY), ::safe) || !hasJailKey())) return VisitResult.UNAVAILABLE
        }
        if (!safe() || !handler.widgets.clickCloseInterface() || !safe()) return VisitResult.UNAVAILABLE

        if (!inCell(bot) && !hasJailKey()) {
            if (!bot.position.isWithinDistance(JAILER_ANCHOR, 12)) {
                if (onAttack == null || !canAttackJailer(bot) || !handler.webWalkToAny(listOf(JAILER_ANCHOR)))
                    return VisitResult.UNAVAILABLE
            }
            if (!safe()) return VisitResult.UNAVAILABLE

            var drop = droppedJailKey(bot)
            if (drop == null && onAttack == null) {
                waitFor(5.seconds) {
                    drop = droppedJailKey(bot)
                    drop != null || hasJailKey() || !safe()
                }
            }
            drop?.let { handler.loot.pickup(it) { safe() && !hasJailKey() } }
            if (!hasJailKey()) {
                if (onAttack == null || !canAttackJailer(bot)) return VisitResult.UNAVAILABLE
                return startJailerFight(bot, ::safe, onAttack)
            }
        }

        if (!inCell(bot)) {
            if (!safe() || !hasJailKey() ||
                !handler.webWalkToAny(listOf(DOOR)) || !safe()) return VisitResult.UNAVAILABLE
            val closed = cellDoor(bot) ?: return VisitResult.UNAVAILABLE
            if (!handler.inventory.useItem(JAIL_KEY).onObject(closed) { safe() && !inCell(bot) } ||
                !waitFor(6.seconds) { inCell(bot) }) return VisitResult.UNAVAILABLE
        }

        val velrak = bot.world.locator.findNearestNpc(VELRAK_ANCHOR, 12) {
            it.id == VELRAK && it.state == EntityState.ACTIVE && inCell(bot) && it.isViewableFrom(bot)
        }
        if (velrak != null && safe()) {
            BotNpcDialogues.respond(bot, velrak,
                listOf("So... do you know anywhere good to explore?", "Yes please!"),
                allowed = { safe() && inCell(bot) }, completed = ::hasDustyKey) {
                handler.interactions.interact(1, velrak) { safe() && inCell(bot) }
            }
        }
        val exited = leaveCell(bot, ::safe)
        return if (hasDustyKey() && exited) VisitResult.OBTAINED else VisitResult.UNAVAILABLE
    }

    private fun canAttackJailer(bot: Bot): Boolean = !bot.preferences.isSkiller &&
        System.currentTimeMillis() >= bot.nextJailerAttempt && BotCombatActionHandler.wantedCombatSkills(bot).isNotEmpty()

    private fun inCell(bot: Bot): Boolean = bot.position.z == 0 &&
        bot.position.x in 2927..2935 && bot.position.y in 9681..9689

    private fun cellDoor(bot: Bot): GameObject? = bot.world.objects.findAll(DOOR)
        .filter { it.id == TaverleyJailDoor.JAIL_CELL.closed && it.state == EntityState.ACTIVE }
        .findFirst().orElse(null)

    private fun droppedJailKey(bot: Bot): GroundItem? = bot.world.locator.findItems(bot, 12) {
        it.id == JAIL_KEY && it.state == EntityState.ACTIVE &&
            it.position.isWithinDistance(JAILER_ANCHOR, 12) && it.isVisibleTo(bot)
    }.minByOrNull { bot.position.computeLongestDistance(it.position) }

    private suspend fun startJailerFight(bot: Bot, safe: () -> Boolean, onAttack: () -> Unit): VisitResult {
        val handler = bot.actionHandler
        val jailer = bot.world.locator.findNearestNpc(JAILER_ANCHOR, 12) {
            it.id == JAILER && it.state == EntityState.ACTIVE && it.isAlive && !it.combat.inCombat()
        } ?: return VisitResult.UNAVAILABLE
        fun permitted() = safe() && canAttackJailer(bot) &&
            bot.scriptStack.current()?.allowsCombatResponse != false && !jailer.combat.inCombat()
        if (!permitted() || !handler.combat.prepareTrainingStyle() ||
            handler.combat.isThreat(jailer, BotCombatThreat.Context.RECOVERY)) return VisitResult.UNAVAILABLE

        var submitted = false
        handler.interactions.interactBeforeSubmit(3, jailer, ::permitted) {
            submitted = true
            onAttack()
            bot.nextJailerAttempt = System.currentTimeMillis() + 10.minutes.inWholeMilliseconds
            true
        }
        return if (submitted) VisitResult.WAIT_FOR_FIGHT else VisitResult.UNAVAILABLE
    }

    private suspend fun leaveCell(bot: Bot, safe: () -> Boolean): Boolean {
        repeat(2) {
            if (!inCell(bot)) return true
            if (!safe()) return false
            val handler = bot.actionHandler
            if (!handler.widgets.clickCloseInterface() || !safe()) return false
            if (cellDoor(bot) == null) waitFor(4.seconds) { !inCell(bot) || cellDoor(bot) != null }
            if (!inCell(bot)) return true
            val closed = cellDoor(bot) ?: return@repeat
            if (handler.interactions.interact(1, closed) { safe() && inCell(bot) } &&
                waitFor(6.seconds) { !inCell(bot) }) return true
        }
        return !inCell(bot)
    }
}
