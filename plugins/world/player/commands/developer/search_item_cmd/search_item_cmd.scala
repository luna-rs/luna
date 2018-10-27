import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.`def`.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.StandardInterface
import io.luna.net.msg.out.{WidgetItemsMessageWriter, WidgetTextMessageWriter}

import scala.collection.mutable.ArrayBuffer


/* An interface representing the result of a search. */
private final class SearchResultInterface(plr: Player, searchItem: String) extends StandardInterface(5292) {

  /* A list of text widget messages. */
  private val TEXT_WIDGETS = Set(5388, 5389, 5390, 5391, 8132, 8133).
    map(new WidgetTextMessageWriter("", _))

  /* Perform search for items and display them on the interface. */
  def init = {

    // Perform search here, filter selected items into displayBuffer.
    val displayBuffer = new ArrayBuffer[Item]()
    ItemDefinition.ALL.forEach { itemDef =>
      if (itemDef != null &&
        itemDef.getId > 0 &&
        !itemDef.isNoted &&
        itemDef.getName.toLowerCase.contains(searchItem)) {
        displayBuffer += new Item(itemDef.getId, Int.MaxValue)
      }
    }

    // Truncate results if necessary.
    val displaySize = displayBuffer.size
    val cap = plr.getBank.capacity
    if (displaySize > cap) {
      plr.sendMessage("The search has been truncated because there are too many results to be displayed.")
      displayBuffer.dropRight(displaySize - cap)
    } else {
      plr.sendMessage(s"There are $displaySize results for search term '$searchItem'.")
    }

    // Send packets.
    plr.queue(new WidgetTextMessageWriter(s"Search results for '$searchItem' ...", 5383))
    TEXT_WIDGETS.foreach(plr.queue(_))
    plr.queue(new WidgetItemsMessageWriter(5382, displayBuffer.toArray))
    this
  }

  override def onClose(player: Player) = {
    plr.queue(new WidgetTextMessageWriter("The Bank of Runescape", 5383))
    plr.queue(new WidgetTextMessageWriter("Withdraw as:", 5388))
    plr.queue(new WidgetTextMessageWriter("Item", 5389))
    plr.queue(new WidgetTextMessageWriter("Rearrange mode:", 5390))
    plr.queue(new WidgetTextMessageWriter("Note", 5391))
    plr.queue(new WidgetTextMessageWriter("Insert", 8132))
    plr.queue(new WidgetTextMessageWriter("Swap", 8133))
  }
}

/* A command that opens an interface spawns item(s) by name. */
onargs[CommandEvent]("search_item", RIGHTS_DEV) { msg =>
  val plr = msg.plr
  val search = msg.args(0).replaceAll("_", " ")
  if (search.length > 1) {
    val searchResults = new SearchResultInterface(plr, search)
    plr.interfaces.open(searchResults.init)
  } else {
    plr.sendMessage("Search term must be more than 1 character.")
  }
}