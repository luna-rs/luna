import api.predef.*
import io.luna.game.action.Action
import io.luna.game.action.ProducingAction
import io.luna.game.event.impl.ItemOnItemEvent
import io.luna.game.event.impl.ServerLaunchEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.StandardInterface
import world.player.skill.crafting.Hide.*
import world.player.skill.crafting.HideArmor

class SoftLeatherInterface : StandardInterface(2311)

class CraftArmorAction(plr: Player, val armor: HideArmor, val amount: Int) : ProducingAction(plr, true, 3) {

    override fun onProduce() {
        // 1249, animation
    }

    override fun add(): Array<Item> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun remove(): Array<Item> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isEqual(other: Action<*>?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

val buttonToAction = mutableMapOf<Int, CraftArmorAction>()
val needleId = -1
val studsId = 2370


on(ItemOnItemEvent::class)
    .filter { matches(needleId, SOFT_LEATHER.tan) }
    .then { plr.interfaces.open(StandardInterface(2311)) }

on(ItemOnItemEvent::class)
    .filter { matches(studsId, HideArmor.LEATHER_BODY.id) }
    .then { }

on(ItemOnItemEvent::class)
    .filter { matches(studsId, HideArmor.LEATHER_CHAPS.id) }
    .then { }

on(ItemOnItemEvent::class)
    .filter { matches(needleId, HARD_LEATHER.tan) }
    .then { }

on(ItemOnItemEvent::class)
    .filter { matches(needleId, SNAKESKIN.tan) }
    .then { }

on(ItemOnItemEvent::class)
    .filter { matches(needleId, GREEN_D_LEATHER.tan) }
    .then { }

on(ItemOnItemEvent::class)
    .filter { matches(needleId, BLUE_D_LEATHER.tan) }
    .then { }

on(ItemOnItemEvent::class)
    .filter { matches(needleId, RED_D_LEATHER.tan) }
    .then { }

on(ItemOnItemEvent::class)
    .filter { matches(needleId, BLACK_D_LEATHER.tan) }
    .then { }

on(ItemOnItemEvent::class)
    .filter { matches(needleId, BLACK_D_LEATHER.tan) }
    .then { }

on(ServerLaunchEvent::class) {

}