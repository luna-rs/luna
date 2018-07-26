/*
 A plugin that adds functionality for sending 'world' announcements.

 SUPPORTS:
  -> Random message selection at fixed interval.
  -> Filtering certain players from the announcements.

 AUTHOR: lare96
*/

import io.luna.game.event.impl.ServerLaunchEvent
import io.luna.game.model.mob.Player


/* Announcement broadcast interval. */
private val TICK_INTERVAL = 1500 // 15 mins

/* Messages that will be randomly announced. */
private val MESSAGES = Vector(
  "Luna is a Runescape private server for the #317 protocol.",
  "Contribute to Luna at github.org/lare96/luna",
  "Change these messages in /plugins/world/announcement.scala",
  "Any bugs found using Luna should be reported to the github page."
)

/* A filter for players that will receive the announcement. */
private val FILTER = (plr: Player) => plr.rights <= RIGHTS_ADMIN // Only players below admin rank.


/* Filter players and broadcast announcements at set intervals. */
on[ServerLaunchEvent] { msg =>
  world.scheduleForever(TICK_INTERVAL) {
    world.getPlayers.
      filter(FILTER).
      foreach { _.sendMessage(pick(MESSAGES)) }
  }
}