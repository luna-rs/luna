import api.*
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.mob.Animation
import io.luna.game.model.mob.Player
import world.player.skill.prayer.Bone

/**
 * The "last_bone_bury" attribute.
 */
private var Player.buryTimer by TimerAttr("last_bone_bury")

/**
 * The bone bury animation.
 */
private val buryAnimation = Animation(827)

/**
 * Attempt to bury a bone, if we haven't recently just buried one.
 */
fun buryBone(msg: ItemFirstClickEvent) {
    val plr = msg.plr
    val bone = Bone.BONE_MAP[msg.id]
    if (bone != null && plr.buryTimer >= 1200) {
        plr.interruptAction()
        plr.animation(buryAnimation)

        plr.skill(SKILL_PRAYER).addExperience(bone.exp)
        plr.inventory.remove(bone.boneItem)

        plr.sendMessage("You dig a hole in the ground.")
        plr.sendMessage("You bury the ${bone.itemName()}.")

        plr.buryTimer = RESET_TIMER
        msg.terminate()
    }
}

/**
 * Determines if the event contains a bone that can be buried.
 */
fun buryIf(msg: ItemFirstClickEvent): Boolean {
    return ItemDefinition.ALL
        .get(msg.id)
        .filter { it.inventoryActions.contains("Bury") }
        .isPresent;
}

/**
 * If the item being clicked is a bone, attempt to bury it.
 */
on(ItemFirstClickEvent::class)
    .condition(this::buryIf)
    .run(this::buryBone)
