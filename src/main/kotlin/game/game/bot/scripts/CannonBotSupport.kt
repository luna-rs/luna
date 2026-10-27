package game.bot.scripts

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import api.bot.action.BotCombatActionHandler
import api.bot.Suspendable.naturalDelay
import api.bot.Suspendable.waitFor
import api.bot.script.BotScript.ActionSafety
import api.bot.script.DynamicBotScript
import api.bot.webwalk.plan.BankAccess
import api.bot.zone.SubZone
import api.predef.world
import engine.bot.gear.BotGearPurpose
import engine.bot.gear.BotItemTracker.Companion.itemTracker
import game.content.dwarfMulticannon.Cannon
import game.content.dwarfMulticannon.CannonStage
import game.content.dwarfMulticannon.DwarfMulticannon
import game.content.dwarfMulticannon.DwarfMulticannon.cannonPosition
import game.content.dwarfMulticannon.DwarfMulticannon.cannonStage
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Skill
import io.luna.game.model.mob.bot.Bot
import io.luna.game.model.mob.bot.brain.BotActivity
import io.luna.game.model.mob.bot.brain.BotPersonalityManager.PersonalityTemplateType
import io.luna.game.model.mob.dialogue.OptionDialogue
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Optional owned-cannon support shared by NPC combat sessions and interrupted-cannon recovery.
 * All construction, ammunition, firing and replacement use normal inventory, object or dialogue actions.
 * Native ownership markers remain authoritative after death, decay, logout and server restart.
 *
 * @author lare96
 */
object CannonBotSupport {
    /**
     * The complete user-approved deployment allowlist. Native placement and firing rules still apply.
     */
    val camps = setOf(SubZone.ROCK_CRABS, SubZone.HAM_CULT, SubZone.EDGEVILLE_DUNGEON_HILL_GIANTS,
        SubZone.CHAOS_DRUID_TOWER_DUNGEON, SubZone.KARAMJA_DUNGEON, SubZone.IORWERTH_CAMP)

    /**
     * Recovery destination, recorded before the base click so cancellation cannot orphan a deployment.
     */
    private var Bot.cannonCamp: SubZone? by Attr.nullableObj(SubZone::class).persist("bot_cannon_camp")

    /**
     * Remaining authorized loads for the current outing. Reservations are made before sending a load click.
     */
    private var Bot.cannonAllowance by Attr.int().persist("bot_cannon_allowance")

    /**
     * Conservative wall-clock rebuild deadline, retained across suspension and restart.
     */
    private var Bot.cannonRebuildAt by Attr.long().persist("bot_cannon_rebuild_at")

    /**
     * Validates the saved outing budget and deadline before restored scripts resume cannon recovery.
     * Native ownership and the bot's recorded camp are retained rather than reconstructing equipment.
     */
    @JvmStatic
    fun restoreState(bot: Bot) {
        bot.cannonCamp = bot.cannonCamp
        bot.cannonAllowance = bot.cannonAllowance.coerceIn(0, 1000)
        bot.cannonRebuildAt = bot.cannonRebuildAt.coerceAtLeast(0)
        bot.cannonPosition = bot.cannonPosition
        bot.cannonStage = bot.cannonStage
    }

    /**
     * Uses 0.5–2% of locally owned economy value per outing, capped at 1,000 owned balls.
     * Confidence and combat affinity increase spending; intelligence favors conserving supplies.
     * Invalid prices disable optional cannon use rather than inventing an ammunition price.
     */
    internal fun allowance(wealth: Double, price: Double, owned: Int, confidence: Double,
                           intelligence: Double, likesCombat: Boolean): Int {
        if (!wealth.isFinite() || wealth <= 0 || !price.isFinite() || price <= 0 || owned <= 0) return 0
        val fraction = 0.005 + 0.0075 * confidence.coerceIn(0.0, 1.0) +
            0.005 * (1 - intelligence.coerceIn(0.0, 1.0)) + if (likesCombat) 0.0025 else 0.0
        return minOf(owned, 1000, (wealth * fraction / price).toInt().coerceAtLeast(0))
    }

    /**
     * Uses the shared permanent-build policy for incidental Ranged experience, including saved PK builds.
     * Compatible sessions opt in only when Ranged is their discipline or a preferred skill.
     */
    internal fun eligible(bot: Bot, purpose: BotGearPurpose): Boolean =
        bot.personality.type != PersonalityTemplateType.SKILLER &&
            BotCombatActionHandler.mayGainExperience(bot, Skill.RANGED) &&
            (purpose == BotGearPurpose.RANGED || Skill.RANGED in bot.preferences.skills)

    /**
     * Prepares only owned hardware and ammunition at an already-open bank. Failure leaves ordinary combat available.
     * The outing carries no more balls than its budget, since the native loading menu fills the magazine at once.
     */
    suspend fun prepare(bot: Bot, zones: List<SubZone>, purpose: BotGearPurpose): Boolean {
        if (!eligible(bot, purpose) || zones.none { it in camps } || needsRecovery(bot) ||
            bot.cannonPosition != null || Cannon.ALL[bot.username] != null ||
            !ActionSafety.NON_COMBAT.allows(bot) || !bot.bank.isOpen ||
            !CannonStage.PARTS.all { bot.itemTracker.contains(it) }) return false
        val price = world.economy.getRealPrice(DwarfMulticannon.CANNONBALL)
        val wealth = bot.itemTracker.entrySet().sumOf {
            val value = if (it.element == 995) 1.0 else world.economy.getRealPrice(it.element)
            if (value.isFinite() && value > 0) value * it.count else 0.0
        }
        val count = allowance(wealth, price, bot.itemTracker.count(DwarfMulticannon.CANNONBALL),
            bot.personality.confidence, bot.personality.intelligence,
            bot.preferences.likesActivity(BotActivity.TRAINING_COMBAT) ||
                bot.preferences.likesActivity(BotActivity.PROFIT_COMBAT))
        if (count < DwarfMulticannon.MAX_AMMO) return false
        val supplies = CannonStage.PARTS.map { Item(it) } + Item(DwarfMulticannon.CANNONBALL, count)
        if (!bot.inventory.hasSpaceForAll(supplies)) return false
        val handler = bot.actionHandler
        handler.banking.clickBankingMode(false)
        for (item in supplies) {
            if (!ActionSafety.NON_COMBAT.allows(bot)) return false
            val missing = item.amount - bot.inventory.computeAmountForId(item.id)
            if (missing > 0 && !handler.banking.withdraw(Item(item.id, missing))) return false
        }
        if (bot.inventory.computeAmountForId(DwarfMulticannon.CANNONBALL) > count) return false
        bot.cannonAllowance = count
        return true
    }

    /**
     * Advances one deployment through native actions. False requests banking/reconciliation before further fighting.
     * Unavailable placement disables the optional cannon for that outing; ordinary NPC combat can continue.
     */
    suspend fun maintain(bot: Bot, zone: SubZone): Boolean {
        if (!ActionSafety.COMBAT.allows(bot)) return true
        if (zone !in camps) return !needsRecovery(bot)
        if (!BotCombatActionHandler.mayGainExperience(bot, Skill.RANGED)) return false
        var cannon = Cannon.ALL[bot.username]
        if (cannon == null) {
            if (bot.cannonPosition != null) return false
            if (!CannonStage.PARTS.all { it in bot.inventory } || bot.cannonAllowance <= 0) return true
            val positions = listOf(bot.position, zone.inside) + List(10) {
                zone.inside.translate((-6..6).random(), (-6..6).random())
            }
            val centre = positions.firstOrNull { validPlacement(zone, it) }
            if (centre == null) {
                bot.cannonAllowance = 0
                return true
            }
            if (centre != bot.position && !bot.actionHandler.webWalkTo(centre, bank = BankAccess.COINS)) {
                bot.cannonAllowance = 0
                return true
            }
            if (!ActionSafety.COMBAT.allows(bot) || bot.position != centre || !validPlacement(zone, centre)) return true
            bot.cannonCamp = zone
            // A wall-clock deadline is conservative when the server is stopped or ticks are delayed.
            bot.cannonRebuildAt = System.currentTimeMillis() + 20 * 60_000L
            if (!bot.actionHandler.inventory.clickItem(1, CannonStage.BASE.partId)) return false
            if (!waitFor(15.seconds) { Cannon.ALL[bot.username] != null }) return false
            cannon = Cannon.ALL[bot.username] ?: return false
        }
        if (bot.cannonCamp != zone || System.currentTimeMillis() >= bot.cannonRebuildAt) return false
        var active = cannon ?: return false
        while (active.stage.next != null) {
            if (!ActionSafety.COMBAT.allows(bot) || bot.emotions.isNervousAboutHp) return false
            val next = active.stage.next ?: break
            if (next.partId !in bot.inventory ||
                !bot.actionHandler.inventory.useItem(next.partId).onObject(active.obj) ||
                !waitFor(5.seconds) { Cannon.ALL[bot.username]?.stage == next }) return false
            active = Cannon.ALL[bot.username] ?: return false
        }
        if (!ActionSafety.COMBAT.allows(bot) || bot.emotions.isNervousAboutHp) return false
        val held = bot.inventory.computeAmountForId(DwarfMulticannon.CANNONBALL)
        if (active.ammo == 0 && (held == 0 || bot.cannonAllowance <= 0)) return false
        // Reload only an empty magazine. Otherwise shots fired while approaching could increase the native
        // load beyond the amount reserved before navigation started.
        if (active.ammo == 0 && held > 0 && bot.cannonAllowance > 0) {
            if (held > bot.cannonAllowance) return false
            val reserved = minOf(held, DwarfMulticannon.MAX_AMMO)
            bot.cannonAllowance -= reserved
            val before = held
            if (!bot.actionHandler.inventory.useItem(DwarfMulticannon.CANNONBALL).onObject(active.obj) ||
                !waitFor(5.seconds) { bot.inventory.computeAmountForId(DwarfMulticannon.CANNONBALL) < before }) return false
        }
        cannon = Cannon.ALL[bot.username] ?: return false
        if (!ActionSafety.COMBAT.allows(bot)) return true
        return cannon.firing || (bot.actionHandler.interactions.interact(1, cannon.obj) &&
            waitFor(5.seconds) { Cannon.ALL[bot.username]?.firing == true })
    }

    /**
     * Requires the full footprint to stay in the approved camp; native collision, restrictions and setup movement
     * remain authoritative and are checked again at the bot's actual destination.
     */
    private fun validPlacement(zone: SubZone, centre: Position): Boolean =
        (-1..1).all { x -> (-1..1).all { y -> centre.translate(x, y) in zone.area } } &&
            DwarfMulticannon.checkSetUp(centre) == null && DwarfMulticannon.findSetUpStep(centre) != null

    /**
     * Includes the base-click intent while native setup is still locked and has not published its ownership markers.
     * Only bot-managed cannons create an obligation; unrelated player cannon state is untouched.
     */
    @JvmStatic
    fun needsRecovery(bot: Bot): Boolean = bot.cannonCamp != null

    /**
     * Counts all returned parts plus ammunition before pickup, including stacking with carried cannonballs.
     */
    internal fun pickupFits(bot: Bot, stage: CannonStage, ammo: Int): Boolean =
        bot.inventory.hasSpaceForAll(stage.parts.map { Item(it) } +
            if (ammo > 0) listOf(Item(DwarfMulticannon.CANNONBALL, ammo)) else emptyList())

    /**
     * Bounded, cancellable recovery. A full inventory may be banked before returning; decay instead leads through
     * Nulodion's normal lost-cannon dialogue. Failed or interrupted recovery retains both native and bot markers.
     */
    suspend fun recover(bot: Bot): Boolean {
        if (!needsRecovery(bot)) return true
        return withTimeoutOrNull(120.seconds) {
            if (!ActionSafety.COMBAT.allows(bot) || (bot.combat.inCombat() && bot.emotions.isNervousAboutHp)) {
                return@withTimeoutOrNull false
            }
            if (bot.cannonPosition == null && bot.cannonStage == null && Cannon.ALL[bot.username] == null) {
                // Strong setup has finished or failed; no native deployment was created.
                bot.cannonCamp = null
                bot.cannonAllowance = 0
                return@withTimeoutOrNull true
            }
            val handler = bot.actionHandler
            // Do not return an injured bot to an aggressive camp just to collect hardware.
            // Existing food reflexes finish healing after an owned-food withdrawal.
            if (bot.emotions.isNervousAboutHp) {
                if (!handler.inventory.eatAnyFood()) {
                    if (!handler.banking.travelToBankDepositAll()) return@withTimeoutOrNull false
                    handler.banking.withdrawAnyFood(amount = 8)
                }
                return@withTimeoutOrNull false
            }
            var cannon = Cannon.ALL[bot.username]
            val stage = cannon?.stage ?: bot.cannonStage ?: return@withTimeoutOrNull false
            if (!pickupFits(bot, stage, cannon?.ammo ?: 0)) {
                if (!handler.banking.travelToBankDepositAll()) return@withTimeoutOrNull false
            }
            cannon = Cannon.ALL[bot.username]
            if (cannon != null) {
                val camp = bot.cannonCamp ?: return@withTimeoutOrNull false
                if (!handler.travelTo(camp, BankAccess.COINS)) return@withTimeoutOrNull false
                cannon = Cannon.ALL[bot.username]
            }
            if (cannon != null) {
                if (!ActionSafety.COMBAT.allows(bot) || !pickupFits(bot, cannon.stage, cannon.ammo)) return@withTimeoutOrNull false
                if (!handler.interactions.interact(if (cannon.stage == CannonStage.FURNACE) 2 else 1, cannon.obj)) {
                    return@withTimeoutOrNull false
                }
            } else {
                if (!ActionSafety.NON_COMBAT.allows(bot) || !handler.travelTo(SubZone.ICE_MOUNTAIN, BankAccess.COINS)) {
                    return@withTimeoutOrNull false
                }
                if (!ActionSafety.NON_COMBAT.allows(bot) || Cannon.ALL[bot.username] != null ||
                    !pickupFits(bot, stage, 0)) return@withTimeoutOrNull false
                val npc = world.locator.findNpcs(SubZone.ICE_MOUNTAIN.inside, SubZone.ICE_MOUNTAIN.area.tileRadius) {
                    it.id == DwarfMulticannon.NULODION
                }.firstOrNull() ?: return@withTimeoutOrNull false
                if (!handler.interactions.interact(1, npc) ||
                    !waitFor(40.seconds) { bot.overlays.has(OptionDialogue::class.java) } ||
                    !handler.widgets.clickDialogueOption("I've lost my cannon.")) return@withTimeoutOrNull false
            }
            if (!waitFor(40.seconds) { bot.cannonPosition == null && Cannon.ALL[bot.username] == null }) {
                return@withTimeoutOrNull false
            }
            bot.cannonCamp = null
            bot.cannonAllowance = 0
            true
        } ?: false
    }
}

/**
 * Retries a persisted cannon recovery before the brain selects another ordinary activity.
 * Native ownership attributes recreate this obligation even when a dynamic script is not saved.
 * Combat reflexes may still interrupt recovery immediately.
 *
 * @author lare96
 */
class RecoverCannonBotScript(bot: Bot) : DynamicBotScript(bot) {
    override suspend fun run(): Boolean {
        if (CannonBotSupport.recover(bot)) return true
        bot.naturalDelay()
        return false
    }
}
