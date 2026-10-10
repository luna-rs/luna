package game.obj.entrances

import api.predef.*
import api.predef.ext.*
import game.skill.Skills
import io.luna.game.model.Direction
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.block.Animation
import io.luna.game.model.`object`.GameObject

/**
 * Bending down to the floor.
 */
val BEND_DOWN = Animation(827)

// The rocks into the jungle cave in the north of Karamja (Jungle Potion), and the hand holds back out.
object2(2584) {
    plr.newDialogue()
        .text("You search the rocks... You find an entrance into some caves.")
        .options("Yes, I'll enter the cave.", {
            it.newDialogue()
                .text("You decide to enter the caves. You climb down several steep rock",
                      "faces into the cavern below.")
                .then { p -> p.move(Position(2830, 9520)) }
                .open()
        }, "No thanks, I'll give it a miss.", {
            it.newDialogue().text("You decide to stay where you are!").open()
        })
        .title("Would you like to enter the caves?")
        .open()
}
object1(2585) {
    plr.newDialogue()
        .text("You attempt to climb the rocks back out.")
        .then { it.move(Position(2823, 3120)) }
        .open()
}

/**
 * [plr] tries to crawl through the crevice into the shaman caves (Legends' Quest).
 */
fun crawlIntoShamanCaves(plr: Player) {
    if (plr.agility.level < 50) {
        plr.newDialogue().text("You need an agility level of at least 50 to even attempt this feat.").open()
        return
    }
    if (!Skills.success(125, 250, plr.agility.level)) {
        Entrances.sequence(plr, { plr.sendMessage("You try to crawl through...") },
                 { plr.sendMessage("You contort your body to fit the crevice.") },
                 { plr.sendMessage("You get cramped in the tiny space and start to suffocate.") }, null,
                 { plr.sendMessage("You wriggle and wriggle but you cannot get out...") }, null,
                 { plr.sendMessage("Eventually you manage to break free.") }, null,
                 { plr.sendMessage("But you scrape yourself very badly as you force your way out.") }, null,
                 { plr.sendMessage("And you're totally exhausted from the ordeal.") }, null,
                 { plr.damage(5) })
        return
    }
    Entrances.sequence(plr, { plr.sendMessage("You try to crawl through...") },
             { plr.sendMessage("You contort your body to fit the crevice.") },
             { plr.sendMessage("You adroitely squeeze serpent like into the crevice.") }, null,
             {
                 plr.move(Position(2773, 9341))
                 plr.sendMessage("You find a small narrow tunnel that goes for some distance.")
             }, null,
             { plr.sendMessage("After some time, you find a small cave opening... and walk through.") }, null, null)
}

// The rocks into the shaman caves (Legends' Quest).
// TODO Once Legends' Quest is added, the rocks only show the crevice once the player has agreed to rescue Ungadulu
//  ("You search the rocks but you see nothing significant..."), and the first time they find it "...at first.".
for (rocks in 2900..2902) {
    object1(rocks) {
        plr.newDialogue()
            .text("You see that there is a small crevice that you may be able to crawl", "though. Would you like to " +
                "try to crawl through, it looks quite an", "enclosed area?")
            .options("Yes, I'll crawl through, I'm very athletic.", { crawlIntoShamanCaves(it) },
                     "No, I'm pretty scared of enclosed areas.", {
                         it.newDialogue()
                             .text("You decide against forcing yourself into the tiny crevice. And realise",
                                   "that you have much better things to do. Like visit inns and mine ore.")
                             .open()
                     })
            .title("Crawl into hole?")
            .open()
    }
}

// The way back out of the shaman caves.
for (exit in listOf(2903, 2904)) {
    object1(exit) {
        Entrances.sequence(plr, null, null, { plr.sendMessage("You crawl back out from the cavern...") }, null, null,
                 { plr.move(Position(2781, 2934)) })
    }
}

// The rocks into the Tomb of Bervirius (Shilo Village), and the climbing rocks back out.
// TODO Once Shilo Village is added, searching only finds the crawl-way once the tattered scroll has been read ("You
//  find nothing of significance. And it does look quite scary."), and the first entry moves the quest on.
object1(2234) { plr.newDialogue().text("These rocks look like they have been stacked uniformly.").open() }
object2(2234) {
    plr.newDialogue()
        .text("You investigate the rocks and find a dank, narrow crawl-way. Do",
              "you want to crawl into this dank, dark, narrow, possibly dangerous", "hole?")
        .options("Yes Please, I can think of nothing nicer!", { crawlIntoTomb(it) },
                 "No way could you get me to go in there!", {
                     it.overlays.closeWindows()
                     it.sendMessage("You decide that the surface is the place for you!")
                 })
        .title("Crawl into hole?")
        .open()
}

/**
 * [plr] squeezes into the Tomb of Bervirius.
 */
fun crawlIntoTomb(plr: Player) {
    if (plr.agility.level < 32) {
        plr.overlays.closeWindows()
        plr.sendMessage("You need an Agility level of at least 32 to squeeze in there.")
        return
    }
    plr.newDialogue()
        .text("You contort your body and prepare to squirm worm like into the", "hole.")
        .then {
            it.animation(BEND_DOWN)
            if (!Skills.success(125, 250, it.agility.level)) {
                Entrances.sequence(it, { it.sendMessage("You manage to get yourself stuck.") }, null,
                         { it.sendMessage("You have to wrench yourself free to get out.") }, null, null,
                         {
                             it.sendMessage("You manage to pull yourself out, but are hurt in the process.")
                             it.move(it.position.translate(0, -1))
                         }, null,
                         {
                             it.sendMessage("Maybe you'll have better luck next time?")
                             it.damage(3)
                         })
                return@then
            }
            it.newDialogue()
                .text("You struggle through the narrow crevice in the rocks.")
                .then { p ->
                    p.move(Position(2760, 9389))
                    p.newDialogue().text("And drop to your feet into a narrow underground corridor.").open()
                }
                .open()
        }
        .open()
}

object1(2236) {
    val rocks = gameObject
    plr.sendMessage("You attempt to climb the granite rock.")
    if (!Skills.success(125, 250, plr.agility.level)) {
        Entrances.sequence(plr, {
            plr.sendMessage("You fall!")
            plr.speak("Arrggghhhhhh!")
            plr.damage(rand(1, 10))
        })
        return@object1
    }
    Entrances.sequence(plr, { plr.sendMessage("You manage to climb back out again!") }, null,
             { plr.move(Position(2764, 2976)) }, null, { plr.walking.addStep(Direction.EAST) })
}

// Ah Za Rhoon, the fissure under the mound of earth (Shilo Village).
// TODO Once Shilo Village is added, the mound has to be dug with a spade, lit with a candle or torch and roped before
//  the fissure can be climbed into. Players who have already done that see the roped fissure, as here.
for (id in listOf(2217, 2218, 2219)) {
    object1(id) { seeFissure(plr, gameObject, enter = false) }
    object2(id) { seeFissure(plr, gameObject, enter = true) }
}

/**
 * The roped fissure under the mound of earth.
 */
val ROPED_FISSURE = 2219

/**
 * Shows [plr] the roped fissure at [mound], and asks whether to climb in if they're searching it.
 */
fun seeFissure(plr: Player, mound: GameObject, enter: Boolean) {
    if (mound.id != ROPED_FISSURE) {
        Entrances.replace(mound, ROPED_FISSURE, 50)
    }
    val dialogue = plr.newDialogue()
    if (!enter) {
        dialogue.text("You see a small fissure in the granite that you might just be able to",
                      "crawl through. You can see that a rope is attached nearby.").open()
        return
    }
    dialogue.text("You see a small fissure in the granite that you might just be able to",
                  "crawl through. You can see that a rope is attached nearby. Do you",
                  "want to try to crawl through the fissure?")
        .options("Yes, I'll give it a go!", { climbIntoFissure(it) },
                 "No thanks, I'm having second thoughts.", {
                     it.newDialogue().text("You think better of attempting to squeeze into the fissure.")
                         .then { p -> p.speak("It looks very dangerous, and dark... Scary!") }.open()
                 })
        .title("Climb into the fissure?")
        .open()
}

/**
 * [plr] climbs down into Ah Za Rhoon.
 */
fun climbIntoFissure(plr: Player) {
    plr.overlays.closeWindows()
    if (plr.agility.level < 32) {
        Entrances.sequence(plr, null, { plr.sendMessage("You need a level 32 agility to attempt this.") })
        return
    }
    Entrances.sequence(plr, null, {
        plr.sendMessage("You start to contort your body...")
        plr.move(Position(2922, 3000))
    }, {
        plr.sendMessage("With some difficulty you manage to push yourself through the small crack in")
        plr.sendMessage("the rock.")
        plr.animation(BEND_DOWN)
    }, {
        plr.sendMessage("You cleverly use the rope to slowly lower yourself to the floor.")
        plr.agility.addExperience(3.0)
        plr.move(Position(2898, 9401))
    })
}
