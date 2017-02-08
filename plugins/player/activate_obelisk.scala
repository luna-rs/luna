import io.luna.game.event.impl.ObjectClickEvent.ObjectFirstClickEvent
import io.luna.game.model.mobile.{Animation, Graphic, Player}
import io.luna.game.model.{Area, Position}

import scala.collection.mutable

// TODO replace with activated models
// TODO only loop through current region players
// TODO lock movement on teleport
// TODO destination should be an area as well
private case class Obelisk(id: Int, destination: Position, teleportArea: Area)

private val GRAPHIC = new Graphic(342)
private val ANIMATION = new Animation(1816)
private val ACTIVATED_OBELISK = 14825
private val OBELISKS = Map(
  14829 -> Obelisk(14829, new Position(3156, 3620), Area.create(3154, 3618, 3158, 3622)), // Level 13 obelisk
  14830 -> Obelisk(14830, new Position(3227, 3667), Area.create(3225, 3665, 3229, 3669)), // Level 19 obelisk
  14827 -> Obelisk(14827, new Position(3035, 3732), Area.create(3033, 3730, 3037, 3733)), // Level 27 obelisk
  14828 -> Obelisk(14828, new Position(3106, 3794), Area.create(3104, 3792, 3108, 3796)), // Level 35 obelisk
  14826 -> Obelisk(14826, new Position(2980, 3866), Area.create(2978, 3864, 2982, 3868)), // Level 44 obelisk
  14831 -> Obelisk(14831, new Position(3307, 3916), Area.create(3306, 3914, 3310, 3918)) // Level 50 obelisk
)

private val OBELISK_IDS = OBELISKS.keys.toVector

private val obelisksActivated = mutable.Set.empty[Int]


private def activate(plr: Player, obelisk: Obelisk) = {
  if (obelisksActivated.contains(obelisk.id)) {
    plr.sendMessage("This Obelisk has already been activated!")
  } else {
    obelisksActivated.add(obelisk.id)

    plr.sendMessage("You activate the ancient Obelisk...")

    val nextObelisk = OBELISKS(rand(OBELISK_IDS))

    /*val plrs: Set[Player] = world.getViewableEntities(obelisk.destination, TYPE_PLAYER)

    world.scheduleOnce(7) {
      plrs.foreach { it =>
        it.graphic(GRAPHIC)
        it.animation(ANIMATION)
      }

      world.scheduleOnce(3) {
        plrs.foreach { it =>
          it.teleport(nextObelisk.destination)
          it.sendMessage("You have been teleported by ancient magic!")
        }
        obelisksActivated.remove(obelisk.id)
      }*/
  }
}


on[ObjectFirstClickEvent] { msg =>
  OBELISKS.get(msg.id).foreach(activate(msg.plr, _))
}