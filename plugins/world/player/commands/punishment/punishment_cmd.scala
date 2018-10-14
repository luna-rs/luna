import java.time.LocalDate

import io.luna.game.event.impl.CommandEvent

import scala.reflect.io.File

// TODO turn these into right click actions

/* Perform a lookup for the person we're punishing. */
private def findPunish(msg: CommandEvent) = {
  val name = msg.args(0).replaceAll("_", "")

  world.players.
    filter(_.rights <= RIGHTS_MOD).
    filter(_.name.equalsIgnoreCase(name))
}

/* Construct a string with punishment lift date ~ [yyyy-mm-dd]. */
private def punishDuration(msg: CommandEvent) = {
  val args = msg.args

  val years = if (args.length == 4) args(3).toInt else 0
  val months = if (args.length == 3) args(2).toInt else 0
  val days = args(1).toInt

  LocalDate.now().
    plusYears(years).
    plusMonths(months).
    plusDays(days).toString
}


/* Perform an IP ban on a player. */
onargs[CommandEvent]("ip_ban", RIGHTS_ADMIN) { msg =>
  val file = File("./data/players/blacklist.txt")

  findPunish(msg).foreach { plr =>
    async {
      file.appendAll(System.lineSeparator, plr.address)
    }
    plr.logout
  }
}

/* Perform a permanent ban on a player. */
onargs[CommandEvent]("perm_ban", RIGHTS_ADMIN) { msg =>
  findPunish(msg).foreach { plr =>
    plr.attr("unban_date", "never")
    plr.logout
  }
}

/* Perform a permanent mute on a player. */
onargs[CommandEvent]("perm_mute", RIGHTS_MOD) { msg =>
  findPunish(msg).foreach { plr =>
    plr.attr("unmute_date", "never")
    plr.logout
  }
}

/* Perform a temporary ban on a player. */
onargs[CommandEvent]("ban", RIGHTS_MOD) { msg =>
  findPunish(msg).foreach { plr =>
    plr.attr("unban_date", punishDuration(msg))
    plr.logout
  }
}

/* Perform a temporary mute on a player. */
onargs[CommandEvent]("mute", RIGHTS_MOD) { msg =>
  findPunish(msg).foreach { plr =>
    plr.attr("unmute_date", punishDuration(msg))
    plr.logout
  }
}

/* Perform a forced disconnect on a player. */
onargs[CommandEvent]("kick", RIGHTS_MOD) { findPunish(_).foreach(_.logout) }
