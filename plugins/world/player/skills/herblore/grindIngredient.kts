import api.*
import io.luna.game.action.Action
import io.luna.game.action.ProducingAction
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.MakeItemDialogueInterface
import world.player.skills.herblore.Ingredient

/**
 * A [ProducingAction] that will grind all ingredients in an inventory.
 */
class GrindAction(plr: Player,
                  val ingredient: Ingredient,
                  var makeTimes: Int) : ProducingAction(plr, true, 2) {

    companion object {

        /**
         * The grinding animation.
         */
        val ANIMATION = Animation(364)
    }

    override fun canProduce() =
        when {
            !mob.inventory.contains(Ingredient.PESTLE_AND_MORTAR) -> false
            makeTimes == 0 -> false
            else -> true
        }


    override fun onProduce() {
        val oldName = itemDef(ingredient.id)?.name
        val newName = itemDef(ingredient.newId)?.name
        val nextWord = if (ingredient.newId == 6693) "a" else "some"
        mob.sendMessage("You grind the $oldName into $nextWord $newName.")

        mob.animation(ANIMATION)
        makeTimes--
    }

    override fun remove() = arrayOf(ingredient.oldItem)

    override fun add() = arrayOf(ingredient.newItem)

    override fun isEqual(other: Action<*>) =
        when (other) {
            is GrindAction -> ingredient == other.ingredient
            else -> false
        }
}

/**
 * Opens a [MakeItemDialogueInterface] for grinding ingredients.
 */
fun grind(msg: ItemOnItemEvent, id: Int) {
    val ingredient = Ingredient.OLD_TO_INGREDIENT[id]
    if (ingredient != null) {
        val plr = msg.plr
        plr.interfaces.open(object : MakeItemDialogueInterface(ingredient.newId) {
            override fun makeItem(player: Player, id: Int, index: Int, forAmount: Int) =
                plr.submitAction(GrindAction(plr, ingredient, forAmount))
        })
        msg.terminate()
    }
}

/**
 * Intercept event for using ingredient with pestle and mortar.
 */
on(ItemOnItemEvent::class).run {
    when (Ingredient.PESTLE_AND_MORTAR) {
        it.usedId -> grind(it, it.targetId)
        it.targetId -> grind(it, it.usedId)
    }
}
