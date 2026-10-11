package game.skill.crafting.jewelleryMaking

import api.predef.*
import api.predef.ext.*
import game.player.Animations
import game.player.Sound
import game.skill.smithing.BarType
import io.luna.game.action.impl.ItemContainerAction.InventoryAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

/**
 * Crafts one configured gold or silver jewellery recipe using the existing inventory action.
 *
 * The selected recipe must belong to the supplied metal table. Its exact mould and current Crafting level
 * are checked on submission and before every conversion, so an open interface cannot bypass requirements.
 * Moulds are retained; the inventory action consumes the bar and optional gem only after checking supplies
 * and output space. Successful cycles retain the existing timing, animation, sound, and recipe experience.
 *
 * @author lare96
 */
class CraftJewelleryAction(plr: Player, private val barType: BarType,
                           private val jewellery: JewelleryItem, times: Int) :
    InventoryAction(plr, true, 3, times) {

    /** Mould for this output in the supplied metal table, or null for an unsupported recipe/metal pair. */
    private val requiredMould: Int? = when (barType) {
        BarType.GOLD -> GoldJewelleryTable.VALUES.firstOrNull { table ->
            table.jewelleryItems.any { it.id == jewellery.id }
        }?.mouldId
        BarType.SILVER -> SilverJewelleryTable.ID_TO_TABLE[jewellery.id]?.mouldId
        else -> null
    }

    override fun executeIf(start: Boolean): Boolean =
        when {
            requiredMould == null -> false
            mob.crafting.level < jewellery.level -> {
                mob.sendMessage("You need a Crafting level of ${jewellery.level} to make this.")
                false
            }

            !mob.inventory.contains(requiredMould) -> {
                mob.sendMessage("You need ${addArticle(requiredMould)} to make this.")
                false
            }

            else -> true
        }

    override fun execute() {
        mob.playSound(Sound.FURNACE)
        mob.animation(Animations.SMELT)
        mob.crafting.addExperience(jewellery.xp)
    }

    override fun add(): List<Item> = listOf(jewellery.item)

    override fun remove(): List<Item> {
        val items = ArrayList<Item>()
        items += Item(barType.id) // Recipe metal bar.
        if (jewellery.requiredItem != null) {
            items += jewellery.requiredItem
        }
        return items
    }
}