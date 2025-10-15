package world.player.privateChat


import api.predef.*
import io.luna.game.event.impl.LoginEvent
import io.luna.game.event.impl.LogoutEvent
import io.luna.game.event.impl.PrivacyListChangeEvent.*
import io.luna.game.event.impl.PrivacyModeChangedEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.PlayerPrivacy.PrivacyMode
import io.luna.net.msg.out.FriendsListStatusMessageWriter
import io.luna.net.msg.out.UpdateFriendsListMessageWriter
import io.luna.net.msg.out.UpdateIgnoreListMessageWriter

/**
 * Determines whether [viewer] should see [subject] as online.
 *
 * @param viewer The player viewing their friends list.
 * @param subject The player whose online visibility is being checked.
 * @return `true` if [subject] should appear online for [viewer].
 */
fun isVisibleTo(viewer: Player, subject: Player): Boolean {
    val mode = subject.privacyOptions.privateChat
    return when {
        subject.ignores.contains(viewer.usernameHash) -> false
        mode == PrivacyMode.ON -> true
        mode == PrivacyMode.FRIENDS -> subject.friends.contains(viewer.usernameHash)
        mode == PrivacyMode.OFF -> false
        else -> {
            logger.warn("Unexpected null or invalid privacy mode for {}!", subject)
            true
        }
    }
}

/**
 * Sends a single friend list update to [receiver] about [subject].
 *
 * @param receiver The player whose friends list should update.
 * @param subject The friend whose online status changed.
 * @param visible Optional explicit visibility override. If `null`, computed automatically.
 */
fun updateFriendOnlineStatus(receiver: Player, subject: Player, visible: Boolean? = null) {
    val msg = UpdateFriendsListMessageWriter(subject.usernameHash, visible ?: isVisibleTo(receiver, subject))
    receiver.queue(msg)
}

/**
 * Broadcasts the current online status of [subject] to everyone who has them added.
 */
fun broadcastOnlineStatus(subject: Player, visible: Boolean? = null) {
    for (viewer in world.players) {
        if (viewer.friends.contains(subject.usernameHash)) {
            updateFriendOnlineStatus(viewer, subject, visible)
        }
    }
}

/**
 * Updates [plr]'s own friends list snapshot on login.
 */
fun updateFriendsListForSelf(plr: Player) {
    for (friendHash in plr.friends) {
        val friendOpt = world.getPlayer(friendHash)
        val online = friendOpt.map { isVisibleTo(plr, it) }.orElse(false)
        plr.queue(UpdateFriendsListMessageWriter(friendHash, online))
    }
}

/**
 * Updates [plr]'s ignore list snapshot on login.
 */
fun updateIgnoreListForSelf(plr: Player) {
    plr.queue(UpdateIgnoreListMessageWriter())
}

/**
 * Adds [name] to [plr]'s friend list and syncs visibility changes.
 */
fun addFriend(plr: Player, name: Long) {
    when {
        plr.friends.size >= 200 -> plr.sendMessage("Your friends list is full.")
        plr.friends.add(name) -> {
            val friendOpt = world.getPlayer(name)
            if (friendOpt.isEmpty) {
                plr.queue(UpdateFriendsListMessageWriter(name, false))
            } else {
                val friend = friendOpt.get()
                updateFriendOnlineStatus(plr, friend)
                if (friend.friends.contains(plr.usernameHash)) {
                    updateFriendOnlineStatus(friend, plr)
                }
            }
        }

        else -> plr.sendMessage("They are already on your friends list.")
    }
}

/**
 * Removes [name] from [plr]'s friends list and updates visibility accordingly.
 */
fun removeFriend(plr: Player, name: Long) {
    if (plr.friends.remove(name)) {
        world.getPlayer(name).ifPresent { updateFriendOnlineStatus(it, plr) }
    } else {
        plr.sendMessage("They are not on your friends list.")
    }
}

/**
 * Adds [name] to [plr]'s ignore list, updating both lists as needed.
 */
fun addIgnore(plr: Player, name: Long) {
    when {
        plr.ignores.size >= 100 -> plr.sendMessage("Your ignore list is full.")
        plr.ignores.add(name) -> {
            world.getPlayer(name)
                .filter { it.friends.contains(plr.usernameHash) }
                .ifPresent { updateFriendOnlineStatus(it, plr, false) }
            updateIgnoreListForSelf(plr)
        }

        else -> plr.sendMessage("They are already on your ignore list.")
    }
}

/**
 * Removes [name] from [plr]'s ignore list, updating visibility appropriately.
 */
fun removeIgnore(plr: Player, name: Long) {
    if (plr.ignores.remove(name)) {
        updateIgnoreListForSelf(plr)
        world.getPlayer(name).ifPresent { updateFriendOnlineStatus(it, plr) }
    } else {
        plr.sendMessage("They are not on your ignore list.")
    }
}

on(AddFriendEvent::class) {
    addFriend(plr, name)
}

on(RemoveFriendEvent::class) {
    removeFriend(plr, name)
}

on(AddIgnoreEvent::class) {
    addIgnore(plr, name)
}

on(RemoveIgnoreEvent::class) {
    removeIgnore(plr, name)
}

on(LogoutEvent::class) {
    // Force everyone to see us offline.
    if (plr.privacyOptions.privateChat != PrivacyMode.OFF) {
        broadcastOnlineStatus(plr, false)
    }
}

on(LoginEvent::class) {
    plr.queue(FriendsListStatusMessageWriter(2))
    updateFriendsListForSelf(plr)
    broadcastOnlineStatus(plr)
    updateIgnoreListForSelf(plr)
}

on(PrivacyModeChangedEvent::class) {
    broadcastOnlineStatus(plr)
}