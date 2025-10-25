package game.skill.crafting.gemCutting

import api.predef.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import game.skill.crafting.gemCutting.Gem.Companion.CHISEL
import game.skill.crafting.gemCutting.Gem.Companion.UNCUT_TO_GEM

// Use chisel with gems.
UNCUT_TO_GEM.entries.forEach {
    val uncutId = it.key
    useItem(CHISEL).onItem(uncutId) {
        val gem = it.value
        plr.interfaces.open(object : MakeItemDialogueInterface(gem.cut) {
            override fun makeItem(player: Player?, id: Int, index: Int, forAmount: Int) =
                plr.submitAction(CutGemActionItem(plr, gem, forAmount))
        })
    }
}
