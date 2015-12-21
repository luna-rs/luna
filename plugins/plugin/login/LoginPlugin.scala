package plugin.login

import plugin.{LoginEvent, Plugin}

class LoginPlugin extends Plugin[LoginEvent] {
  p.setRights(determineRights())

  sendMessage("Welcome to Luna, a #317 Runescape emulator!")

  if (get("first_login")) {
    sendMessage("This is your first login. Enjoy your stay!")
    set("first_login", false)
  }

  def determineRights() = {
    if (p.getSession.getHostAddress.equals("127.0.0.1")) rightsDev else p.getRights
  }
}