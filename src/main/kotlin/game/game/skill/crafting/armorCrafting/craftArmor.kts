package game.skill.crafting.armorCrafting

import api.predef.*
import io.luna.game.model.mob.Player
import game.skill.crafting.hideTanning.Hide
import io.luna.game.model.mob.dialogue.MakeItemDialogue

/**
 * Opens a [MakeItemDialogueInterface] for crafting armor.
 */
fun craftArmor(plr: Player, hide: Hide) {
    when (hide) {
        Hide.SOFT_LEATHER -> {
            // Soft leather, because it uses a different interface.
            plr.overlays.open(SoftLeatherInterface())
        }

        else -> {
            // All other hides.
            val ids = HideArmor.HIDE_TO_ARMOR[hide]!!
            plr.overlays.open(object : MakeItemDialogue(*ids) {
                override fun make(player: Player, id: Int, index: Int, forAmount: Int) {
                    val hideArmor = HideArmor.ID_TO_ARMOR[id]!!
                    if (hideArmor.hides != null) {
                        val invAmount = player.inventory.computeAmountForId(hide.tan)
                        val reqAmount = hideArmor.hides.second
                        if (invAmount < reqAmount) {
                            player.sendMessage("You need at least $reqAmount hides to make this.")
                            return
                        }
                    }
                    plr.submitAction(CraftArmorActionItem(plr, hideArmor, forAmount))
                }
            })
        }
    }
}

// Handle all armor crafting.
Hide.TAN_TO_HIDE.entries.forEach {
    useItem(CraftArmorActionItem.NEEDLE_ID).onItem(it.key) { craftArmor(plr, it.value) }
}