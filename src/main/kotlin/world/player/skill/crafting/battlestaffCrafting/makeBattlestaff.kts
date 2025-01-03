package world.player.skill.crafting.battlestaffCrafting

import api.predef.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import world.player.skill.crafting.battlestaffCrafting.Battlestaff.Companion.BATTLESTAFF
import world.player.skill.crafting.battlestaffCrafting.Battlestaff.Companion.ORB_TO_BATTLESTAFF

// Use battlestaff with orb.
ORB_TO_BATTLESTAFF.entries.forEach {
    val orbId = it.key
    useItem(BATTLESTAFF).onItem(orbId) {
        val bs = it.value
        plr.interfaces.open(object : MakeItemDialogueInterface(bs.staff) {
            override fun makeItem(player: Player?, id: Int, index: Int, forAmount: Int) =
                plr.submitAction(MakeBattlestaffActionItem(plr, bs, forAmount))
        })
    }
}