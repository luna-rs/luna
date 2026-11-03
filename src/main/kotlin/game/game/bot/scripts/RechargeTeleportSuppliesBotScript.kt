package game.bot.scripts

import api.bot.action.BotNpcDialogues
import api.bot.script.InventoryBotScript
import api.bot.script.InventoryBotScript.Companion.InventoryScriptData
import api.bot.script.StationaryInventoryBotScript
import api.bot.webwalk.model.SpecialTeleport
import api.bot.webwalk.plan.BankItems
import api.bot.zone.SubZone
import api.predef.*
import api.predef.ext.*
import com.google.gson.JsonObject
import game.item.teleport.Camulet
import game.item.teleport.EctophialTeleport
import game.item.teleport.EnchantedLyre
import game.item.teleport.TeleportCrystal
import game.item.teleport.TeleportCrystal.teleportCrystalRechargePrice
import game.item.teleport.TeleportCrystalChantDialogue
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.bot.Bot
import kotlin.time.Duration

/**
 * Restores one owned, depleted native teleport supply through its ordinary item or NPC interaction.
 * Empty Ectophials use the Ectofuntus, tiny crystals pay Eluned, lyres use owned worthy fish offerings,
 * and Camulets use an owned bucket of dung. Existing charged stock is used before another copy is prepared.
 * No quest is started, completed or bypassed by this helper; native handlers retain admission and payment.
 * Shared inventory lifecycle supplies banking, retries and saved visits. One conversion ends the visit.
 *
 * @author lare96
 */
class RechargeTeleportSuppliesBotScript(bot: Bot, val type: SpecialTeleport, duration: Duration) :
    InventoryBotScript(bot, duration, when (type) {
        SpecialTeleport.CRYSTAL -> mutableListOf(SubZone.ELUNED_CLEARING)
        SpecialTeleport.LYRE -> mutableListOf(SubZone.RELLEKKA_STRANGE_ALTAR)
        SpecialTeleport.ECTOPHIAL -> mutableListOf(SubZone.ECTOFUNTUS_ATRIUM)
        SpecialTeleport.CAMULET -> StationaryInventoryBotScript.DEFAULT_ZONES.toMutableList()
    }) {
    override val verifiedProductionWithdrawals = true

    companion object {
        /**
         * Owned teleport family and inherited banking/session retry state, with no live NPC or object persisted.
         *
         * @author lare96
         */
        class RechargeData : InventoryScriptData() {
            /**
             * Owned native teleport family chosen for this visit.
             */
            var type = SpecialTeleport.ECTOPHIAL.name
            /**
             * A finished recharge is not repeated by a resumed visit.
             */
            var completed = false
            override fun load(data: JsonObject) {
                super.load(data)
                type = data.get("type")?.asString ?: SpecialTeleport.ECTOPHIAL.name
                completed = data.get("completed")?.asBoolean ?: false
            }
            override fun save(data: JsonObject) {
                super.save(data)
                data.addProperty("type", type)
                data.addProperty("completed", completed)
            }
        }
    }

    private var completedRecharge = false
    private var chant: TeleportCrystalChantDialogue? = null

    constructor(bot: Bot, data: RechargeData) : this(bot, SpecialTeleport.valueOf(data.type), data.duration) {
        completedRecharge = data.completed
        restoreInventoryState(data)
    }

    /**
     * Chooses only a real, currently owned offering, preferring its greater native number of charges.
     */
    private fun offering(): Int? = EnchantedLyre.OFFERINGS.keys.reversed().firstOrNull { handler.has(it) }

    /**
     * One complete native refill; Eluned's current discount supplies the exact coin requirement.
     */
    private fun materials(): List<Item>? = when (type) {
        SpecialTeleport.CRYSTAL -> listOf(Item(TeleportCrystal.TINY_ELF_CRYSTAL), Item(995, bot.teleportCrystalRechargePrice))
        SpecialTeleport.ECTOPHIAL -> listOf(Item(EctophialTeleport.EMPTY))
        SpecialTeleport.CAMULET -> listOf(Item(Camulet.CAMULET), Item(Camulet.UGTHANKI_DUNG))
        SpecialTeleport.LYRE -> offering()?.let { listOf(Item(EnchantedLyre.LYRE), Item(it)) }
    }

    /**
     * Depleted stock is useful only when no charged form of that family remains in carried or banked supplies.
     */
    fun isEligible(): Boolean = !completedRecharge && type.charges(bot, BankItems.of(bot)) == 0 &&
        materials()?.all { item -> bot.bank.computeAmountForId(item.id).toLong() +
            bot.inventory.computeAmountForId(item.id) + bot.equipment.computeAmountForId(item.id) >= item.amount } == true

    override suspend fun beforeInventoryInit(): Boolean {
        if (!isEligible()) return false
        if (type == SpecialTeleport.CAMULET && !bot.inventory.contains(Camulet.CAMULET) &&
            !bot.bank.contains(Camulet.CAMULET) && bot.equipment[Equipment.AMULET]?.id == Camulet.CAMULET) {
            if (bot.inventory.isFull && !handler.banking.travelToBankDepositAll()) return false
            if (bot.bank.isOpen && !handler.widgets.clickCloseInterface()) return false
            if (!isActionSafe() || isPaused() || isTerminated() ||
                !handler.equipment.unequip(Equipment.AMULET) || !bot.inventory.contains(Camulet.CAMULET)) return false
        }
        return true
    }

    override fun withdraw(): List<Item> {
        val materials = materials()
        if (!isEligible() || materials == null) { stop(); return emptyList() }
        return productionWithdraw(materials, requestMissing = false)
    }

    override fun bankWithdraw(): List<Item> = materials()?.takeIf { bot.bank.containsAll(it) }
        ?: emptyList<Item>().also { stop() }

    override suspend fun onInventoryBankRequested(): Boolean = false

    override suspend fun onExecuteInZone(): Boolean {
        if (completedRecharge || type.charges(bot) > 0) { stop(); return true }
        val materials = materials()
        if (!productionReady(materials != null && bot.inventory.containsAll(materials))) return true
        val before = type.charges(bot)
        val consumed = when (type) {
            SpecialTeleport.CRYSTAL -> TeleportCrystal.TINY_ELF_CRYSTAL
            SpecialTeleport.ECTOPHIAL -> EctophialTeleport.EMPTY
            SpecialTeleport.LYRE -> EnchantedLyre.LYRE
            SpecialTeleport.CAMULET -> Camulet.UGTHANKI_DUNG
        }
        attemptProduction(consumed) {
            when (type) {
                SpecialTeleport.CAMULET -> handler.inventory.useItem(Camulet.UGTHANKI_DUNG).onItem(Camulet.CAMULET)
                SpecialTeleport.ECTOPHIAL -> world.locator.findObjectsOnTile(Position(3658, 3518)) {
                    it.id == EctophialTeleport.ECTOFUNTUS
                }.firstOrNull()?.let { handler.inventory.useItem(EctophialTeleport.EMPTY).onObject(it) } ?: false
                SpecialTeleport.LYRE -> world.locator.findObjectsOnTile(Position(2626, 3598)) { it.id == 4141 }
                    .firstOrNull()?.let { altar -> offering()?.let { handler.inventory.useItem(it).onObject(altar) } } ?: false
                SpecialTeleport.CRYSTAL -> {
                    val npc = world.locator.findNpcs(SubZone.ELUNED_CLEARING.inside, 16) {
                        it.id == TeleportCrystal.ELUNED_ISAFDAR
                    }.firstOrNull()
                    npc != null && BotNpcDialogues.respond(bot, npc,
                        listOf("Yes please.", "Yes - ${bot.teleportCrystalRechargePrice} coins."),
                        allowed = { isActionSafe() }, completed = { type.charges(bot) > before }) {
                        handler.interactions.interact(1, npc)
                    }
                }
            }
        }
        chant = bot.overlays.getOverlay(TeleportCrystalChantDialogue::class.java)
        if (type.charges(bot) > before) { completedRecharge = true; stop() }
        return true
    }

    /**
     * Only Eluned's captured item box belongs to cleanup; unrelated replacement dialogue is preserved.
     */
    private fun closeChant() {
        if (chant != null && bot.overlays.getOverlay(TeleportCrystalChantDialogue::class.java) === chant)
            bot.overlays.closeWindows()
        chant = null
    }

    override fun onPaused() { closeChant() }
    override suspend fun finish() { closeChant() }
    override fun snapshot() = RechargeData().also {
        it.type = type.name
        it.completed = completedRecharge
        saveInventoryState(it)
    }
}
