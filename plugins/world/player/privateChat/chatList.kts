package world.player.privateChat

import api.predef.*
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.LogoutEvent
import io.luna.game.event.impl.PrivateChatListChangeEvent
import io.luna.game.event.impl.PrivateChatListChangeEvent.ChangeType.*
import io.luna.game.model.mob.Player
import io.luna.net.msg.out.FriendsListStatusMessageWriter
import io.luna.net.msg.out.UpdateFriendsListMessageWriter

/**
 * Updates your own friend list with online statuses.
 */
fun update(plr: Player) {
    plr.friends
        .map { UpdateFriendsListMessageWriter(it, world.getPlayer(it).isPresent) }
        .forEach { plr.queue(it) }
}

/**
 * Updates other friend lists with your online status.
 */
fun updateOtherLists(plr: Player, online: Boolean) {
    val name = plr.usernameHash
    world.players.stream()
        .filter { it.friends.contains(name) }
        .forEach { it.queue(UpdateFriendsListMessageWriter(name, online)) }
}

/**
 * Adds a friend.
 */
fun addFriend(plr: Player, name: Long) {
    when {
        plr.friends.size >= 200 -> plr.sendMessage("Your friends list is full.")
        plr.friends.add(name) -> {
            val online = world.getPlayer(name).isPresent
            plr.queue(UpdateFriendsListMessageWriter(name, online))
        }
        else -> plr.sendMessage("They are already on your friends list.")
    }
}

/**
 * Adds an ignore.
 */
fun addIgnore(plr: Player, name: Long) {
    when {
        plr.ignores.size >= 100 -> plr.sendMessage("Your ignore list is full.")
        !plr.ignores.add(name) -> plr.sendMessage("They are already on your ignore list.")
    }
}

/**
 * Removes a friend.
 */
fun removeFriend(plr: Player, name: Long) {
    if (!plr.friends.remove(name)) {
        plr.sendMessage("They are not on your friends list.")
    }
}

/**
 * Removes an ignore.
 */
fun removeIgnore(plr: Player, name: Long) {
    if (!plr.ignores.remove(name)) {
        plr.sendMessage("They are not on your ignore list.")
    }
}

/**
 * Record friend and ignore list changes.
 */
on(PrivateChatListChangeEvent::class) {
    when (type!!) {
        ADD_FRIEND -> addFriend(plr, name)
        ADD_IGNORE -> addIgnore(plr, name)
        REMOVE_FRIEND -> removeFriend(plr, name)
        REMOVE_IGNORE -> removeIgnore(plr, name)
    }
}

/**
 * Update friends lists on logout.
 */
on(LogoutEvent::class) { updateOtherLists(plr, false) }

/**
 * Update friends lists on login.
 */
on(LoginEvent::class) {
    plr.queue(FriendsListStatusMessageWriter(2))
    update(plr)
    updateOtherLists(plr, true)
}