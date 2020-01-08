package world.player.skill.herblore.makePotion

import api.predef.*
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface

/**
 * Opens a [MakeItemDialogueInterface] to make finished potions.
 */
fun makePotion(plr: Player, potion: Potion) {
    plr.interfaces.open(object : MakeItemDialogueInterface(potion.id) {
        override fun makeItem(plr: Player, id: Int, index: Int, forAmount: Int) =
            plr.submitAction(MakePotionAction(plr, potion, forAmount))
    })
}

/**
 * Start a [MakePotionAction] if the intercepted event contains the required items.
 */
on(ItemOnItemEvent::class) {
    val potion = Potion.getPotion(usedId, targetId)
    if (potion != null) {
        makePotion(plr, potion)
    }
}
