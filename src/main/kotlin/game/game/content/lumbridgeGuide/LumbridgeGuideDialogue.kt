package game.content.lumbridgeGuide

import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.DialogueQueueBuilder.DialogueOption

/**
 * The Lumbridge Guide's introduction to Luna's autonomous adventurers and population ledger.
 *
 * @author lare96
 */
class LumbridgeGuideDialogue(private val plr: Player, private val npcId: Int) {

    /**
     * Greets a visitor before opening the main topics.
     */
    fun open() {
        plr.newDialogue()
            .npc(npcId, "Greetings, adventurer! Welcome to Luna.",
                "I'm the Lumbridge Guide. A little advice now",
                "may save you a long walk back from a graveyard.")
            .then { mainMenu() }
            .open()
    }

    /**
     * Opens a small menu using the ordinary chatbox option dialogues.
     */
    private fun menu(vararg options: Pair<String, () -> Unit>) {
        plr.newDialogue().options(options.map { (text, action) ->
            DialogueOption(text) { action() }
        }).open()
    }

    /**
     * Answers a question in short, manually wrapped pages, then returns to its topic menu.
     */
    private fun explain(question: String, vararg pages: Array<String>, back: () -> Unit) {
        val dialogue = plr.newDialogue().player(question)
        pages.forEach { dialogue.npc(npcId, *it) }
        dialogue.then { back() }.open()
    }

    /**
     * Opens the main conversation topics.
     */
    private fun mainMenu() {
        menu(
            "What sort of world is Luna?" to { worldMenu() },
            "How do the bots work?" to { workingsMenu() },
            "What kinds of bots will I meet?" to { typesMenu() },
            "May I see your bot statistics?" to { statisticsMenu() },
            "That's all, thank you." to {
                plr.newDialogue().player("That's all, thank you.")
                    .npc(npcId, "Safe travels! And do take some food.",
                        "Enthusiasm is a poor substitute for a lobster.")
                    .open()
            }
        )
    }

    /**
     * Explains the project's purpose and continuing world simulation.
     */
    private fun worldMenu() {
        menu(
            "What is the idea behind Luna?" to {
                explain("What is the idea behind Luna?",
                    arrayOf("A living world, with a life of its own.",
                        "Not merely a splendid map waiting for someone",
                        "to arrive and make use of it."),
                    arrayOf("The aim is a world that can sustain itself.",
                        "Adventurers gather, make things, fight and trade.",
                        "Their needs give other adventurers work to do."),
                    arrayOf("Bots help keep that world moving, even when",
                        "there are few people about. You can join their",
                        "adventures and their economy at your own pace."),
                    arrayOf("There is still work to be done, of course.",
                        "A living world is quite an undertaking.",
                        "My own undertaking is mostly explaining it."), back = ::worldMenu)
            },
            "Do the bots really progress?" to {
                explain("Do the bots really progress?",
                    arrayOf("Yes. They earn experience, train their skills,",
                        "collect supplies and improve their equipment.",
                        "A familiar face may be stronger next time."),
                    arrayOf("The goal is a journey from a fresh adventurer",
                        "at combat level 3 to a veteran at level 126.",
                        "Progress comes from playing the game."),
                    arrayOf("Their skills, belongings and preferences are",
                        "saved. They needn't begin life afresh every",
                        "time they return. Neither would I, at my age."), back = ::worldMenu)
            },
            "What happens when I log out?" to {
                explain("What happens when I log out?",
                    arrayOf("While the server is running, the bots carry on.",
                        "They do not need you to stand beside them",
                        "and remind them which end of an axe to use."),
                    arrayOf("They can finish jobs, spend their coins and",
                        "find new things to do while you are away.",
                        "Some also keep their own login schedules."),
                    arrayOf("They do need a running server, mind you.",
                        "When the whole world is asleep, even the most",
                        "industrious miner must put down his pickaxe."), back = ::worldMenu)
            },
            "Back to my other questions." to { mainMenu() }
        )
    }

    /**
     * Introduces dynamic scripts, supplies and individual decision making.
     */
    private fun workingsMenu() {
        menu(
            "Are these bots artificial intelligence?" to {
                explain("Are these bots artificial intelligence?",
                    arrayOf("They are dynamically scripted adventurers.",
                        "Written rules govern what they can do; their",
                        "circumstances help decide what they do next."),
                    arrayOf("They aren't powered by generative AI.",
                        "There is no language model dreaming up a plan",
                        "or a person directing every swing of a sword."),
                    arrayOf("Fully autonomous means they can play without",
                        "waiting for instructions from a real player.",
                        "Scripted needn't mean the same little loop."),
                    arrayOf("A shortage of food, a better tool or an attack",
                        "can change their next step. The rules are",
                        "written; the day's adventures are not."), back = ::workingsMenu)
            },
            "Where do their items come from?" to { supplies(::workingsMenu) },
            "How do they decide what to do next?" to {
                explain("How do they decide what to do next?",
                    arrayOf("They weigh up activities using their own",
                        "preferences, skills and available resources.",
                        "Training and earning money are different aims."),
                    arrayOf("Once a job is chosen, smaller jobs may help",
                        "it along. A fishing trip might need a bank",
                        "visit or a stop at a shop for supplies."),
                    arrayOf("The fishing job can wait underneath the errand.",
                        "Afterwards, the bot can return to its work.",
                        "Danger and other urgent needs can interrupt."),
                    arrayOf("That is why two bots with the same job needn't",
                        "take the same path through the day.",
                        "Even a well laid plan may run out of bait."), back = ::workingsMenu)
            },
            "What gives each bot a personality?" to { personalityMenu() },
            "Back to my other questions." to { mainMenu() }
        )
    }

    /**
     * Describes ordinary item acquisition without claiming administrator-created bots have no starting kit.
     */
    private fun supplies(back: () -> Unit) {
        explain("Where do their items come from?",
            arrayOf("In ordinary play, from the world around them.",
                "They gather resources, make goods, loot their",
                "kills, buy from shops and trade with others."),
            arrayOf("A shop must have stock, and a buyer needs money.",
                "A tradesman needs something to offer.",
                "A miner is far less useful without a pickaxe."),
            arrayOf("Bots keep track of things they want, including",
                "supplies and suitable equipment. A missing",
                "ingredient may send them looking for a seller."),
            arrayOf("Their banks are real stores of belongings.",
                "They bank what they have earned and withdraw",
                "what they need for another outing."),
            arrayOf("Some may begin with a starting kit, depending",
                "on how they were created. The aim after that",
                "is to acquire things through playing."), back = back)
    }

    /**
     * Explains the traits and preferences that influence bot routines.
     */
    private fun personalityMenu() {
        menu(
            "What are their personality traits?" to {
                explain("What are their personality traits?",
                    arrayOf("Intelligence, kindness, confidence, sociability",
                        "and dexterity. Each bot has its own mixture.",
                        "They are values used by the written routines."),
                    arrayOf("Those values can affect choices and reactions.",
                        "A confident adventurer and a timid one may",
                        "have rather different ideas about danger."),
                    arrayOf("A clever shopper may spread spare money over",
                        "several purchases. Another may spend it all",
                        "on one thing that takes their fancy."),
                    arrayOf("These are tendencies, not promises.",
                        "Even a clever adventurer needs enough coins",
                        "before clever shopping becomes much use."), back = ::personalityMenu)
            },
            "Do they have favourite skills and equipment?" to {
                explain("Do they have favourite skills and equipment?",
                    arrayOf("Indeed. Bots have preferred skills, equipment",
                        "and activities. Their favourites help influence",
                        "which opportunities they take."),
                    arrayOf("Someone keen on training may work on a weaker",
                        "skill. Someone after profit may favour a skill",
                        "they already do well. Preferences also matter."),
                    arrayOf("Favourite equipment must still be usable.",
                        "Wanting a fine weapon is one thing; owning it",
                        "and meeting its requirements are quite another."), back = ::personalityMenu)
            },
            "Can their reactions change?" to {
                explain("Can their reactions change?",
                    arrayOf("They have scripted feelings and reactions too.",
                        "Events can affect fear, happiness or greed,",
                        "and they can hold opinions of other players."),
                    arrayOf("These values give the routines more to consider.",
                        "They aren't a mind inventing new rules, nor",
                        "a guarantee that every bot will be friendly."),
                    arrayOf("You may also hear little remarks as they play.",
                        "I wouldn't expect a learned conversation from",
                        "someone currently being bitten by a goblin."), back = ::personalityMenu)
            },
            "Back to how bots work." to { workingsMenu() }
        )
    }

    /**
     * Separates the main bot activities into their own question menus.
     */
    private fun typesMenu() {
        menu(
            "Tell me about PK bots." to { pkMenu() },
            "Tell me about skilling bots." to { skillingMenu() },
            "Tell me about combat bots." to { combatMenu() },
            "Tell me about traders and other personalities." to { tradingMenu() },
            "Back to my other questions." to { mainMenu() }
        )
    }

    /**
     * Gives practical advice about Wilderness routines.
     */
    private fun pkMenu() {
        menu(
            "What does a PK bot do?" to {
                explain("What does a PK bot do?",
                    arrayOf("It ventures into the Wilderness looking for",
                        "player combat. It travels between hunting",
                        "spots and looks for a suitable opponent."),
                    arrayOf("The Wilderness combat rules still apply.",
                        "Not everyone a bot sees is a valid target.",
                        "Level differences and surroundings matter."),
                    arrayOf("PKing is an activity, and a PKer is a type of",
                        "personality. Neither means a bot spends every",
                        "moment of its life swinging at passers-by."), back = ::pkMenu)
            },
            "Will they attack me?" to {
                explain("Will they attack me?",
                    arrayOf("They can, if you are a valid Wilderness target.",
                        "A bot is a player opponent for these purposes.",
                        "Do not mistake an empty chatbox for safety."),
                    arrayOf("Carry only what you are prepared to risk.",
                        "Their equipment is no promise of an easy fight.",
                        "Neither, regrettably, is yours."), back = ::pkMenu)
            },
            "Do they ever retreat?" to {
                explain("Do they ever retreat?",
                    arrayOf("Yes. PK routines can flee from trouble and",
                        "respond to combat. A dangerous situation need",
                        "not end with a bot standing still until death."),
                    arrayOf("Food, health and the situation affect an outing.",
                        "A finished PK trip also needs a safe way out.",
                        "Running away is sometimes excellent judgement."), back = ::pkMenu)
            },
            "How do they get their equipment?" to { supplies(::pkMenu) },
            "Back to the kinds of bots." to { typesMenu() }
        )
    }

    /**
     * Describes resource gathering and production routines.
     */
    private fun skillingMenu() {
        menu(
            "What can skilling bots do?" to {
                explain("What can skilling bots do?",
                    arrayOf("Gatherers cut trees, mine ore and catch fish.",
                        "Other routines train skills or turn resources",
                        "into useful goods. It depends on the job."),
                    arrayOf("They need the right levels, tools and supplies.",
                        "A full inventory may mean banking the haul",
                        "before returning for another load."),
                    arrayOf("The work earns experience and produces items.",
                        "Those items may later feed a fighter, supply",
                        "a craftsman or find their way into a shop."), back = ::skillingMenu)
            },
            "Do they make things from the materials?" to {
                explain("Do they make things from the materials?",
                    arrayOf("Yes. There are routines for such work as",
                        "cooking food, smelting ore, smithing bars,",
                        "fletching, crafting and preparing potions."),
                    arrayOf("Production uses the actual ingredients.",
                        "A bank full of ore is useful to a smelter.",
                        "A bank full of wishful thinking is less so."),
                    arrayOf("When supplies run short, they may seek more",
                        "or switch tasks. What is available helps shape",
                        "what can be made and how long work continues."), back = ::skillingMenu)
            },
            "Are all skillers alike?" to {
                explain("Are all skillers alike?",
                    arrayOf("Far from it. Preferred skills, current levels",
                        "and the choice between training and profit",
                        "all help determine the next job."),
                    arrayOf("Some personalities favour long skilling trips.",
                        "Others divide their time between several kinds",
                        "of work. A skiller needn't be a copy of another."), back = ::skillingMenu)
            },
            "Back to the kinds of bots." to { typesMenu() }
        )
    }

    /**
     * Explains monster combat and its supply needs.
     */
    private fun combatMenu() {
        menu(
            "How are combat bots different from PKers?" to {
                explain("How are combat bots different from PKers?",
                    arrayOf("Here I mean adventurers who fight monsters.",
                        "Some train combat skills; others seek useful",
                        "or valuable drops. PKers hunt player opponents."),
                    arrayOf("Training and profit can lead to different hunts.",
                        "A creature worth fighting for experience may",
                        "not be the best way to fill a coin pouch."), back = ::combatMenu)
            },
            "How do they choose what to fight?" to {
                explain("How do they choose what to fight?",
                    arrayOf("Combat routines choose from suitable hunts.",
                        "Levels, the aim of the trip and available",
                        "equipment help decide what is practical."),
                    arrayOf("As a bot progresses, different opportunities",
                        "can become available. A fresh adventurer and",
                        "a well equipped veteran have different needs."),
                    arrayOf("Do not assume every nearby bot shares a plan.",
                        "One may want experience, another treasure,",
                        "and another may simply be on the way past."), back = ::combatMenu)
            },
            "What happens when they run out of supplies?" to {
                explain("What happens when they run out of supplies?",
                    arrayOf("They have routines for banking and preparing",
                        "supplies. Fighting is only part of an outing;",
                        "getting ready for it matters as well."),
                    arrayOf("Food and equipment have to come from somewhere.",
                        "A hunter's needs can become a cook's business.",
                        "That is part of the purpose of a living economy."),
                    arrayOf("They can also die and lose things under the",
                        "usual death rules. Autonomous does not mean",
                        "invincible. I give that advice quite often."), back = ::combatMenu)
            },
            "Back to the kinds of bots." to { typesMenu() }
        )
    }

    /**
     * Describes economic interactions and the wider personality templates.
     */
    private fun tradingMenu() {
        menu(
            "What do merchant bots do?" to {
                explain("What do merchant bots do?",
                    arrayOf("They seek opportunities to buy and sell.",
                        "Other bots have wanted items, spare goods",
                        "and coins. A merchant brings them together."),
                    arrayOf("Trading routines consider what is wanted,",
                        "what is offered and what each side can afford.",
                        "An offer can change before it is accepted."),
                    arrayOf("Goods move between real inventories.",
                        "The seller gives something up, and the buyer",
                        "must have something acceptable in return."), back = ::tradingMenu)
            },
            "Can I trade with them?" to {
                explain("Can I trade with them?",
                    arrayOf("Bot trading routines can deal with players too.",
                        "Whether a trade goes ahead depends on the bot,",
                        "its current business and the offer."),
                    arrayOf("They are not shops with an endless catalogue.",
                        "A bot may lack the item, lack the money,",
                        "or simply have no use for what you are selling."),
                    arrayOf("Read both trade screens before accepting.",
                        "That is good advice whoever stands opposite you.",
                        "My ledger does not insure regrettable bargains."), back = ::tradingMenu)
            },
            "What other personalities are there?" to {
                explain("What other personalities are there?",
                    arrayOf("There are skillers, PKers and merchants,",
                        "as well as heroes, keen young adventurers,",
                        "tireless sorts and jacks of all trades."),
                    arrayOf("There is even a scammer personality.",
                        "Best keep your wits about you when trading.",
                        "A pleasant face is not a certificate of honesty."),
                    arrayOf("A personality is a starting set of tendencies,",
                        "not a permanent job title. Individual traits",
                        "and preferences give each bot its own mixture."), back = ::tradingMenu)
            },
            "Back to the kinds of bots." to { typesMenu() }
        )
    }

    /**
     * Captures each requested report when selected, on the game thread.
     */
    private fun statisticsMenu() {
        menu(
            "Show me the population overview." to { BotStatistics.capture(plr.world).openOverview(plr) },
            "What percentage have each script?" to { scriptReport(false) },
            "Which scripts are at the front?" to { scriptReport(true) },
            "How should I read these figures?" to {
                explain("How should I read these figures?",
                    arrayOf("The ledger takes a fresh count when you ask.",
                        "It describes bots online at that moment,",
                        "not every bot saved away in the world."),
                    arrayOf("The full script list counts each bot once for",
                        "each kind of script it has. A bot can have",
                        "several jobs; totals may exceed 100%."),
                    arrayOf("The front script is the current or next task.",
                        "A bank errand may sit in front of a longer hunt.",
                        "That does not turn the hunter into a banker."),
                    arrayOf("These are observed jobs, not fixed assignments",
                        "or chances used to create bots. Empty routines",
                        "show zero; no bots online means no sample."),
                    arrayOf("Close the ledger when you have finished.",
                        "Speak to me again for another report.",
                        "I shall endeavour not to lose my place."), back = ::statisticsMenu)
            },
            "Back to my other questions." to { mainMenu() }
        )
    }

    /**
     * Keeps every registered script reachable if the registry grows beyond a journal page.
     */
    private fun scriptReport(currentOnly: Boolean, page: Int = 0) {
        val snapshot = BotStatistics.capture(plr.world)
        val pages = snapshot.scriptPageCount
        val selected = page.coerceIn(0, pages - 1)
        if (pages == 1) {
            snapshot.openScripts(plr, currentOnly, selected)
            return
        }
        menu(
            "Read ledger page ${selected + 1} of $pages." to {
                val fresh = BotStatistics.capture(plr.world)
                fresh.openScripts(plr, currentOnly, selected.coerceAtMost(fresh.scriptPageCount - 1))
            },
            "Next page." to { scriptReport(currentOnly, (selected + 1) % pages) },
            "Previous page." to { scriptReport(currentOnly, (selected + pages - 1) % pages) },
            "Back to the statistics." to { statisticsMenu() }
        )
    }
}
