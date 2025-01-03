package world.player.skill.crafting.armorCrafting

import api.predef.*
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
                override fun makeItem(player: Player, id: Int, index: Int, forAmount: Int) {
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