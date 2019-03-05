import api.attr.Attr
import api.predef.*
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player
import world.player.skill.prayer.Bone
import java.util.concurrent.TimeUnit

/**
 * The delay in between burying bones.
 */
val buryDelay = 1200L

/**
 * The bone bury animation.
 */
val buryAnimation = Animation(827)

/**
 * An attribute representing the bury timer.
 */
var Player.buryTimer by Attr.stopwatch(buryDelay, TimeUnit.MILLISECONDS)

/**
 * Attempt to bury a bone, if we haven't recently just buried one.
 */
fun buryBone(plr: Player, bone: Bone) {
    if (plr.buryTimer >= buryDelay) {
        plr.interruptAction()
        plr.animation(buryAnimation)

        plr.prayer.addExperience(bone.exp)
        plr.inventory.remove(bone.boneItem)

        plr.sendMessage("You dig a hole in the ground.")
        plr.sendMessage("You bury the ${bone.itemName()}.")

        plr.buryTimer.reset()
    }
}

/**
 * If the item being clicked is a bone, attempt to bury it.
 */
on(ItemFirstClickEvent::class)
    .filter { itemDef(id).hasInventoryAction(0, "Bury") }
    .then {
        val bone = Bone.ID_TO_BONE[id]
        if (bone != null) {
            buryBone(plr, bone)
        }
    }
