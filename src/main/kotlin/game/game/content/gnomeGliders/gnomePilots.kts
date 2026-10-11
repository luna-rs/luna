package game.content.gnomeGliders

import api.predef.*
import game.content.gnomeGliders.GnomeGliders.GliderMap
import game.content.gnomeGliders.GnomeGliders.openMap
import game.content.gnomeGliders.GnomeGliders.select
import io.luna.game.model.Position
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * The Gnome pilot, who flies the gliders and also waits by the crashed gliders.
 */
val GNOME_PILOT = 170

/**
 * Gnormadium Avlafrim, who flies the Lemantolly Undri glider.
 */
val GNORMADIUM_AVLAFRIM = 1800

/**
 * Where the pilot of the glider that crashed on Karamja during The Grand Tree spawns.
 */
val KARAMJA_CRASH = Position(2918, 3057)

/**
 * Asks the pilot at [site] for a flight, then opens the glider map.
 */
fun askForFlight(plr: Player, site: GliderSite) {
    plr.newDialogue()
        .player(Expression.QUIZZICAL, "Can you take me on the glider?")
        .npc(GNOME_PILOT, Expression.HAPPY, "Of course!")
        .then { openMap(it, site) }
        .open()
}

/**
 * Ends the talk with a pilot.
 */
fun declineFlight(plr: Player) {
    plr.newDialogue().player(Expression.CONFUSED, "Sorry, I don't want anything now.").open()
}

/**
 * The pilot at [site] explains why gliders beat other transport.
 */
fun praiseGliders(plr: Player, site: GliderSite) {
    plr.newDialogue()
        .player("Why are gliders better than other transport?")
        .npc(GNOME_PILOT, Expression.HAPPY, "Oh we have a whole network! It's wonderful for getting",
             "to hard to reach places.")
        .npc(GNOME_PILOT, Expression.HAPPY, "There are so many places where your teleports cannot", "reach!")
        .player(Expression.HAPPY, "How did you all manage to build such an established", "network?")
        .npc(GNOME_PILOT, "I think you'll find that is a gnome trade secret!")
        .options("Can you take me on the glider?", { askForFlight(it, site) },
                 "Sorry, I don't want anything now.", { declineFlight(it) })
        .open()
}

/**
 * Talks to the pilot at [site].
 */
fun talkToPilot(plr: Player, site: GliderSite) {
    // TODO The Grand Tree: until it's complete, pilots say "Welcome to Gnome Air!" and offer "What's Gnome Air?",
    //  "Where can you take me?", "How much for one-way to Varrock?" and "I'll leave you to it.", refusing
    //  because "Glough has ordered that I only take gnomes on Gnome Air." During the quest, the Ta Quir Priw pilot
    //  flies the player to the Karamja crash site instead.
    plr.newDialogue().options(
        "Can you take me on the glider?", { askForFlight(it, site) },
        "Why are gliders better than other transport?", { praiseGliders(it, site) },
        "Sorry, I don't want anything now.", { declineFlight(it) }).open()
}

npc1(GNOME_PILOT) {
    val spawn = targetNpc.basePosition
    when (val site = GliderSite.near(spawn)) {
        GliderSite.LEMANTO_ANDRA ->
            plr.newDialogue()
                .player(Expression.QUIZZICAL, "What happened here?")
                .npc(GNOME_PILOT, Expression.SHIFTY, "Call it 'creative landing'.")
                .open()

        null -> if (spawn == KARAMJA_CRASH) {
            // TODO The Grand Tree: during the quest, this pilot points the player to the shipyard instead.
            plr.newDialogue()
                .npc(GNOME_PILOT, Expression.SAD, "I don't think I can fix this.",
                     "Looks like I'll be heading back by foot.")
                .open()
        }

        else -> talkToPilot(plr, site)
    }
}

/**
 * Talks to Gnormadium Avlafrim at Lemantolly Undri.
 */
fun talkToGnormadium(plr: Player) {
    // TODO One Small Favour: until it's complete, he says "Hello! Don't get in the way around here, we've got a lot
    //  of work to do!" and talks about building the Lemantolly Undri glider site instead of flying the player.
    plr.newDialogue().options(
        "Hello, how's the work going?", {
            it.newDialogue()
                .player("Hello, how's the work going?")
                .npc(GNORMADIUM_AVLAFRIM, Expression.HAPPY, "Getting there now, thanks to your help!")
                .then { talkToGnormadium(it) }
                .open()
        },
        "Can I take a flight in the glider?", {
            it.newDialogue()
                .player(Expression.QUIZZICAL, "Can I take a flight in the glider?")
                .npc(GNORMADIUM_AVLAFRIM, "Sure, go ahead.")
                .then { openMap(it, GliderSite.LEMANTOLLY_UNDRI) }
                .open()
        },
        "Have a nice day.", {
            it.newDialogue()
                .player("Have a nice day.")
                .npc(GNORMADIUM_AVLAFRIM, "You too, human.")
                .open()
        }).open()
}

npc1(GNORMADIUM_AVLAFRIM) { talkToGnormadium(plr) }

for (site in GliderSite.values()) {
    for (id in site.buttons) {
        button(id) {
            val map = plr.overlays.getOverlay(GliderMap::class.java)
            if (map != null) {
                select(plr, map.from, site)
            }
        }
    }
}
