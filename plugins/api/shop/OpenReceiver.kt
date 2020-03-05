package api.shop

import api.predef.*
import io.luna.game.event.impl.PlayerEvent
import io.luna.game.model.item.shop.Shop
import io.luna.game.model.item.shop.ShopInterface

/**
 * The receiver for the [ShopReceiver.open] closure that encapsulates how the shop will be opened.
 *
 * @author lare96
 */
class OpenReceiver {

    // These properties are mapped to functions in EventPredef.kt with the same name.
    var button: Int? = null
    var npc1: Int? = null
    var npc2: Int? = null
    var npc3: Int? = null
    var npc4: Int? = null
    var npc5: Int? = null
    var object1: Int? = null
    var object2: Int? = null
    var object3: Int? = null

    /**
     * Maps all properties to their respective event listeners.
     */
    fun addListeners(shop: Shop) {
        val open: PlayerEvent.() -> Unit = { plr.interfaces.open(ShopInterface(shop)) }
        when {
            button != null -> button(button!!, open)
            npc1 != null -> npc1(npc1!!, open)
            npc2 != null -> npc2(npc2!!, open)
            npc3 != null -> npc3(npc3!!, open)
            npc4 != null -> npc4(npc4!!, open)
            npc5 != null -> npc5(npc5!!, open)
            object1 != null -> object1(object1!!, open)
            object2 != null -> object2(object2!!, open)
            object3 != null -> object3(object3!!, open)
        }
    }
}