package plugin.login

import plugin.{LoginEvent, Plugin}

class LoginPlugin extends Plugin[LoginEvent] {
  p.setRights(determineRights())

  sendMessage("Welcome to Luna!")
  sendMessage("Luna is an open source #317 Runescape emulator developed by lare96.")

  if (get("first_login")) {
    yell("A new player, " + p.getUsername + " has just logged in!")
    set("first_login", false)
  }

  def determineRights() = {
    if (p.getSession.getHostAddress.equals("127.0.0.1")) rightsDev else p.getRights
  }
}