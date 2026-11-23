package game.skill.cooking.churn

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.game.model.`object`.GameObject
import io.luna.net.msg.out.WidgetItemModelMessageWriter

/**
 * The churn's own menu. The client has one for each starting ingredient: milk offers cream, butter and cheese,
 * cream offers butter and cheese, and butter offers cheese alone.
 */
class ChurnInterface(val from: Dairy, val churn: GameObject) : DialogueInterface(WIDGETS.getValue(from)) {

    override fun init(plr: Player): Boolean {
        products().forEachIndexed { index, product ->
            plr.queue(WidgetItemModelMessageWriter(MODELS.getValue(from)[index], 170, product.id))
        }
        return true
    }

    /**
     * The products on this menu, left to right.
     */
    fun products() = Dairy.entries.filter { it.ordinal > from.ordinal }

    companion object {
        private val WIDGETS = mapOf(Dairy.MILK to 15336, Dairy.CREAM to 15328, Dairy.BUTTER to 15322)

        private val MODELS = mapOf(
            Dairy.MILK to listOf(15339, 15340, 15341),
            Dairy.CREAM to listOf(15331, 15332),
            Dairy.BUTTER to listOf(15325))

        /**
         * The option under each product, mapped to the menu it is on and its position there.
         */
        val BUTTONS = mapOf(
            15342 to (Dairy.MILK to 0), 15343 to (Dairy.MILK to 1), 15344 to (Dairy.MILK to 2),
            15333 to (Dairy.CREAM to 0), 15334 to (Dairy.CREAM to 1),
            15326 to (Dairy.BUTTER to 0))
    }
}
