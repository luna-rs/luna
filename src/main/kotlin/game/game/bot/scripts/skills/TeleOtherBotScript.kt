package game.bot.scripts.skills

import api.bot.Suspendable.naturalMicroDelay
import api.bot.Suspendable.waitFor
import api.bot.isRealBot
import api.bot.script.InventoryBotScript
import api.bot.script.BotScript.ActionSafety
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.script.ownsProductionSupplies
import api.bot.script.productionRuneCosts
import api.bot.social.BotConversations
import api.bot.social.BotSocialInteractions
import api.predef.*
import com.google.gson.JsonObject
import engine.controllers.Controllers.inWilderness
import game.bot.scripts.IdleBotScript
import game.skill.magic.Magic
import game.skill.magic.Rune
import game.skill.magic.SpellCost
import game.skill.magic.teleOther.TeleOtherInterface
import game.skill.magic.teleOther.TeleOtherType
import io.luna.game.model.EntityState
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Spellbook
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.varp.PersistentVarp
import java.util.WeakHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Offers one native city tele-other cast to a nearby idle bot with owned rune supplies.
 *
 * Both participants retain their current scripts while a short social claim prevents competing interactions.
 * The recipient accepts or declines through the real interface, with kindness affecting willingness to help.
 * Temporary accept-aid changes are restored. Unsolicited requests have no matching agreement and are declined;
 * this activity never targets human players. Native actions own teleport movement, costs and experience.
 * A saved attempted offer is not repeated after login, and live partners are never persisted.
 *
 * @author lare96
 */
class TeleOtherBotScript(bot: Bot, val type: TeleOtherType, duration: Duration) :
    InventoryBotScript(bot, duration, StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()) {
    override val trainingSkill = SKILL_MAGIC
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * Actual regular-spellbook widgets registered by handleTeleOther.kts.
         */
        val BUTTONS = mapOf(TeleOtherType.LUMBRIDGE to 12425, TeleOtherType.FALADOR to 12435,
                            TeleOtherType.CAMELOT to 12455)
        private val offers = WeakHashMap<Bot, Offer>()

        /**
         * Only an idle, unclaimed bot can be invited; no active gathering or trading is interrupted.
         */
        internal fun availableRecipient(bot: Bot): Boolean = bot.isRealBot() && !bot.inWilderness() &&
            !bot.status.isTeleBlocked() &&
            bot.scriptStack.current() is IdleBotScript && BotConversations.available(bot)

        /**
         * Handles the current native request before ordinary brain processing can walk away from its interface.
         * A matching runtime agreement is required for acceptance; replacing the caster or spell invalidates it.
         */
        @JvmStatic
        fun respond(bot: Bot): Boolean {
            val window = bot.overlays.getOverlay(TeleOtherInterface::class.java) ?: return false
            val offer = offers[bot]
            val accept = offer != null && offer.valid() && offer.caster === window.source &&
                offer.recipient === window.target && offer.type == window.type && offer.willing &&
                ActionSafety.NON_COMBAT.allows(bot)
            if (offer?.responded != true) {
                if (offer != null) offer.responded = true
                bot.output.clickButton(if (accept) 12566 else 12568)
            }
            return true
        }

        /**
         * Selected city spell and original one-off execution state alongside shared inventory retry budgets.
         *
         * @author lare96
         */
        class TeleOtherData : InventoryScriptData() {
            /**
             * Native city spell chosen for the visit.
             */
            var spell = TeleOtherType.LUMBRIDGE.name
            /**
             * A dispatched or abandoned invitation is never replayed by a restored visit.
             */
            var attempted = false
            override fun load(data: JsonObject) {
                super.load(data)
                spell = data.get("spell")?.asString ?: TeleOtherType.LUMBRIDGE.name
                attempted = data.get("attempted")?.asBoolean ?: false
            }
            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("spell", spell)
                data.addProperty("attempted", attempted)
            }
        }
    }

    private var attempted = false
    private var offer: Offer? = null

    constructor(bot: Bot, data: TeleOtherData) : this(bot, TeleOtherType.valueOf(data.spell), data.duration) {
        attempted = data.attempted
        restoreInventoryState(data)
    }

    /**
     * Makes this an occasional cooperative option only when a real recipient and one cast's owned runes exist.
     */
    fun isEligible(): Boolean = bot.spellbook == Spellbook.REGULAR && bot.magic.staticLevel >= type.level &&
        bot.ownsProductionSupplies(bot.productionRuneCosts(type.requirements)) &&
        world.locator.findViewablePlayers(bot) { it !== bot && it is Bot && availableRecipient(it) }.isNotEmpty()

    override fun withdraw(): List<Item> = productionWithdraw(bot.productionRuneCosts(type.requirements),
        levelEligible = !attempted && bot.spellbook == Spellbook.REGULAR && bot.magic.staticLevel >= type.level,
        requestMissing = false)

    override fun bankWithdraw(): List<Item> = bot.productionRuneCosts(type.requirements, bankedOnly = true)

    override suspend fun onInventoryBankRequested(): Boolean = false

    override suspend fun onExecuteInZone(): Boolean {
        if (attempted) { stop(); return true }
        if (!productionReady(Magic.spellCost(bot, type.level, type.requirements) is SpellCost.Castable,
                levelEligible = bot.spellbook == Spellbook.REGULAR && bot.magic.level >= type.level &&
                    !bot.inWilderness())) return true
        val recipient = world.locator.findViewablePlayers(bot) {
            it !== bot && it is Bot && availableRecipient(it)
        }.filterIsInstance<Bot>().shuffled().firstOrNull()
        if (recipient == null) { stop(); return true }
        val agreement = Offer(bot, recipient, type)
        if (!BotSocialInteractions.claim(listOf(bot, recipient), agreement)) { stop(); return true }
        offer = agreement
        offers[recipient] = agreement
        attempted = true
        try {
            if (agreement.originalAid == 0) {
                agreement.changedAid = recipient.output.clickButton(12464)
                if (!waitFor(3.seconds) { recipient.varpManager.getValue(PersistentVarp.ACCEPT_AID) != 0 })
                    return true
            }
            attemptProduction(Rune.LAW.id) {
                agreement.valid() && output.useSpellOnPlayer(BUTTONS.getValue(type), recipient)
            }
            waitFor(15.seconds) {
                !agreement.valid() || recipient.position.isWithinDistance(type.destination, 3) &&
                    !recipient.isLocked && !bot.isLocked
            }
        } finally {
            agreement.close()
            offer = null
        }
        stop()
        return true
    }

    override fun onPaused() { offer?.close(); offer = null }
    override suspend fun finish() { offer?.close(); offer = null }
    override fun snapshot() = TeleOtherData().also {
        it.spell = type.name
        it.attempted = attempted
        saveInventoryState(it)
    }

    /**
     * Runtime-only consent, settings ownership and short mutual reservation for a single offer.
     *
     * @author lare96
     */
    private class Offer(val caster: Bot, val recipient: Bot, val type: TeleOtherType) : BotSocialInteractions.Session {
        private val casterScript = caster.scriptStack.current()
        private val recipientScript = recipient.scriptStack.current()
        private val deadline = System.currentTimeMillis() + 25_000L
        private var closed = false
        val originalAid = recipient.varpManager.getValue(PersistentVarp.ACCEPT_AID)
        val willing = rand(0.1 + recipient.personality.kindness * 0.8)
        var changedAid = false
        var responded = false

        override fun valid(): Boolean = !closed && System.currentTimeMillis() < deadline &&
            listOf(caster, recipient).all { it.state == EntityState.ACTIVE && it.health > 0 && !it.combat.inCombat() } &&
            caster.scriptStack.current() === casterScript && recipient.scriptStack.current() === recipientScript

        override suspend fun participate(bot: Bot) { bot.naturalMicroDelay() }

        override fun close() {
            if (closed) return
            closed = true
            if (offers[recipient] === this) offers.remove(recipient)
            val window = recipient.overlays.getOverlay(TeleOtherInterface::class.java)
            if (window?.source === caster && window.type == type) recipient.overlays.closeWindows()
            if (changedAid && originalAid == 0 && recipient.state == EntityState.ACTIVE &&
                recipient.varpManager.getValue(PersistentVarp.ACCEPT_AID) != 0) recipient.output.clickButton(12465)
            BotSocialInteractions.release(listOf(caster, recipient), this)
        }
    }
}
