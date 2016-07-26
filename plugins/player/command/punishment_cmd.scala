/*
 A plugin for applying punishments to players through the use of commands.

 SUPPORTS:
  -> Internet protocol address ban.
  -> Permanent ban/mute.
  -> Temporary ban/mute.
   -> Specific punishment lift dates using 'LocalDate'.
  -> Kicking (forced disconnect).

 TODO:
  -> MAC address ban (maybe?).

 AUTHOR: lare96
*/

import java.time.LocalDate

import io.luna.game.event.impl.CommandEvent

import scala.reflect.io.File


/* Perform a lookup for the person we're punishing. */
private def findPunish(msg: CommandEvent) = {
  val name = msg.getArgs()(0).replaceAll("_", "")

  world.getPlayers.
    filterNot(_.rights >=@ RIGHTS_ADMIN).
    filter(_.getUsername.equalsIgnoreCase(name))
}

/* Construct a string with punishment lift date ~ [yyyy-mm-dd]. */
private def punishDuration(msg: CommandEvent) = {
  val args = msg.getArgs

  val years = if (args.length == 4) args(3).toInt else 0
  val months = if (args.length == 3) args(2).toInt else 0
  val days = args(1).toInt

  LocalDate.now().
    plusYears(years).
    plusMonths(months).
    plusDays(days).toString
}


/* Perform an IP ban on a player. */
intercept_@[CommandEvent]("ip_ban", RIGHTS_ADMIN) { (msg, plr) =>
  val file = File("./data/players/blacklist.txt")

  findPunish(msg).foreach(it => {
    async {
      file.appendAll(System.lineSeparator, it.address)
    }
    it.logout
    world.messageToAll(s"${plr.name} has just blacklisted (ip banned) ${it.name}!")
  })
}

/* Perform a permanent ban on a player. */
intercept_@[CommandEvent]("perm_ban", RIGHTS_ADMIN) { (msg, plr) =>
  findPunish(msg).foreach(it => {
    it.attr("unban_date", "never")
    it.logout

    world.messageToAll(s"${plr.name} has just permanently banned ${it.name}!")
  })
}

/* Perform a permanent mute on a player. */
intercept_@[CommandEvent]("perm_mute", RIGHTS_MOD) { (msg, plr) =>
  findPunish(msg).foreach(it => {
    it.attr("unmute_date", "never")

    world.messageToAll(s"${plr.name} has just permanently muted ${it.name}!")
  })
}

/* Perform a temporary ban on a player. */
intercept_@[CommandEvent]("ban", RIGHTS_MOD) { (msg, plr) =>
  val duration = punishDuration(msg)

  findPunish(msg).foreach(it => {
    it.attr("unban_date", duration)
    it.logout

    world.messageToAll(s"${plr.name} has just banned ${it.name} until [$duration]!")
  })
}

/* Perform a temporary mute on a player. */
intercept_@[CommandEvent]("mute", RIGHTS_MOD) { (msg, plr) =>
  val duration = punishDuration(msg)

  findPunish(msg).foreach(it => {
    it.attr("unmute_date", duration)

    world.messageToAll(s"${plr.name} has just muted ${it.name} until [$duration]!")
  })
}

/* Perform a forced disconnect on a player. */
intercept_@[CommandEvent]("kick", RIGHTS_MOD) { (msg, plr) => findPunish(msg).foreach(_.logout) }