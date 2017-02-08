/*
 A plugin for the Prayer skill that adds functionality for burying bones.

 SUPPORTS:
  -> Burying most bones.
  -> Throttling (can only bury every 1.2 seconds).

 TODO:
  -> Add more types of bones.

 AUTHOR: lare96
*/

import io.luna.game.event.impl.ItemClickEvent.ItemFirstClickEvent
import io.luna.game.model.item.Item
import io.luna.game.model.mobile.{Animation, Player}


/* Bone bury animation. */
private val ANIMATION = new Animation(827)

/* A table of all the bones that can be buried. */
private val BONES = Map(
  526 -> 4.5, // Bones
  530 -> 5.2, // Bat bones
  3179 -> 5.0, // Monkey bones
  2859 -> 4.5, // Wolf bones
  532 -> 15.0, // Big bones
  534 -> 30.0, // Babydragon bones
  536 -> 72.0 // Dragon bones
)

/* Attempt to bury a bone, if we haven't recently just buried one. */
private def buryBone(plr: Player, id: Int, exp: Double) = {
  if (plr.elapsedTime("last_bone_bury", 1200)) {

    plr.interruptAction()
    plr.animation(ANIMATION)

    plr.skill(SKILL_PRAYER).addExperience(exp)
    plr.inventory.remove(new Item(id))

    plr.sendMessage("You dig a hole in the ground.")
    plr.sendMessage(s"You bury the ${ nameOfItem(id) }.")

    plr.resetTime("last_bone_bury")
  }
}


/* If the item being clicked is a bone, attempt to bury it. */
on[ItemFirstClickEvent] { msg =>
  val boneId = msg.id
  BONES.get(boneId).foreach { experience =>
    buryBone(msg.plr, boneId, experience)
    msg.terminate
  }
}
