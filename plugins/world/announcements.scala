import io.luna.game.event.impl.ServerLaunchEvent
import io.luna.game.model.mobile.Player


val ANNOUNCEMENT_TICK_INTERVAL = 1500 // 15 mins

val ANNOUNCEMENT_MESSAGES = Vector(
  "Luna is a Runescape private server for the #317 protocol.",
  "Lare96's favorite bands are Tame Impala, Black Sabbath, and Bad Brains.",
  "Lare96 enjoys collecting records from various places around the world.",
  "Lare96 wants to travel to Japan, Iran, Turkey, Brazil, and Germany.",
  "Change these messages in /plugins/player/announcements.scala",
  "Any bugs found using Luna should be reported to the github page."
)

val FILTER_ANNOUNCEMENT = (plr: Player) => plr.rights <=@ RIGHTS_DEV // Send announcement to everyone.


>>[ServerLaunchEvent] { (msg, plr) =>
  world.scheduleForever(ANNOUNCEMENT_TICK_INTERVAL) {
    world.getPlayers.
      filter(FILTER_ANNOUNCEMENT).
      foreach(_.sendMessage(ANNOUNCEMENT_MESSAGES.randomElement))
  }
}