package world.player.skill.magic.lowHighAlch

import api.attr.Attr
import io.luna.game.action.QueuedAction
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player

class AlchemyAction(plr: Player, val type: AlchemyType) : QueuedAction<Player>(plr, plr.alchemyDelay, 5) {
 // todo everything
    companion object {
        val Player.alchemyDelay by Attr.timeSource()
    }

    override fun execute() {
        TODO("Not yet implemented")
    }


    fun getValue(item: Item, type: AlchemyType): Int = when (type) {
        AlchemyType.LOW -> item.itemDef.value
        AlchemyType.HIGH -> item.itemDef.value
    }
}