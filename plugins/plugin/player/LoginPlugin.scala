package plugin.player

import plugin.{LoginEvent, Plugin}

class LoginPlugin extends Plugin[LoginEvent] {
  plr.setRights(?(plr.address.equals("127.0.0.1"))(rightsDev, plr.getRights))

  plr.sendMessage("Welcome to Luna, a #317 Runescape emulator!")

  if (plr.attr("first_login")) {
    plr.sendMessage("This is your first login. Enjoy your stay!")
    plr.attr("first_login", false)
  }
}