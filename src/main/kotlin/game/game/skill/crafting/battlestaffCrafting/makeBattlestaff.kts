package game.skill.crafting.battlestaffCrafting

import api.predef.*
import io.luna.game.model.mob.Player
import game.skill.crafting.battlestaffCrafting.Battlestaff.Companion.BATTLESTAFF
import game.skill.crafting.battlestaffCrafting.Battlestaff.Companion.ORB_TO_BATTLESTAFF
import io.luna.game.model.mob.dialogue.MakeItemDialogue

// Use battlestaff with orb.
ORB_TO_BATTLESTAFF.entries.forEach {
    val orbId = it.key
    useItem(BATTLESTAFF).onItem(orbId) {
        val bs = it.value
        plr.overlays.open(object : MakeItemDialogue(bs.staff) {
            override fun make(player: Player?, id: Int, index: Int, forAmount: Int) =
                plr.submitAction(MakeBattlestaffActionItem(plr, bs, forAmount))
        })
    }
}