package world.player.skill.crafting.armorCrafting

import api.predef.*
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import world.player.skill.crafting.hideTanning.Hide

/**
 * Opens a [MakeItemDialogueInterface] for crafting armor.
 */
fun craftArmor(plr: Player, hide: Hide) {
    when (hide) {
        Hide.SOFT_LEATHER -> {
            // Soft leather, because it uses a different interface.
            plr.interfaces.open(SoftLeatherInterface())
        }
        else -> {
            // All other hides.
            val ids = HideArmor.HIDE_TO_ARMOR[hide]!!
            plr.interfaces.open(object : MakeItemDialogueInterface(*ids) {
                override fun makeItem(player: Player?, id: Int, index: Int, forAmount: Int) {
                    plr.submitAction(CraftArmorAction(plr, HideArmor.ID_TO_ARMOR[id]!!, forAmount))
                }
            })
        }
    }
}

// Handle all armor crafting.
on(ItemOnItemEvent::class)
    .filter { matches(CraftArmorAction.NEEDLE_ID) }
    .then {
        val result = lookup(Hide.TAN_TO_HIDE)
        if (result != null) {
            craftArmor(plr, result)
        }
    }