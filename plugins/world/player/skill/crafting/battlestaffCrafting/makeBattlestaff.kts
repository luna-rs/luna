package world.player.skill.crafting.battlestaffCrafting

import api.predef.*
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import world.player.skill.crafting.battlestaffCrafting.Battlestaff.Companion.ORB_TO_BATTLESTAFF

// Use battlestaff with orb.
on(ItemOnItemEvent::class) {
    val battlestaff = lookup(ORB_TO_BATTLESTAFF)
    if (battlestaff != null) {
        plr.interfaces.open(object : MakeItemDialogueInterface(battlestaff.staff) {
            override fun makeItem(player: Player?, id: Int, index: Int, forAmount: Int) =
                plr.submitAction(MakeBattlestaffAction(plr, battlestaff, forAmount))
        })
    }
}