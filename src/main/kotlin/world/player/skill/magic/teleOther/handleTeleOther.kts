package world.player.skill.magic.teleOther

import api.predef.*
import api.predef.ext.*
import com.google.common.base.Stopwatch
import io.luna.game.event.impl.UseSpellEvent.MagicOnPlayerEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.varp.PersistentVarp
import world.player.skill.magic.Magic
import world.player.skill.magic.teleOther.TeleOtherAction.Companion.teleOtherRequests
import java.util.concurrent.TimeUnit

/**
 * Represents a teleother request.
 */
class TeleOtherRequest {

    /**
     * The timer for this request.
     */
    private val timer = Stopwatch.createStarted()

    /**
     * If another request can be sent.
     */
    fun isExpired() = timer.elapsed().toSeconds() >= TeleOtherAction.TELEOTHER_DELAY_SECONDS
}


/**
 * Opens the [TeleOtherInterface] for the [target].
 */
fun open(source: Player, target: Player, type: TeleOtherType) {
    if (target.interfaces.isStandardOpen || target.interfaces.isInputOpen) {
        source.sendMessage("That player is busy.")
        return
    }
    if (target.varpManager.getValue(PersistentVarp.ACCEPT_AID) == 0) {
        source.sendMessage("This player has accept aid turned off.")
        return
    }
    if (source.teleOtherRequests.size >= 5) {
        source.sendMessage("You have sent teleother requests to too many different people recently.")
        return
    }
    val timestamp = source.teleOtherRequests[target.usernameHash]
    if (timestamp != null) {
        val elapsed = TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - timestamp)
        if (elapsed < TeleOtherAction.TELEOTHER_DELAY_SECONDS) {
            source.sendMessage("You have already recently sent a teleother request to this person.")
            return
        }
    }
    if (Magic.checkRequirements(source, type.level, type.requirements) != null) {
        source.teleOtherRequests[target.usernameHash] = System.nanoTime()
        target.interfaces.open(TeleOtherInterface(source, target, type))
        target.walking.clear()
    }
}

/**
 * Attempts to teleport [target] to the destination when accept is clicked.
 */
fun clickAccept(target: Player) {
    val openInterface = target.interfaces.get(TeleOtherInterface::class)
    if (openInterface != null) {
        target.interfaces.close()

        val source = openInterface.source
        source.teleOtherRequests.remove(target)
        if (!source.position.isWithinDistance(target.position, 10)) {
            source.sendMessage("You are too far away from ${target.username} to teleport them.")
            target.sendMessage("You are too far away from ${source.username} to be teleported.")
            return
        }
        source.submitAction(TeleOtherAction(source, target, openInterface.type))
    }
}

/**
 * Declines the teleother request and notifies the sender.
 */
fun clickDecline(target: Player) {
    val openInterface = target.interfaces.get(TeleOtherInterface::class)
    if (openInterface != null) {
        target.interfaces.close()
        openInterface.source.sendMessage("${target.username} has declined your teleother request.")
        target.sendMessage("You have declined ${openInterface.source.username}'s teleother request.")
    }
}


/* Interactions for teleother spellbook actions and interface buttons. */
on(MagicOnPlayerEvent::class).filter { spellId == 12425 } // Lumbridge
    .then { open(plr, targetPlr, TeleOtherType.LUMBRIDGE) }
on(MagicOnPlayerEvent::class).filter { spellId == 12435 }
    .then { open(plr, targetPlr, TeleOtherType.FALADOR) } // Falador
on(MagicOnPlayerEvent::class).filter { spellId == 12455 }
    .then { open(plr, targetPlr, TeleOtherType.CAMELOT) } // Camelot


// Accept
button(12566) { clickAccept(plr) }

// Decline
button(12568) { clickDecline(plr) }