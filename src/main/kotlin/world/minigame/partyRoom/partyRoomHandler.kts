package world.minigame.partyRoom

import api.predef.*
import api.predef.ext.*
import io.luna.game.action.Action
import io.luna.game.action.RepeatingAction
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.Position
import io.luna.game.model.item.Item
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression
import world.minigame.partyRoom.dropParty.DropPartyOption

/**
 * Represents the action that party pete will continuously do.
 */
class PartyPeteAction(private val npc: Npc) : RepeatingAction<Npc>(npc, true, 3) {

    companion object {

        /**
         * All possible messages pete can speak.
         */
        private val MESSAGES = listOf<(Any) -> String>(
                { "Event $it is active in the party room! Yeah!" },
                { "The $it event is currently under way!" },
                { "This $it event is so much fun! " }
        )
    }

    override fun start() = true

    override fun repeat() {
        npc.animation(Animation(if (rand().nextBoolean()) 866 else 862))

        when (PartyRoom.option) {
            null -> {}
            DropPartyOption -> {
                val secondsLeft = DropPartyOption.secondsLeft!!
                npc.forceChat(when {
                                  secondsLeft == 0 -> "The drop party has started! Everyone come join!"
                                  secondsLeft < 60 -> "The drop party is starting in $secondsLeft seconds!"
                                  secondsLeft < 3600 -> "The drop party is starting in ${secondsLeft / 60} minutes!"
                                  else -> "The drop party is starting soon!"
                              })
            }

            else -> {
                if (rand(4) == 0) {
                    npc.forceChat(MESSAGES.random()(PartyRoom.option!!.description))
                }
            }
        }
    }

    override fun ignoreIf(other: Action<*>?) = true
}

/**
 * The teleport fee.
 */
val TELEPORT_FEE = 10_000

/**
 *  The party room teleport position.
 */
val PARTY_ROOM_SPAWN = Position(2734, 3476, 0)

/**
 * Teleports a player to the party room.
 */
fun teleport(plr: Player) {
    plr.interfaces.close()
    plr.interruptAction()
    plr.move(PartyRoom.TELEPORT_POSITIONS.random())
    plr.sendMessage("You are teleported to the party room.")
}

/**
 * Manages the party pete dialogues.
 */
fun talk(plr: Player, npc: Npc) {
    if (npc.position == PARTY_ROOM_SPAWN) {
        plr.newDialogue().npc(659, Expression.LAUGHING, "Weeeee! Wooooo! It's a paaaaarttyyy!!").open()
    } else {
        plr.newDialogue()
            .npc(659, Expression.LAUGHING, if (PartyRoom.option == null) "Hello mister! Is there anything I can do for you?"
            else "Yay! The ${PartyRoom.option!!.description} event is under way!")
            .options("Can you take me to the party room?", { talkAboutTeleporting(plr) },
                     "No thanks.", { plr.interfaces.close() }).open()
    }
}

/**
 * Talk about teleporting the player to the party room.
 */
fun talkAboutTeleporting(plr: Player) {
    if (PartyRoom.option == DropPartyOption) {
        plr.newDialogue().npc(659, "Yes, but there's an active drop party so I'll have",
                              "to charge you ${numF(TELEPORT_FEE)} gold.")
            .options("Sure, that's fine.", {
                val item = Item(995, TELEPORT_FEE)
                if (plr.inventory.contains(item)) {
                    plr.inventory.remove(item)
                    teleport(plr)
                } else {
                    plr.newDialogue()
                        .npc(659, "You don't have the money, stop wasting my time.")
                        .open()
                }
            }, "No.", { plr.newDialogue().npc(659, "Fine. Enjoy the walk.").open() }).open()
    } else {
        plr.newDialogue()
            .npc(659, "Yes I can, there's no active drop party so it's free!")
            .player("Wow, thanks pete!")
            .then { teleport(it) }.open()
    }
}

// Pull the lever.
object1(2416) { PartyRoom.pullLever(plr, gameObject) }

// Talk to party pete.
npc1(659) { talk(plr, targetNpc) }

// Spawn party petes.
on(ServerLaunchEvent::class) {
    val homeNpc = world.addNpc(Npc(ctx, 659, Position(3094, 3250, 0)))
    val partyRoomNpc = world.addNpc(Npc(ctx, 659, PARTY_ROOM_SPAWN))
    homeNpc.actions.submit(PartyPeteAction(homeNpc))
    partyRoomNpc.actions.submit(PartyPeteAction(partyRoomNpc))
}