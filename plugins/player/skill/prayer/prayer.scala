/*
 Prayer plugin, supports:
  -> Praying at altars
  -> Burying bones

 TODO:
  -> Prayer activation and draining
*/

import io.luna.game.event.impl.ItemFirstClickEvent
import io.luna.game.model.`def`.ItemDefinition
import io.luna.game.model.mobile.{Animation, Player, Skill}


/* Class representing bones in the 'BONE_TABLE'. */
private case class Bone(id: Int, experience: Double)


/* Bone bury animation. */
private val BURY_ANIMATION = new Animation(827)

/*
 A table of all the bones that can be used for prayer.

 bone_symbol -> (id, experience)
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

 id -> (id, experience)
*/
private val ID_TO_BONE = BONE_TABLE.values.map { it => it.id -> it }.toMap


/* Attempt to bury a bone, if we haven't recently just buried one. */
private def buryBone(plr: Player, bone: Bone) = {
  if (plr.elapsedTime("last_bone_bury", 1200)) {
    plr.animation(BURY_ANIMATION)

    plr.sendMessage("You dig a hole in the ground.")
    plr.sendMessage(s"You bury the ${ItemDefinition.computeNameForId(bone.id)}.")

    plr.skill(Skill.PRAYER).addExperience(bone.experience)
    plr.resetTime("last_bone_bury")
  }
}


/* If the item being clicked is a bone, attempt to bury it. */
>>[ItemFirstClickEvent] { (msg, plr) =>
  ID_TO_BONE.get(msg.getItemId).foreach { it =>
    buryBone(plr, it)
    msg.terminate
  }
}
