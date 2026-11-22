package game.content.canoes

/**
 * One chat box of a talk with a canoe guide.
 *
 * @property guide If the guide says it, rather than the player.
 * @property lines Its lines.
 */
class GuideChat(val guide: Boolean, val lines: Array<out String>)

/**
 * A chat box the guide says.
 */
private fun guide(vararg lines: String) = GuideChat(true, lines)

/**
 * A chat box the player says.
 */
private fun player(vararg lines: String) = GuideChat(false, lines)

/**
 * How most guides explain making a canoe.
 */
private val SIMPLE = listOf(
    guide("It's really quite simple. Just walk down to that tree on", "the bank and chop it down."),
    guide("When you have done that you can shape the log", "further with your axe to make a canoe."))

/**
 * The guides who stand by the canoe stations.
 *
 * @property id The NPC.
 * @property greeting How the guide answers "Hello there."
 * @property story What the guide says about themself, ending on an offer to explain canoes.
 * @property storyAsk How the player takes up that offer.
 * @property menuAsk How the player asks about canoes without hearing the story.
 * @property howTo How the guide explains making a canoe.
 * @property advice What the guide adds about the best canoe the player can make.
 * @author TheLining
 */
enum class CanoeGuide(val id: Int,
                      val greeting: List<GuideChat>,
                      val story: List<GuideChat>,
                      val storyAsk: String,
                      val menuAsk: String,
                      val howTo: List<GuideChat>,
                      val advice: Map<CanoeType, List<GuideChat>>) {
    /**
     * Barfy Bill, at the Lumbridge station.
     */
    BARFY_BILL(id = 3331,
               greeting = listOf(
                   guide("Oh! Hello there.")),
               story = listOf(
                   player("Who are you?"),
                   guide("My name is Ex Sea Captain Barfy Bill."),
                   player("Ex sea captain?"),
                   guide("Yeah, I bought a lovely ship and was planning to make",
                         "a fortune running her as a merchant vessel."),
                   player("Why are you not still sailing?"),
                   guide("Chronic sea sickness. My first, and only, voyage was", "spent dry heaving over the rails."),
                   guide("If I had known about the sea sickness I could have", "saved myself a lot of money."),
                   player("What are you up to now then?"),
                   guide("Well my ship had a little fire related problem.", "Fortunately it was well insured."),
                   guide("Anyway, I don't have to work anymore so I've taken to", "canoeing on the river."),
                   guide("I don't get river sick!"),
                   guide("Would you like to know how to make a canoe?")),
               storyAsk = "Could you teach me about canoes?",
               menuAsk = "Can you teach me about canoes?",
               howTo = SIMPLE,
               advice = mapOf(
                   CanoeType.LOG to listOf(
                       guide("Hah! I can tell just by looking that you lack talent in", "woodcutting."),
                       player("What do you mean?"),
                       guide("No Callouses! No Splinters! No camp fires littering the", "trail behind you."),
                       guide("Anyway, the only 'canoe' you can make is a log. You'll",
                             "be able to travel 1 stop along the river with a log canoe.")),
                   CanoeType.DUGOUT to listOf(
                       guide("With your skill in woodcutting you could make my",
                             "favourite canoe, the Dugout. They might not be the",
                             "best canoe on the river, but they get you where you're",
                             "going."),
                       player("How far will I be able to go in a Dugout canoe?"),
                       guide("You will be able to travel 2 stops on the river.")),
                   CanoeType.STABLE_DUGOUT to listOf(
                       guide("The best canoe you can make is a Stable Dugout, one", "step beyond a normal Dugout."),
                       guide("With a Stable Dugout you can travel to any place on", "the river."),
                       player("Even into the Wilderness?"),
                       guide("Not likely! I've heard tell of a man up near Edgeville",
                             "who claims he can use a Waka to get up into the",
                             "Wilderness."),
                       guide("I can't think why anyone would wish to venture into", "that hellish landscape though.")),
                   CanoeType.WAKA to listOf(
                       guide("Hoo! You look like you know which end of an axe is", "which!"),
                       guide("You can easily build one of those Wakas. Be careful if",
                             "you travel into the Wilderness though."),
                       guide("I've heard tell of great evil in that blasted wasteland."),
                       player("Thanks for the warning Bill.")))),

    /**
     * Tarquin, at the Champions' Guild station.
     */
    TARQUIN(id = 3328,
            greeting = listOf(
                guide("Hello old bean. Is there something I can help you with?")),
            story = listOf(
                player("Who are you?"),
                guide("My name is Tarquin Marjoribanks."),
                guide("I'd be surprised if you haven't already heard of me?"),
                player("Why would I have heard of you Mr. Marjoribanks?"),
                guide("It's pronounced 'Marchbanks'!"),
                guide("You should know of me because I am a member of the", "royal family of Misthalin!"),
                player("Are you related to King Roald?"),
                guide("Oh yes! Quite closely actually."),
                guide("I'm his 4th cousin, once removed on his mothers side."),
                player("Er... Okay. What are you doing here then?"),
                guide("I'm canoeing on the river! It's enormous fun! Would", "you like to know how?")),
            storyAsk = "Could you teach me about canoes?",
            menuAsk = "Can you teach me about canoes?",
            howTo = SIMPLE + listOf(
                guide("My personal favourite is the Stable Dugout canoe. A", "finer craft you'll never see old bean!"),
                guide("A Stable Dugout canoe will take you pretty much the", "length of the Lum river."),
                guide("Of course there are other canoes.")),
            advice = mapOf(
                CanoeType.LOG to listOf(
                    guide("Further up river, near the Barbarian Village, I saw",
                          "some darned fool 'canoeing' on a log!"),
                    guide("Unfortunately, you don't have the skill to create",
                          "anything more than one of those logs. I dare say it will",
                          "only get 1 stop down the river!"),
                    guide("Still, I'm sure it will satisfy one such as yourself."),
                    player("What's that supposed to mean?"),
                    guide("Do not profane the royal house of Varrock by",
                          "engaging me in further discourse you knave!"),
                    player("Pfft! I doubt he even knows the King!")),
                CanoeType.DUGOUT to listOf(
                    guide("You seem to be quite handy with an axe though!"),
                    guide("I'm sure you can build a Dugout canoe. Not as fine",
                          "as a Stable Dugout but it will carry you 2 stops on the",
                          "river."),
                    guide("I should imagine it would suit your limited means."),
                    player("What do you mean when you say 'limited means'?"),
                    guide("Well, you're just an itinerant adventurer!"),
                    guide("What possible reason would you have for cluttering up",
                          "my river with your inferior water craft!")),
                CanoeType.STABLE_DUGOUT to listOf(
                    guide("Ah! Perfect! You can make a Stable Dugout canoe!",
                          "One of those will carry you to any civilised place on the",
                          "river."),
                    guide("If you were of good pedigree I'd let you join my boat",
                          "club. You seem to be one of those vagabond",
                          "adventurers though."),
                    player("Charming!"),
                    guide("Be off with you rogue!")),
                CanoeType.WAKA to listOf(
                    guide("Well ... erm. You seem to be able to make a Waka!"),
                    player("Sounds fun, what's a Waka."),
                    guide("I've only ever seen one man on the river who uses a",
                          "Waka. A big, fearsome looking fellow up near Edgeville."),
                    guide("People say he was born in the Wilderness and that he", "is looking for a route back."),
                    player("Is that true!"),
                    guide("How should I know? I would not consort with such a", "base fellow!")))),

    /**
     * Sigurd, at the Barbarian Village station.
     */
    SIGURD(id = 3329,
           greeting = listOf(
               guide("Ha Ha! Hello!")),
           story = listOf(
               player("Who are you?"),
               guide("I'm Sigurd the Great and Brainy."),
               player("Why do they call you the Great and Brainy?"),
               guide("Because I invented the Log Canoe!"),
               player("Log Canoe?"),
               guide("Yeash! Me and my cousins were having a great party",
                     "by the river when we decided to have a game of 'Smack",
                     "The Tree'"),
               player("Smack the Tree?"),
               guide("It's a game were you take it in turnsh shmacking a",
                     "tree. First one to uproot the tree winsh!"),
               guide("Anyway, I won the game with a flying tackle. The tree",
                     "came loose and down the river bank I went, still holding",
                     "the tree."),
               guide("I woke up a few hours later and found myself several",
                     "miles down river. And thatsh how I invented the log",
                     "canoe!"),
               player("So you invented the 'Log Canoe' by falling into a river", "hugging a tree?"),
               guide("Well I refined the design from the original you know!"),
               guide("I cut all the branches off to make it more comfortable.",
                     "I could tell you how to if you like?")),
           storyAsk = "Can you teach me about canoes?",
           menuAsk = "Can you teach me about canoes?",
           howTo = listOf(
               guide("It's really quite simple. Just walk down to that tree on", "the bank and chop it down."),
               guide("Then take your axe to it and shape it how you like!")),
           advice = mapOf(
               CanoeType.LOG to listOf(
                   guide("You can make a log canoe like mine! It'll get you 1", "stop down the river."),
                   guide("There's some snooty fella down near the Champions",
                         "Guild who reckons his canoes are better than mine. He's",
                         "never said it to my face though.")),
               CanoeType.DUGOUT to listOf(
                   guide("You could make a Dugout canoe with your woodcutting",
                         "skill, but I don't see why you would want to.")),
               CanoeType.STABLE_DUGOUT to listOf(
                   guide("Well, you're pretty handy with an axe!"),
                   guide("You could make Stable Dugout canoes, like that snooty", "fella Tarquin."),
                   guide("He reckons his canoes are better than mine. He's never", "said it to my face though.")),
               CanoeType.WAKA to listOf(
                   guide("You look like you know your way around a tree, you", "can make a Waka canoe."),
                   player("What's a Waka?"),
                   guide("I've only ever seen Hari using them. People say he's",
                         "found a way to canoe the river underground and into",
                         "the Wilderness"),
                   guide("Hari hangs around up near Edgeville"),
                   guide("He's a nice bloke.")))),

    /**
     * Hari, at the Edgeville station.
     */
    HARI(id = 3330,
         greeting = listOf(
             guide("Hello.")),
         story = listOf(
             player("Who are you?"),
             guide("My name is Hari."),
             player("And what are you doing here Hari?"),
             guide("Like most people who come to Edgeville, I am here to", "seek adventure in the Wilderness."),
             guide("I found a secret underground river that will take me", "quite a long way north."),
             player("Underground river? Where does it come out?"),
             guide("It comes out in a pond located deep in the Wilderness."),
             guide("I had to find a very special type of canoe to get me up",
                   "the river though, would you like to know more?")),
         storyAsk = "Could you teach me about canoes?",
         menuAsk = "Could you teach me about canoes?",
         howTo = SIMPLE,
         advice = mapOf(
             CanoeType.LOG to listOf(
                 guide("I can sense you're still a novice woodcutter, you will",
                       "only be able to make a log canoe at present."),
                 player("Is that good?"),
                 guide("A log will take you one stop along the river. But you",
                       "won't be able to travel into the Wilderness on it.")),
             CanoeType.DUGOUT to listOf(
                 guide("You are an average woodcutter. You should be able to",
                       "make a Dugout canoe quite easily. It will take you 2",
                       "stops along the river."),
                 player("Can I take a dugout canoe to reach the Wilderness?"),
                 guide("You would never make it there alive."),
                 player("Best not to try then.")),
             CanoeType.STABLE_DUGOUT to listOf(
                 guide("You seem to be an accomplished woodcutter. You will",
                       "easily be able to make a Stable Dugout"),
                 guide("They are reliable enough to get you anywhere on this",
                       "river, except to the Wilderness of course."),
                 guide("Only a Waka can take you there."),
                 player("A Waka? What's that?"),
                 guide("Come and ask me when you have improved your skills", "as a woodcutter.")),
             CanoeType.WAKA to listOf(
                 guide("Your skills rival mine friend. You will certainly be able", "to build a Waka."),
                 player("A Waka? What's that?"),
                 guide("A Waka is an invention of my people, it's an incredible",
                       "strong and fast canoe and will carry you safely to any",
                       "destination on the river."),
                 player("Any destination?"),
                 guide("Yes, you can take a waka north through the", "underground portion of this river."),
                 guide("It will bring you out at a pond in the heart of the",
                       "Wilderness. Be careful up there, many have lost more",
                       "than their lives in that dark and twisted place."))));

    companion object {

        /**
         * What every guide tells a player who can't make a canoe yet.
         */
        val UNSKILLED = listOf(
            guide("Well, you don't look like you have the skill to make a", "canoe."),
            guide("You need to have at least level 12 woodcutting."),
            guide("Once you are able to make a canoe it makes travel", "along the river much quicker!"))
    }
}
