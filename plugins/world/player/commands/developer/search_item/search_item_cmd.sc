import io.luna.game.event.impl.CommandEvent
import io.luna.game.model.`def`.ItemDefinition
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.inter.StandardInterface
import io.luna.net.msg.out.{WidgetItemsMessageWriter, WidgetTextMessageWriter}

import scala.collection.mutable.ArrayBuffer


/* An interface representing the result of a search. */
private final class SearchResultInterface(searchItem: String) extends StandardInterface(5292) {

  /* A list of empty text widget messages. */
  private val TEXT_WIDGETS = Set(5388, 5389, 5390, 5391, 8132, 8133).
    map(new WidgetTextMessageWriter("", _))

  override def onOpen(plr: Player) = {
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

    // Convert buffer into raw array, truncating results if necessary.
    val displayArray = {
      val displaySize = displayBuffer.size
      val cap = plr.getBank.capacity
      if (displaySize > cap) {
        plr.sendMessage("The search has been truncated because there are too many results to be displayed.")
        displayBuffer.dropRight(displaySize - cap).toArray
      } else {
        plr.sendMessage(s"Found $displaySize results for search term '$searchItem'.")
        displayBuffer.toArray
      }
    }


    // Send packets.
    plr.sendWidgetText(s"Search results for '$searchItem' ...", 5383)
    TEXT_WIDGETS.foreach(plr.queue(_))
    plr.queue(new WidgetItemsMessageWriter(5382, displayArray))
  }

  override def onClose(plr: Player) = {
    plr.sendWidgetText("The Bank of Runescape", 5383)
    plr.sendWidgetText("Withdraw as:", 5388)
    plr.sendWidgetText("Item", 5389)
    plr.sendWidgetText("Rearrange mode:", 5390)
    plr.sendWidgetText("Note", 5391)
    plr.sendWidgetText("Insert", 8132)
    plr.sendWidgetText("Swap", 8133)
  }
}

/* A command that opens an interface spawns item(s) by name. */
on[CommandEvent].
  args("search_item", RIGHTS_DEV).
  run { msg =>
    val plr = msg.plr
    val search = msg.args(0).replaceAll("_", " ")
    if (search.length > 1) {
      plr.interfaces.open(new SearchResultInterface(search))
    } else {
      plr.sendMessage("Search term must be more than 1 character.")
    }
  }