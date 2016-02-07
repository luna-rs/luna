package plugin.player

import io.luna.game.model.item.Item
import plugin.LoginEvent
import plugin.ScalaBindings._

object Login {

  // TODO: add items to inventory

  val items = Array(new Item(995, 50000), new Item(4151, 1))

  on[LoginEvent] { (msg, plr) =>
    plr.setRights(if (plr.address.equals("127.0.0.1")) RightsDev else plr.getRights)

    plr.sendMessage("Welcome to Luna, a #317 Runescape emulator!")

    if (plr.attr("first_login")) {
      plr.sendMessage("This is your first login. Enjoy your stay!")
      plr.attr("first_login", false)
    }
  }
}
