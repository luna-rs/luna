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


/* Type alias for bones in 'BONE_TABLE'. */
private type Bone = (Int, Double)


/* Bone bury animation. */
private val BURY_ANIMATION = new Animation(827)

/*
 A table of all the bones that can be used for prayer.

 bone_symbol -> (id, experience)
*/
private val BONE_TABLE = Map(
  'bones -> (526, 4.5),
  'bat_bones -> (530, 5.2),
  'monkey_bones -> (3179, 5.0),
  'wolf_bones -> (2859, 4.5),
  'big_bones -> (532, 15.0),
  'babydragon_bones -> (534, 30.0),
  'dragon_bones -> (536, 72.0)
)

/*
 A different mapping of the 'BONE_TABLE' that maps bone identifiers to their data.

 id -> (id, experience)
*/
private val ID_TO_BONE = BONE_TABLE.values.map { case (id, experience) => id -> (id, experience) }.toMap


/* Attempt to bury a bone, if we haven't recently just buried one. */
private def buryBone(plr: Player, bone: Bone) = {
  if (plr.elapsedTime("last_bone_bury", 1200)) {
    plr.animation(BURY_ANIMATION)

    plr.sendMessage("You dig a hole in the ground.")
    plr.sendMessage(s"You bury the ${ItemDefinition.computeNameForId(bone._1)}.")

    plr.skill(Skill.PRAYER).addExperience(bone._2)
    plr.resetTime("last_bone_bury")
  }
}


/* If the item being clicked is a bone, attempt to bury it. */
>>[ItemFirstClickEvent] { (msg, plr) =>
  val bone = ID_TO_BONE.get(msg.getItemId)
  if (bone.isDefined) {
    buryBone(plr)(bone.get)
  }
}
