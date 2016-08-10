import io.luna.game.event.impl.{EquipmentChangeEvent, LoginEvent}
import io.luna.game.model.item.Equipment.HEAD
import io.luna.game.model.mobile.Player


private val CONFIG_ID = 491
private val TIARAS = Map(
  5527 -> 1, // Air tiara
  5529 -> 2, // Mind tiara
  5531 -> 4, // Water tiara
  5535 -> 8, // Body tiara
  5537 -> 16, // Earth tiara
  5533 -> 31, // Fire tiara
  5539 -> 64, // Cosmic tiara
  5543 -> 128, // Nature tiara
  5541 -> 256, // Chaos tiara
  5545 -> 512, // Law tiara
  5547 -> 1024, // Death tiara
  5549 -> ???, // Blood tiara
  5551 -> ??? // Soul tiara
)


private def sendTiaraConfig(plr: Player) = {
  val headId = plr.inventory.computeIdForIndex(HEAD).orElse(-1)
  val tiaraConfig = TIARAS.get(headId)

  if (tiaraConfig.isDefined) {
    plr.sendConfig(CONFIG_ID, value)
  } else {
    plr.sendConfig(CONFIG_ID, 0)
  }
}


intercept[LoginEvent] { (msg, plr) => sendTiaraConfig(plr) }

intercept[EquipmentChangeEvent] { (msg, plr) => sendTiaraConfig(plr) }