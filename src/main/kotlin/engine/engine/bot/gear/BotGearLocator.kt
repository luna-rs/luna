package engine.bot.gear

import api.bot.script.BotScript.ActionSafety
import api.predef.itemDef
import io.luna.game.model.EntityState
import io.luna.game.model.def.EquipmentDefinition
import io.luna.game.model.item.Equipment.SHIELD
import io.luna.game.model.item.Equipment.WEAPON
import io.luna.game.model.item.Item
import io.luna.game.model.mob.bot.Bot
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/**
 * Obtains real selected equipment before replacing working worn pieces through native equip actions.
 * Missing withdrawals leave the previous piece equipped while other valid replacements can still succeed.
 * Notes are deposited and withdrawn unnoted; all displaced or interrupted cargo remains owned.
 *
 * Null slots retain compatible worn pieces. Purpose-based layouts may remove an incompatible piece only
 * after all requested replacements are ready; exact partial layouts do not clear unrelated slots.
 * Strict encounter layouts can explicitly clear unselected slots after their replacements succeed.
 *
 * @param bot The account preparing its gear.
 * @param equipment Concrete unnoted equipment ids indexed by native equipment slot.
 * @param purposes The primary purposes of a selected outfit, or empty for an exact partial layout.
 * @param clearUnselected Whether a strict complete outfit must remove every unselected old piece.
 * @author lare96
 */
class BotGearLocator @JvmOverloads constructor(
    val bot: Bot,
    val equipment: Array<Int?>,
    private val purposes: Set<BotGearPurpose> = emptySet(),
    private val clearUnselected: Boolean = false
) {
    /**
     * Applies the available valid pieces within a bounded bank visit, retaining working gear on partial failure.
     */
    suspend fun locateAndEquip(): Boolean = locateAndEquip { true }

    /**
     * Applies the layout while its owning activity still permits native equipment actions.
     */
    suspend fun locateAndEquip(allowed: () -> Boolean): Boolean {
        fun permitted() = allowed() && bot.state == EntityState.ACTIVE &&
            !bot.client.isPendingLogout && ActionSafety.NON_COMBAT.allows(bot)
        if (!permitted()) return false
        if (equipment.all { it == null } && purposes.isEmpty() && !clearUnselected) return true
        return withTimeoutOrNull(120.seconds) {
            val banking = bot.actionHandler.banking
            if (!banking.travelToBankOpen() || !permitted() || !bot.bank.isOpen) return@withTimeoutOrNull false
            val requested = equipment.withIndex().filter { it.value != null }
            val ids = requested.mapNotNull { it.value }.toSet()
            banking.depositInventory(ids)
            if (!permitted() || !bot.bank.isOpen) return@withTimeoutOrNull false

            val weapon = equipment.getOrNull(WEAPON)
            val conflicting = weapon?.let { EquipmentDefinition.ALL.get(it).orElse(null)?.isTwoHanded } == true &&
                equipment.getOrNull(SHIELD) != null
            var success = !conflicting
            val ready = linkedMapOf<Int, Int>()
            for ((slot, selected) in requested) {
                val id = selected!!
                if (!permitted() || !bot.bank.isOpen) return@withTimeoutOrNull false
                val definition = EquipmentDefinition.ALL.get(id).orElse(null)
                if (definition == null || definition.index != slot || !definition.meetsAllRequirements(bot) ||
                    itemDef(id).isNoted || conflicting && slot in setOf(WEAPON, SHIELD)) {
                    success = false
                    continue
                }
                val worn = bot.equipment[slot]?.takeIf { it.id == id }
                val stackable = itemDef(id).isStackable
                if (worn != null && (!stackable || worn.amount >= 500)) {
                    ready[slot] = id
                    continue
                }
                val quantity = if (stackable)
                    (500L - (worn?.amount ?: 0) - bot.inventory.computeAmountForId(id)).coerceAtLeast(0L).toInt()
                    else if (bot.inventory.contains(id)) 0 else 1
                if (quantity > 0) {
                    banking.withdrawUnnoted(Item(id, quantity), ::permitted)
                }
                if (!permitted()) return@withTimeoutOrNull false
                if (worn != null || bot.inventory.contains(id)) ready[slot] = id else success = false
            }

            if (!permitted()) return@withTimeoutOrNull false
            bot.overlays.closeWindows()
            for ((slot, id) in ready) {
                if (!permitted()) return@withTimeoutOrNull false
                if (bot.equipment[slot]?.id == id &&
                    (!itemDef(id).isStackable || !bot.inventory.contains(id))) continue
                val definition = EquipmentDefinition.ALL.get(id).orElse(null)
                if (definition == null || !definition.meetsAllRequirements(bot) || !bot.inventory.contains(id)) {
                    success = false
                    continue
                }
                if (slot == SHIELD && bot.equipment[WEAPON]?.equipDef?.isTwoHanded == true) {
                    success = false
                    continue
                }
                if (!bot.actionHandler.equipment.equip(id) || bot.equipment[slot]?.id != id) success = false
            }

            if (success && (clearUnselected || purposes.isNotEmpty())) {
                for ((slot, item) in bot.equipment.withIndex()) {
                    if (item == null || equipment.getOrNull(slot) != null) continue
                    val gear = BotGearSelector.ALL_GEAR_IDS.value[item.id]
                    if (!clearUnselected && gear != null && BotEquipmentPreferences.supports(gear, purposes)) continue
                    if (!permitted()) return@withTimeoutOrNull false
                    if (!bot.actionHandler.equipment.unequip(slot)) success = false
                }
            }
            success && permitted() && requested.all { (slot, id) -> bot.equipment[slot]?.id == id }
        } == true
    }
}
