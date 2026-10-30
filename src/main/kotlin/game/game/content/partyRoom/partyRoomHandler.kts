package game.content.partyRoom

import api.bot.zone.SubZone
import api.predef.*
import game.content.partyRoom.dropParty.DropPartyOption
import io.luna.game.action.Action
import io.luna.game.action.ActionType
import io.luna.game.event.impl.ServerStateChangedEvent.ServerReadyEvent
import io.luna.game.model.Position
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.mob.dialogue.Expression

/**
 * An [Action] implementation that makes the party pete NPC dance and speak.
 */
class PartyPeteAction(private val npc: Npc) : Action<Npc>(npc, ActionType.STRONG, true, 3) {

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

    override fun run(): Boolean {
        // Every 3 ticks, never completes.
        npc.animation(Animation(if (rand().nextBoolean()) 866 else 862))

        when (PartyRoom.option) {
            null -> {}
            DropPartyOption -> {
                val secondsLeft = DropPartyOption.secondsLeft ?: return false
                npc.speak(when {
                              secondsLeft == 0 -> "The drop party has started! Everyone come join!"
                              secondsLeft < 60 -> "The drop party is starting in $secondsLeft seconds!"
                              secondsLeft < 3600 -> "The drop party is starting in ${secondsLeft / 60} minutes!"
                              else -> "The drop party is starting soon!"
                          })
            }

            else -> {
                if (rand(4) == 0) {
                    npc.speak(MESSAGES.random()(PartyRoom.option!!.description))
                }
            }
        }
        return false
    }
}

/**
 * Manages the party pete dialogues.
 */
fun talk(plr: Player, npc: Npc) {
    plr.newDialogue().npc(659, Expression.LAUGHING, "Weeeee! Wooooo! It's a paaaaarttyyy!!").open()
}

// Pull the lever.
object1(2416) { PartyRoom.pullLever(plr, gameObject) }

// Talk to party pete.
npc1(659) { talk(plr, targetNpc) }

// Start action for party pete.
on(ServerReadyEvent::class) {
    val partyRoomNpc =
        world.locator.findNpcs(SubZone.PARTY_ROOM.inside, Position.VIEWING_DISTANCE) { it.id == 659 }.firstOrNull()
    partyRoomNpc?.let { it.actions.submit(PartyPeteAction(it)) }
}