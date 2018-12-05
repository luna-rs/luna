import api.attr.Stopwatch
import api.predef.*
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player
import world.player.skills.prayer.Bone

/**
 * The "last_bone_bury" stopwatch.
 */
var Player.buryTimer by Stopwatch("last_bone_bury")

/**
 * The bone bury animation.
 */
val buryAnimation = Animation(827)

/**
 * Attempt to bury a bone, if we haven't recently just buried one.
 */
fun buryBone(plr: Player, bone: Bone) {
    if (plr.buryTimer >= 1200) {
        plr.interruptAction()
        plr.animation(buryAnimation)

        plr.skill(SKILL_PRAYER).addExperience(bone.exp)
        plr.inventory.remove(bone.boneItem)

        plr.sendMessage("You dig a hole in the ground.")
        plr.sendMessage("You bury the ${bone.itemName()}.")

        plr.buryTimer = -1
    }
}

/**
 * If the item being clicked is a bone, attempt to bury it.
 */
on(ItemFirstClickEvent::class)
    .filter { itemDef(it.id).isInventoryAction(0, "Bury") }
    .then {
        val bone = Bone.BONE_MAP[it.id]
        if (bone != null) {
            buryBone(it.plr, bone)
            it.terminate()
        }
    }
