package api.shop.dsl

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
    val button: ArrayList<Int> = ArrayList(4)
    val npc1: ArrayList<Int> = ArrayList(4)
    val npc2: ArrayList<Int> = ArrayList(4)
    val npc3: ArrayList<Int> = ArrayList(4)
    val npc4: ArrayList<Int> = ArrayList(4)
    val npc5: ArrayList<Int> = ArrayList(4)
    val object1: ArrayList<Int> = ArrayList(4)
    val object2: ArrayList<Int> = ArrayList(4)
    val object3: ArrayList<Int> = ArrayList(4)

    /**
     * Maps all properties to their respective event listeners.
     */
    fun addListeners(shop: Shop) {
        val open: PlayerEvent.() -> Unit = { plr.interfaces.open(ShopInterface(shop)) }
        button.forEach { button(it, open) }
        npc1.forEach { npc1(it, open) }
        npc2.forEach { npc2(it, open) }
        npc3.forEach { npc3(it, open) }
        npc4.forEach { npc4(it, open) }
        npc5.forEach { npc5(it, open) }
        object1.forEach { object1(it, open) }
        object2.forEach { object2(it, open) }
        object3.forEach { object3(it, open) }
    }
}