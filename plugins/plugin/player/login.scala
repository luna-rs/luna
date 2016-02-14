import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.mobile.Player

on[LoginEvent] { (msg: LoginEvent, plr: Player) =>
  plr.setRights(if (plr.address.equals("127.0.0.1")) rightsDev else plr.getRights)

  plr.sendMessage("Welcome to Luna, a #317 Runescape emulator!")

  if (plr.attr("first_login")) {
    plr.sendMessage("This is your first login. Enjoy your stay!")
    plr.attr("first_login", false)
  }
}