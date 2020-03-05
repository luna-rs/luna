package world.player.skill.crafting.gemCutting

import api.predef.*
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import world.player.skill.crafting.gemCutting.Gem.Companion.UNCUT_TO_GEM

// Use chisel with gems.
on(ItemOnItemEvent::class) {
    val gem = lookup(UNCUT_TO_GEM)
    if (gem != null) {
        plr.interfaces.open(object : MakeItemDialogueInterface(gem.cut) {
            override fun makeItem(player: Player?, id: Int, index: Int, forAmount: Int) =
                plr.submitAction(CutGemAction(plr, gem, forAmount))
        })
    }
}
