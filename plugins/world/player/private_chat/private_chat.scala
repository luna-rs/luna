import io.luna.game.event.impl.PrivateChatListChangeEvent.ChangeType
import io.luna.game.event.impl.{LoginEvent, LogoutEvent, PrivateChatEvent, PrivateChatListChangeEvent}
import io.luna.game.model.mob.Player
import io.luna.net.msg.out.{FriendsListStatusMessageWriter, PrivateChatMessageWriter, UpdateFriendsListMessageWriter}


/* Updates your own friend list with online statuses. */
private def updateList(plr: Player) = {
  plr.getFriends.forEach { name =>
    val msg = new UpdateFriendsListMessageWriter(name, world.playerOnline(name))
    plr.queue(msg)
  }
}

/* Updates other peoples friend lists with your online status. */
private def updateOtherLists(plr: Player, online: Boolean): Unit = {
  val name = plr.getUsernameHash
  world.players.foreach { otherPlr =>
    if (otherPlr.getFriends.contains(name)) {
      otherPlr.queue(new UpdateFriendsListMessageWriter(name, online))
    }
  }
}

/* Adds a friend. */
private def addFriend(plr: Player, name: Long) = {
  if (plr.getFriends.size >= 200) {
    plr.sendMessage("Your friends list is full.")
  } else if (plr.getFriends.add(name)) {
    val isOnline = world.playerOnline(name)
    plr.queue(new UpdateFriendsListMessageWriter(name, isOnline))
  } else {
    plr.sendMessage("They are already on your friends list.")
  }
}

/* Adds an ignore. */
private def addIgnore(plr: Player, name: Long) {
  if (plr.getIgnores.size >= 100) {
    plr.sendMessage("Your ignore list is full.")
  } else if (!plr.getIgnores.add(name)) {
    plr.sendMessage("They are already on your ignore list.")
  }
}

/* Removes a friend. */
private def removeFriend(plr: Player, name: Long) = {
  if (!plr.getFriends.remove(name)) {
    plr.sendMessage("They are not on your friends list.")
  }
}

/* Removes an ignore. */
private def removeIgnore(plr: Player, name: Long) = {
  if (!plr.getIgnores.remove(name)) {
    plr.sendMessage("They are not on your ignore list.")
  }
}

/* Sends a private message. */
private def sendMessage(plr: Player, name: Long, msg: Array[Byte]): Unit = {
  world.getPlayer(name).ifPresent {
    _.queue(new PrivateChatMessageWriter(plr.getUsernameHash, msg))
  }
}


/* Update your own friend list and your friends lists. */
on[LoginEvent].run { msg =>
  val plr = msg.plr

  plr.queue(new FriendsListStatusMessageWriter(2))
  updateList(plr)
  updateOtherLists(plr, online = true)
}

/* Update your friends lists. */
on[LogoutEvent].run { msg => updateOtherLists(msg.plr, online = false) }

/* Record friend and ignore list changes. */
on[PrivateChatListChangeEvent].run { msg =>
  msg.`type` match {
    case ChangeType.ADD_FRIEND => addFriend(msg.plr, msg.name)
    case ChangeType.ADD_IGNORE => addIgnore(msg.plr, msg.name)
    case ChangeType.REMOVE_FRIEND => removeFriend(msg.plr, msg.name)
    case ChangeType.REMOVE_IGNORE => removeIgnore(msg.plr, msg.name)
  }
  msg.terminate
}

/* Send private message to a player. */
on[PrivateChatEvent].run { msg =>
  sendMessage(msg.plr, msg.name, msg.message)
  msg.terminate
}