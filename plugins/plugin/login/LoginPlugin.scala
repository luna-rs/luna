package plugin.login

import io.luna.game.plugin.Plugin
import plugin.LoginEvent

class LoginPlugin extends Plugin[LoginEvent] {

  override def handle() = {
    sendMessage("Welcome to Luna!")
    sendMessage("Luna is an open source #317 Runescape emulator developed by lare96.")

    if (get("run_energy") == 100) { // XXX just a test, will remove later
      sendMessage("You have full run energy!") 
    }
  }
}