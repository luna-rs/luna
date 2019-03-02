package world.player.skill.fletching.cutLog

import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.ProducingAction
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import world.player.skill.fletching.attachArrow.Arrow
import world.player.skill.fletching.stringBow.Bow

/**
 * A [ProducingAction] implementation that cuts logs.
 */
class CutLogAction(plr: Player,
                   val log: Int,
                   val bow: Bow,
                   makeTimes: Int) : ProducingAction(plr, true, 3, makeTimes) {

    companion object {

        /**
         * The log cutting animation.
         */
        val ANIMATION = Animation(6782)
    }

    override fun add(): Array<Item> {
        val unstrungItem =
            when (bow) {
                Bow.ARROW_SHAFT -> Item(bow.unstrung, Arrow.SET_AMOUNT)
                else -> Item(bow.unstrung)
            }
        return arrayOf(unstrungItem)
    }

    override fun remove() = arrayOf(Item(log))

    override fun canProduce() =
        when {
            mob.fletching.level < bow.level -> {
                mob.sendMessage("You need a Fletching level of ${bow.level} to cut this.")
                false
            }
            else -> true
        }


    override fun onProduce() {
        val unstrungName = itemDef(bow.unstrung).name
        mob.sendMessage("You carefully cut the wood into ${addArticle(unstrungName)}.")

        mob.animation(ANIMATION)
        mob.fletching.addExperience(bow.exp)
    }

    override fun isEqual(other: Action<*>) =
        when (other) {
            is CutLogAction -> log == other.log && bow == other.bow
            else -> false
        }
}

/**
 * Opens a [MakeItemDialogueInterface] for cutting logs.
 */
fun openInterface(msg: ItemOnItemEvent, id: Int) {
    val log = Log.ID_TO_LOG[id]
    if (log != null) {
        val interfaces = msg.plr.interfaces
        interfaces.open(object : MakeItemDialogueInterface(*log.unstrungIds) {
            override fun makeItem(plr: Player, id: Int, index: Int, forAmount: Int) =
                plr.submitAction(CutLogAction(plr, log.id, log.bows[index], forAmount))
        })
    }
}

// Intercept item on item event to open interface.
on(ItemOnItemEvent::class) {
    when (Log.KNIFE) {
        targetId -> openInterface(this, usedId)
        usedId -> openInterface(this, targetId)
    }
}