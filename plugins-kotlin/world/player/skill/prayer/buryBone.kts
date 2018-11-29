import Bone.Bone
import api.*
import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.def.ItemDefinition
import io.luna.game.model.mob.Animation

/**
 * The bone bury animation.
 */
private val buryAnimation = Animation(827)

/**
 * A mapping of bones identifiers to instances.
 */
private val boneMap = Bone.values().map { it.id to it }.toMap()

/**
 * Attempt to bury a bone, if we haven't recently just buried one.
 */
fun buryBone(msg: ItemFirstClickEvent) {
    val plr = msg.plr
    val bone = boneMap[msg.id]
    if (bone != null && plr.elapsedTime("last_bone_bury", 1200)) {
        plr.interruptAction()
        plr.animation(buryAnimation)

        plr.skill(SKILL_PRAYER).addExperience(bone.exp)
        plr.inventory.remove(bone.boneItem)

        plr.sendMessage("You dig a hole in the ground.")
        plr.sendMessage("You bury the ${bone.itemName()}.")

        plr.resetTime("last_bone_bury")
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
