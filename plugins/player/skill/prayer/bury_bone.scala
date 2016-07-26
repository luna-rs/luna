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
import io.luna.game.model.`def`.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mobile.Skill.PRAYER
import io.luna.game.model.mobile.{Animation, Player}


/* Class representing bones in the 'BONE_TABLE'. */
private case class Bone(id: Int, exp: Double)


/* Bone bury animation. */
private val BURY_ANIMATION = new Animation(827)

/*
 A table of all the bones that can be buried.

 bone_symbol -> Bone
*/
private val BONE_TABLE = Map(
  'bones -> Bone(526, 4.5),
  'bat_bones -> Bone(530, 5.2),
  'monkey_bones -> Bone(3179, 5.0),
  'wolf_bones -> Bone(2859, 4.5),
  'big_bones -> Bone(532, 15.0),
  'babydragon_bones -> Bone(534, 30.0),
  'dragon_bones -> Bone(536, 72.0)
)

/*
 A different mapping of the 'BONE_TABLE' that maps bone identifiers to their data.

 id -> Bone
*/
private val ID_TO_BONE = BONE_TABLE.values.map(bone => bone.id -> bone).toMap


/* Attempt to bury a bone, if we haven't recently just buried one. */
private def buryBone(plr: Player, bone: Bone) = {
  if (plr.elapsedTime("last_bone_bury", 1200)) {

    plr.interruptAction()
    plr.animation(BURY_ANIMATION)

    plr.skill(PRAYER).addExperience(bone.exp)
    plr.inventory.remove(new Item(bone.id))

    plr.sendMessage("You dig a hole in the ground.")
    plr.sendMessage(s"You bury the ${ItemDefinition.getNameForId(bone.id)}.")

    plr.resetTime("last_bone_bury")
  }
}


/* If the item being clicked is a bone, attempt to bury it. */
intercept[ItemFirstClickEvent] { (msg, plr) =>
  ID_TO_BONE.get(msg.getId).foreach { it =>
    buryBone(plr, it)
    msg.terminate
  }
}
