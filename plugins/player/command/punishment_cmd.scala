import java.time.LocalDate

import io.luna.game.event.impl.CommandEvent

import scala.reflect.io.File

def findPunish(msg: CommandEvent) = {
  val name = msg.getArgs()(0).replaceAll("_", "")
  world.
    getPlayers.
    filterNot(_.rights >=@ RIGHTS_ADMIN).
    find(_.getUsername.equalsIgnoreCase(name))
}

def punishDuration(msg: CommandEvent) = {
  val args = msg.getArgs

  val years = if (args.length == 4) args(3).toInt else 0
  val months = if (args.length == 3) args(2).toInt else 0
  val days = args(1).toInt

  LocalDate.now().
    plusYears(years).
    plusMonths(months).
    plusDays(days).toString
}

>>@[CommandEvent]("ip_ban", RIGHTS_ADMIN) { (msg, plr) =>
  val file = File("./data/players/blacklist.txt")

  findPunish(msg).foreach(it => {
    async {
      file.appendAll(System.lineSeparator, it.address)
    }
    it.logout
    world.messageToAll(s"${plr.name} has just blacklisted (ip banned) ${it.name}!")
  })
}

>>@[CommandEvent]("perm_ban", RIGHTS_ADMIN) { (msg, plr) =>
  findPunish(msg).foreach(it => {
    it.attr("unban_date", "never")
    it.logout

    world.messageToAll(s"${plr.name} has just permanently banned ${it.name}!")
  })
}

>>@[CommandEvent]("perm_mute", RIGHTS_MOD) { (msg, plr) =>
  findPunish(msg).foreach(it => {
    it.attr("unmute_date", "never")

    world.messageToAll(s"${plr.name} has just permanently muted ${it.name}!")
  })
}

>>@[CommandEvent]("ban", RIGHTS_MOD) { (msg, plr) =>
  val duration = punishDuration(msg)

  findPunish(msg).foreach(it => {
    it.attr("unban_date", duration)
    it.logout

    world.messageToAll(s"${plr.name} has just banned ${it.name} until [$duration]!")
  })
}

>>@[CommandEvent]("mute", RIGHTS_MOD) { (msg, plr) =>
  val duration = punishDuration(msg)

  findPunish(msg).foreach(it => {
    it.attr("unmute_date", duration)

    world.messageToAll(s"${plr.name} has just muted ${it.name} until [$duration]!")
  })
}

>>@[CommandEvent]("kick", RIGHTS_MOD) { (msg, plr) => findPunish(msg).foreach(_.logout) }