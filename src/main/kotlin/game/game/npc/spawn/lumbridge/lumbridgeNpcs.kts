package game.npc.spawn.lumbridge

import api.predef.*
import api.predef.ext.*
import io.luna.game.event.impl.ServerStateChangedEvent.ServerLaunchEvent
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.Npc
import io.luna.game.model.mob.dialogue.Expression
import io.luna.game.model.item.shop.*
import io.luna.game.model.mob.wandering.*
import java.time.*
import java.time.temporal.*

npc1(521) {
    plr.newDialogue()
        .npc(targetNpc.id, "I'm the assistant!")
        .then { it.overlays.open(ShopInterface(world, "General Store"))}.open()
}

// Hans dialogue
npc1(0) {
    var daysPlayed = plr.timePlayed.toHours() / 24
    var hoursPlayed = plr.timePlayed.toHours() % 24
    var minutesPlayed = plr.timePlayed.toMinutes() % 60;
    var daysSinceCreation = ChronoUnit.DAYS.between(plr.createdAt, Instant.now());
    plr.newDialogue()
        .npc(targetNpc.id, "Hello. What are you doing here?")
        .options(
            "I'm looking for whoever is in charge of this place.", {
                plr.newDialogue()
                    .player("I'm looking for whoever is in charge of this place.")
                    .npc(targetNpc.id, "Who, the Duke? He's in his study, on the first floor.").open()
            },
            "I have come to kill everyone in this castle!", {
                plr.newDialogue()
                    .player(Expression.ANGRY, "I have come to kill everyone in this castle!")
                    .then {targetNpc.speak("Help! Help!")}
                    .open()
            },
            "Can you tell me how long I've been here?", {
                plr.newDialogue()
                    .player("Can you tell me how long I've been here?")
                    .npc(
                        targetNpc.id,
                        Expression.LAUGHING,
                        "Ahh, I see all the newcomers arriving in Lumbridge,",
                        "fresh-faced and eager for adventure. I remember you..."
                    )
                    .npc(
                        targetNpc.id,
                        "You've spent $daysPlayed days, $hoursPlayed hours, $minutesPlayed minutes in the",
                        "world since you arrived $daysSinceCreation days ago."
                    )
                    .open()
            })
        .open()
}

// Man dialogue
npc1(1) {
    manDialogue(plr, targetNpc)
}
npc1(2) {
    manDialogue(plr, targetNpc)
}
npc1(3) {
    manDialogue(plr, targetNpc)
}

fun manDialogue(plr: Player, targetNpc: Npc) {
    val random = rand(4)
    when (random) {
        0 -> {plr.newDialogue()
                .player("Hello. How's it going?")
                .npc(targetNpc.id, Expression.ANGRY, "Get out of my way, I'm in a hurry!")
                .open()}
        1 -> {plr.newDialogue()
                .player("Hello. How's it going?")
                .npc(targetNpc.id, "How can I help you?")
                .options("Do you wish to trade?", {
                    plr.newDialogue()
                        .player("Do you wish to trade?")
                        .npc(targetNpc.id, "No, I have nothing I wish to get rid of. If you want to", "do some trading, there are plenty of shops and market", "stalls around though.")
                        .open()
                },
                    "I'm in search of a quest.", {
                        plr.newDialogue()
                            .player("I'm in search of a quest.")
                            .npc(targetNpc.id, "I'm sorry, I can't help you there.")
                            .open()
                    },
                    "I'm in search of enemies to kill.", {
                        plr.newDialogue()
                            .player("I'm in search of enemies to kill.")
                            .npc(targetNpc.id, "I've heard there are many fearsome creatures that", "dwell under the ground...")
                            .open()
                    })
                .open()}
        2 -> {plr.newDialogue()
                .player("Hello. How's it going?")
                .npc(targetNpc.id, "I'm fine, how are you?")
                .player("Very well thank you.")
                .open()}
        3 -> {plr.newDialogue()
            .player("Hello. How's it going?")
            .npc(targetNpc.id, "Who are you?")
            .player("I'm a bold adventurer.")
            .npc(targetNpc.id, "Ah, a very noble profession.")
            .open()}
        4 -> {plr.newDialogue()
            .player("Hello. How's it going?")
            .npc(targetNpc.id, "I'm busy right now.")
            .open()}
    }
}

// Women dialogue
npc1(4) {
    womenDialogue(plr, targetNpc)
}
npc1(5) {
    womenDialogue(plr, targetNpc)
}

fun womenDialogue(plr: Player, targetNpc: Npc) {
    val random = rand(11)
    when (random) {
        0 -> {plr.newDialogue()
                .player("Hello. How's it going?")
                .npc(targetNpc.id, Expression.ANGRY, "Get out of my way, I'm in a hurry!")
                .open()}
        1 -> {plr.newDialogue()
                .player("Hello. How's it going?")
                .npc(targetNpc.id, "Who are you?")
                .player("I'm a bold adventurer.")
                .npc(targetNpc.id, "Ah, a very noble profession.")
                .open()}
        2 -> {plr.newDialogue()
                .player("Hello. How's it going?")
                .npc(targetNpc.id, "I'm fine, how are you?")
                .player("Very well thank you.")
                .open()}
        3 -> {plr.newDialogue()
                .player("Hello. How's it going?")
                .npc(targetNpc.id, "How can I help you?")
                .options("Do you wish to trade?", {
                    plr.newDialogue()
                        .player("Do you wish to trade?")
                        .npc(targetNpc.id, "No, I have nothing I wish to get rid of. If you want to", "do some trading, there are plenty of shops and market", "stalls around though.")
                        .open()
                },
                    "I'm in search of a quest.", {
                        plr.newDialogue()
                            .player("I'm in search of a quest.")
                            .npc(targetNpc.id, "I'm sorry, I can't help you there.")
                            .open()
                    },
                    "I'm in search of enemies to kill.", {
                        plr.newDialogue()
                            .player("I'm in search of enemies to kill.")
                            .npc(targetNpc.id, "I've heard there are many fearsome creatures that", "dwell under the ground...")
                            .open()
                    })
                .open()}
        4 -> {plr.newDialogue()
                .player("Hello. How's it going?")
                .npc(targetNpc.id, "Not too bad, but I'm a little bit worried about the increase", "of goblins these days.")
                .player("Don't worry, I'll kill them.")
                .open()}
        5 -> {plr.newDialogue()
            .player("Hello. How's it going?")
            .npc(targetNpc.id, "A little worried - I've heard there's lots of people", "going about, killing citizens at random.")
            .open()}
        6 -> {plr.newDialogue()
            .player("Hello. How's it going?")
            .npc(targetNpc.id, "I think we need a new king. The one we've got isn't", "very good.")
            .open()}
        7 -> {plr.newDialogue()
            .player("Hello. How's it going?")
            .npc(targetNpc.id, Expression.ANGRY, "No, I don't want to buy anything!")
            .open()}
        8 -> {plr.newDialogue()
            .player("Hello. How's it going?")
            .npc(targetNpc.id, "Not too bad thanks.")
            .open()}
        9 -> {plr.newDialogue()
            .player("Hello. How's it going?")
            .npc(targetNpc.id, "Hello there! Nice weather we've been heaving.")
            .open()}
        10 -> {plr.newDialogue()
            .player("Hello. How's it going?")
            .npc(targetNpc.id, "Yo, wassup!")
            .open()}
        11 -> {plr.newDialogue()
            .player("Hello. How's it going?")
            .npc(targetNpc.id, "I'm very well thank you.")
            .open()}
    }
}

on(ServerLaunchEvent::class) {
    // Hans
    // TODO make him run around the castle
    world.addNpc(
        id = 0,
        x = 3221,
        y = 3224)
        .startWandering(10, WanderingFrequency.NORMAL)
}