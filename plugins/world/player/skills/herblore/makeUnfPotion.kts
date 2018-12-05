import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.ProducingAction
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import world.player.skills.herblore.UnfPotion

/**
 * A [ProducingAction] that will make unfinished potions.
 */
class MakeUnfAction(plr: Player,
                    val unfPotion: UnfPotion,
                    var makeTimes: Int) : ProducingAction(plr, true, 2) {

    companion object {

        /**
         * The unfinished potion making animation.
         */
        val ANIMATION = Animation(363)
    }

    /**
     * The herblore skill.
     */
    val herblore = mob.skill(SKILL_HERBLORE)

    override fun canInit() =
        when {
            herblore.level < unfPotion.level -> {
                mob.sendMessage("You need a Herblore level of ${unfPotion.level} to make this potion.")
                false
            }
            else -> true
        }

    override fun onProduce() {
        mob.sendMessage("You put the ${itemDef(unfPotion.herb).name} into the vial of water.")
        mob.animation(ANIMATION)
    }

    override fun add() = arrayOf(unfPotion.idItem)

    override fun remove() = arrayOf(unfPotion.herbItem, Item(UnfPotion.VIAL_OF_WATER))

    override fun isEqual(other: Action<*>) =
        when (other) {
            is MakeUnfAction -> unfPotion == other.unfPotion
            else -> false
        }
}

/**
 * Opens a [MakeItemDialogueInterface] to make unfinished potions.
 */
fun makeUnf(msg: ItemOnItemEvent, herb: Int) {
    val unfPotion = UnfPotion.HERB_TO_UNF[herb]
    if (unfPotion != null) {
        val plr = msg.plr
        plr.openInterface(object : MakeItemDialogueInterface(unfPotion.id) {
            override fun makeItem(player: Player, id: Int, index: Int, forAmount: Int) =
                plr.submitAction(MakeUnfAction(plr, unfPotion, forAmount))
        })
        msg.terminate()
    }
}

/**
 * Intercept event to make unf. potions if the required items are present.
 */
on(ItemOnItemEvent::class) {
    when (UnfPotion.VIAL_OF_WATER) {
        it.targetId -> makeUnf(it, it.usedId)
        it.usedId -> makeUnf(it, it.targetId)
    }
}
