package game.skill.fletching.ogre

import api.predef.*
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueInterface
import io.luna.net.msg.out.WidgetItemModelMessageWriter
import io.luna.net.msg.out.WidgetTextMessageWriter

/**
 * A [DialogueInterface] that asks how many sets of wolf bones to chisel: "How many would you like to make?" with
 * Make 1, Make 5, Make X and Make All.
 *
 * @author lare96
 */
class ChiselBonesMenu : DialogueInterface(4429) {

    companion object {

        /**
         * The item model and the "Make All" button, which also carries the item's name.
         */
        const val MODEL = 1746
        const val MAKE_ALL = 1747

        /**
         * The other buttons.
         */
        const val MAKE_X = 1748
        const val MAKE_5 = 2798
        const val MAKE_1 = 2799
    }

    override fun init(plr: Player): Boolean {
        plr.queue(WidgetItemModelMessageWriter(MODEL, 190, OgreFletching.WOLFBONE_ARROWTIPS))
        plr.queue(WidgetTextMessageWriter(itemName(OgreFletching.WOLFBONE_ARROWTIPS), MAKE_ALL))
        return true
    }
}
