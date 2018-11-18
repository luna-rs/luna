import java.time.LocalDate
import java.time.format.DateTimeFormatter

import io.luna.game.event.impl.LoginEvent


/* Formats dates into the pattern specified. */
private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MMMM d, uuuu")

/* Gets the mute lift date in the specified format. */
private def getLiftDate(date: String) = DATE_FORMATTER.format(LocalDate.parse(date))


/* If the player is muted, indicate that they are and when it will be lifted. */
on[LoginEvent].run { msg =>
  val plr = msg.plr
  val date: String = plr.attr("unmute_date")

  date match {
    case "n/a" => // Do nothing, we aren't muted.
    case "never" => plr.sendMessage("You are permanently muted. It can only be overturned by an administrator.")
    case _ => plr.sendMessage(s"You are muted. You will be unmuted on ${getLiftDate(date)}.")
  }
}