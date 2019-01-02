package world.player.item.fill

import api.predef.*
import io.luna.game.event.impl.ItemOnObjectEvent
import io.luna.game.model.def.ObjectDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface

abstract class Source {

    abstract fun matchesDef(def: ObjectDefinition): Boolean
    abstract fun getFilled(empty: Int): Int?

    open fun onFill(plr: Player) {

    }

    fun fill(plr: Player, emptyId: Int, objectId: Int? = null, msg: ItemOnObjectEvent? = null) {
        val filled = getFilled(emptyId)
        if (filled != null) {
            plr.interfaces.open(object : MakeItemDialogueInterface(filled) {
                override fun makeItem(player: Player, id: Int, index: Int, forAmount: Int) {
                    val emptyItem = Item(emptyId)
                    val filledItem = Item(filled)
                    plr.submitAction(FillAction(plr, objectId, emptyItem, filledItem, this@Source, forAmount))
                }
            })
            msg?.terminate()
        }
    }

    fun register() {
        for (obj in ObjectDefinition.ALL) {
            if (matchesDef(obj)) {
                on(ItemOnObjectEvent::class)
                    .condition { objectId == obj.id }
                    .then { fill(plr, objectId, itemId, this) }
            }
        }
    }
}