package plugin.msg

import plugin.{PickupItemEvent, Plugin}

class PickupItemPlugin extends Plugin[PickupItemEvent] {

  // XXX: will test this once updating is done, etc.
  sendMessage("item with item id " + evt.id + " was picked up!")
}
