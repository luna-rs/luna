package game.content.magicCarpets

import api.predef.*
import game.content.magicCarpets.CarpetRide.Companion.MAGIC_CARPET
import game.content.magicCarpets.MagicCarpets.fly
import game.content.magicCarpets.MagicCarpets.merchant
import game.content.magicCarpets.MagicCarpets.say
import io.luna.game.event.impl.LoginEvent
import io.luna.game.model.item.Equipment
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder

/**
 * The rug merchants, and the merchants that only show once a quest is done (Uzer, Sophanem and Menaphos).
 */
val RUG_MERCHANTS = listOf(2291, 2292, 2293, 2294, 2295, 2296, 2297, 2298, 2299, 2300, 3020)

/**
 * The amulet that lets players understand monkeys.
 */
val AMULET_OF_MONKEYSPEAK = 4021

/**
 * The topics that leave themselves out of the options that follow them.
 */
enum class Topic { ALI, FLEET, QUESTIONS }

/**
 * One chat box's worth of talk, or more, from the merchant or the player.
 */
class Line(val merchant: Boolean, val text: String)

fun m(text: String) = Line(true, text)
fun p(text: String) = Line(false, text)

/**
 * Has the merchant at [station] and [plr] say [lines] in turn.
 */
fun DialogueQueueBuilder.lines(station: CarpetStation, lines: List<Line>): DialogueQueueBuilder {
    for (line in lines) {
        if (line.merchant) merchant(station.face, line.text) else say(line.text)
    }
    return this
}

/**
 * Talks to the merchant at [station].
 */
fun greet(plr: Player, station: CarpetStation) {
    plr.newDialogue()
        .say("Hello.")
        .merchant(station.face, "Greetings, desert traveler. Do you require the services of Ali Morrisane's flying " +
                                "carpet fleet?")
        .options("Yes please.", {
            it.newDialogue().say("Yes please.").then { plr -> offerFlights(plr, station) }.open()
        }, "Tell me about Ali Morrisane.", {
            aboutAli(it, station)
        }, "Tell me about this magic carpet fleet.", {
            aboutFleet(it, station)
        }, "I have some questions.", {
            questions(it, station)
        }, "No thanks.", {
            it.newDialogue().merchant(station.face, "Come back anytime.").open()
        })
        .open()
}

/**
 * The merchant at [station] says where the carpets go, and flies [plr] wherever they choose.
 */
fun offerFlights(plr: Player, station: CarpetStation) {
    // TODO The Golem: only players who have completed it see the Shantay Pass option to Uzer.
    // TODO The Tourist Trap: the 2006 Knowledge Base says only players who have completed it can fly to the Bedabin
    //  camp, though the Shantay Pass merchant always offers it.
    // TODO Icthlarin's Little Helper: only players who have completed it see the south Pollnivneach options to
    //  Menaphos and Sophanem.
    val routes = station.routes
    val decline = if (routes.size > 1) "I don't want to travel to any of those places." else
        "I don't want to travel there."
    val options = routes.map { route ->
        DialogueOption(route.option) { it.newDialogue().say(route.option).then { plr -> fly(plr, route) }.open() }
    } + DialogueOption(decline) {
        it.newDialogue()
            .say(decline)
            .merchant(station.face, "Fair enough, magic carpet travel isn't for everyone. Enjoy the walk.")
            .open()
    }
    val talk = plr.newDialogue()
    station.offer.forEach { talk.merchant(station.face, it) }
    talk.options(options).open()
}

/**
 * Offers [plr] the merchant's topics again after [asked], which is left out unless it was a question.
 */
fun followUp(plr: Player, station: CarpetStation, asked: Topic): List<DialogueOption> {
    val options = ArrayList<DialogueOption>()
    options += DialogueOption("I want to travel by magic carpet.") {
        it.newDialogue()
            .say("I want to travel by magic carpet.")
            .then { plr -> offerFlights(plr, station) }
            .open()
    }
    if (asked != Topic.ALI) {
        options += DialogueOption("Tell me about Ali Morrisane.") { aboutAli(it, station) }
    }
    if (asked != Topic.FLEET) {
        options += DialogueOption("Tell me about this magic carpet fleet.") { aboutFleet(it, station) }
    }
    options += DialogueOption("I have some questions.") { questions(it, station) }
    options += DialogueOption("Thanks, I'm done here.") {
        it.newDialogue().merchant(station.face, "Come back anytime.").open()
    }
    return options
}

/**
 * Has the merchant at [station] and [plr] say [lines] in turn, then offers the topics that follow [asked].
 */
fun chat(plr: Player, station: CarpetStation, asked: Topic, lines: List<Line>) {
    plr.newDialogue().lines(station, lines).options(followUp(plr, station, asked)).open()
}

/**
 * [plr] asks the merchant at [station] about Ali Morrisane.
 */
fun aboutAli(plr: Player, station: CarpetStation) {
    // TODO The Feud: once it's complete, the player says "Ah yes I remember him now, I went on a wild goose chase
    //  looking for his nephew." and the merchant answers "Ha! No doubt old Ali M instigated the whole thing." instead.
    chat(plr, station, Topic.ALI, listOf(
        p("Tell me about Ali Morrisane."),
        m("What, you haven't heard of Ali M? Possibly the greatest salesman of the Kharidian empire if not all " +
          "Gielinor?"),
        p("I can't say that I have, but he must be the ambitious type to try and set up his own airline."),
        m("You know something, I reckon that he's trying to take on those gnomes at their own game and I'd bet good " +
          "money that he'll probably win."),
        p("Hah? I think you've gone and lost me now."),
        m("You know those small little guys, not the dwarves now mind."),
        p("Ya... gnomes, I'm with you that far."),
        m("Well they have already established an Airline, Gnome Air..."),
        p("Go on..."),
        m("Anyway I think that Ali M's setup here will prove really successful and maybe once we're properly " +
          "established we could try compete with those gnomes."),
        p("I'll watch this space.")))
}

/**
 * [plr] asks the merchant at [station] about the carpet fleet.
 */
fun aboutFleet(plr: Player, station: CarpetStation) {
    plr.newDialogue()
        .lines(station, listOf(
            p("Tell me about this Magic Carpet fleet."),
            m("The latest idea from the great Ali Morrisane. Desert travel will never be the same again."),
            p("So how does it work?"),
            m("The carpet or the whole enterprise?")))
        .options("Tell me about how the carpet works.", {
            chat(it, station, Topic.FLEET, listOf(
                p("Tell me about how the carpet works."),
                m("I'm not really too sure, it's just an enchanted rug really, made out of special Ugthanki hair. " +
                  "It flies to whatever destination its owner commands."),
                p("Are they for sale then?"),
                m("Do you think I'm mad? Do you think that Ali Morrisane would throw his magic carpet monopoly away?"),
                p("Well perhaps if I offered the right price?"),
                m("Not a hope. Could you imagine the mess there'd be if people were constantly zooming through Al " +
                  "Kharid and Pollnivneach? It would be chaos. This way, we can keep the carpet traffic outside " +
                  "towns and other busy places."),
                p("I suppose getting stuck in a carpet jam could get a bit tiresome."),
                m("Just think of the friction burns you would get if you were in a carpet crash.")))
        }, "Tell me about the enterprise then.", {
            chat(it, station, Topic.FLEET, listOf(
                p("Tell me about the enterprise then."),
                m("It's quite simple really, Ali Morrisane has hired myself and a few others to set up carpet " +
                  "stations at some of the desert's more populated places and run flights between the stations."),
                p("So why has he limited the service to just the desert?"),
                m("I don't think Ali is prepared to take on Gnome Air just yet, their gliders are much faster than " +
                  "our carpets, besides that I think we are in the short haul business, something that would only " +
                  "work in harsh conditions like"),
                m("the desert."),
                p("Why is that?"),
                m("I suppose because people would just walk. Getting lost isn't too much of a problem generally, but " +
                  "it's a different matter when you're in the middle of the Kharidian desert with a dry waterskin " +
                  "and no idea"),
                m("which direction to go in."),
                p("You're right I guess. How's the business going then?"),
                m("Not too bad, the hubs are generally quite busy. But the stations in Uzer and the Bedabin camp " +
                  "could do with a bit more traffic."),
                p("A growth market I guess.")))
        })
        .open()
}

/**
 * [plr] has some questions for the merchant at [station].
 */
fun questions(plr: Player, station: CarpetStation) {
    plr.newDialogue()
        .say("I have some questions.")
        .merchant(station.face, "I'll try help you as much as I can.")
        .options("What are you doing here?", {
            chat(it, station, Topic.QUESTIONS, listOf(p("What are you doing here?")) + doingHere(station))
        }, "Is that your pet monkey nearby?", {
            chat(it, station, Topic.QUESTIONS, aboutMonkey(it))
        }, "Where did you get that hat?", {
            chat(it, station, Topic.QUESTIONS, listOf(
                p("Where did you get that hat?"),
                m("My fez? I got it from Ali Morrisane, it's a uniform of sorts, apparently it makes us more " +
                  "visible, but I'm not too sure about it."),
                p("Well it is quite distinctive."),
                m("Do you like it? I haven't really made my mind up about it yet. You see it's not all that " +
                  "practical for desert conditions."),
                p("How so?"),
                m("Well it doesn't keep the sun out of my eyes and after a while sitting out in the desert they " +
                  "really begin to burn.")))
        })
        .open()
}

/**
 * What the merchant at [station] says they're doing there.
 */
fun doingHere(station: CarpetStation): List<Line> = when (station) {
    CarpetStation.SHANTAY_PASS -> listOf(
        m("Well this is a good position for desert traffic. Shantay seems to have a nice little money spinner " +
          "setup, but I reckon, this could turn out even better."))
    CarpetStation.UZER -> listOf(
        m("You mightn't realise it, but this is quite a busy station."),
        p("Who would want to come here?"),
        m("Well you for one, and besides that we get quite a few archaeologists from the dig site passing through " +
          "to examine the golem."))
    CarpetStation.BEDABIN_CAMP -> listOf(
        m("Well besides the obvious - looking after this station, I'm trying to figure out how these Tentis manage " +
          "to cultivate such delicious pineapples."))
    CarpetStation.POLLNIVNEACH_NORTH -> listOf(
        m("Well Pollnivneach is the ideal location for setting up a carpet station."),
        p("Why's that?"),
        m("You see it's located halfway between Al Kharid, and the Menaphite cities and close enough to Nardah " +
          "too, so we get more than enough traffic to keep the business running."))
    CarpetStation.POLLNIVNEACH_SOUTH -> listOf(
        m("I work here renting out magic carpets. I'm from Pollnivneach so it is a handy job, I don't have to " +
          "commute too far to work every day."),
        p("Why's that?"),
        m("You see it's located halfway between Al Kharid, and the Menaphite cities and close enough to Nardah " +
          "too, so we get more than enough traffic to keep the business running."),
        p("So I suppose you're called Ali then."),
        m("Not the most remarkable of names, not that it matters, you see everyone in town knows me as Flash."),
        p("Really?"),
        m("No."),
        p("........"),
        m("........"),
        p("Oh right."))
    CarpetStation.NARDAH -> listOf(
        m("Well I'd preferred to have been running one of the carpet stations at a hub such as Pollnivneach. I was " +
          "a bit slow off the mark to get that gig though. Still business in Nardah isn't bad for a terminal. At " +
          "least"),
        m("people come here for the bank and to see the herbalist."))
    CarpetStation.SOPHANEM -> listOf(
        m("I look after the carpet station here. The place is a bit dead though. Ha! I'm just too much."),
        p("What?"),
        m("You know, Sophanem, city of the dead and all that?"),
        p("..."),
        m("Aww come on, the joke wasn't that bad."),
        p("..."))
    CarpetStation.MENAPHOS -> listOf(
        m("Until recently I was looking after one of the busiest carpet stations. But that all changed since " +
          "Menaphos closed its gates."),
        m("Right now I have to fill my day trying to come up with reasons why Ali M should keep this place open."),
        p("So have you come up with any good ideas then?"),
        m("Not really. The only reason I have come up with to date is that by keeping the station open, people " +
          "will become familiar with it."),
        m("Maybe once Menaphos opens her gates again, the station will make a fortune once more."))
}

/**
 * What [plr] and the merchant say about the station's monkey.
 */
fun aboutMonkey(plr: Player): List<Line> {
    val lines = arrayListOf(
        p("Is that your pet monkey nearby?"),
        m("He's his own monkey, he does whatever suits him, a total nuisance."),
        p("I detect a degree of hostility being directed towards the monkey."),
        m("I shouldn't say this really, but sometimes I begin to question some of Ali Morrisane's ideas, he says " +
          "that associating a monkey with any product will increase sales. I just don't know, what will be next?"),
        p("Frogs?"),
        m("I doubt it, amphibians don't have the same cutesy factor as monkeys."),
        p("I'm confused. I thought you didn't like monkeys."),
        m("I don't dislike monkeys, it's just that monkey. I don't know, I might just be paranoid but I think " +
          "he's... well... evil."),
        p("Hmmm... Interesting."))
    val amulet = when {
        plr.inventory.contains(AMULET_OF_MONKEYSPEAK) ->
            p("Hang on a second, I have an amulet of monkeyspeak in my bag, perhaps I could have a word with him.")
        plr.bank.contains(AMULET_OF_MONKEYSPEAK) ->
            p("Hang on a minute, I think I have an amulet of monkeyspeak stored somewhere. Perhaps I could get it and " +
              "see what it has to say for itself.")
        else -> null
    }
    if (amulet != null) {
        lines += amulet
        lines += m("Would you? It sometimes gives me these stares... ~A visible shiver runs down his back.")
        lines += m("I would be really grateful if you could get that monkey off my back.")
    }
    return lines
}

for (id in RUG_MERCHANTS) {
    npc1(id) {
        val station = CarpetStation.at(targetNpc.basePosition) ?: return@npc1
        greet(plr, station)
    }
}

// A rider who was logged out mid-flight is saved holding the carpet.
on(LoginEvent::class) {
    if (plr.equipment.get(Equipment.WEAPON)?.id == MAGIC_CARPET) {
        plr.equipment.set(Equipment.WEAPON, null)
    }
}
