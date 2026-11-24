package game.content.questRewards

import api.attr.Attr
import api.attr.getValue
import api.attr.setValue
import game.content.questRewards.QuestRewards.owns
import io.luna.game.model.item.Item
import io.luna.game.model.mob.Player
import io.luna.game.model.mob.dialogue.Expression

/**
 * The Fitzharmon gauntlets: Dimintheis keeps returning them, and each of his three sons can turn the steel pair into
 * his own kind once.
 *
 * @author TheLining
 */
object FamilyCrest {

    /**
     * Dimintheis, in his house in south-east Varrock.
     */
    const val DIMINTHEIS = 664

    /**
     * The gauntlets that Dimintheis hands out before a son changes them.
     */
    const val STEEL_GAUNTLETS = 778

    /**
     * The three sons and what each one tells and does.
     */
    enum class Son(val npc: Int,
                   val gauntlets: Int,
                   val gauntletsName: String,
                   val greeting: List<String>,
                   val ask: List<String>,
                   val askEmpty: List<String>,
                   val offer: List<List<String>>,
                   val accept: String,
                   val decline: List<String>,
                   val declineReply: List<String>,
                   val work: List<String>,
                   val greetingFace: Expression = Expression.HAPPY,
                   val askFace: Expression = Expression.QUIZZICAL,
                   val acceptFace: Expression = Expression.DEFAULT,
                   val declineFace: Expression = Expression.CONFUSED,
                   val declineOption: String = decline.joinToString(" ")) {
        CALEB(666, 775, "cooking gauntlets",
              listOf("I hear you brought the completed crest to my father. I",
                     "must say, that was awfully impressive work."),
              listOf("I believe your father mentioned you would be able to",
                     "improve these gauntlets for me..."),
              listOf("I believe your father mentioned you would be able to",
                     "improve some gauntlets for me..."),
              listOf(listOf("Yes, that is correct. I can slightly alter these gauntlets",
                            "to allow you some of my skill at preparing seafood by",
                            "making them cooking gauntlets."),
                     listOf("When you wear them you will burn lobsters, sharks and",
                            "swordfish a lot less. It will be a permanent modification",
                            "however, so make sure this is what you want.")),
              "Yes, please do that for me.",
              listOf("I'll see what your brothers have to offer first."),
              listOf("As you wish. Let me know if you change your mind."),
              listOf("Caleb takes the gauntlets from you, pours some herbs",
                     "and seasonings on them, bakes them on his range for a",
                     "short time, then hands them back to you."),
              askFace = Expression.DEFAULT, declineFace = Expression.DEFAULT),
        AVAN(663, 776, "goldsmithing gauntlets",
             listOf("I have received word from my father of your assistance",
                    "to our family in this matter. You have my thanks for",
                    "restoring our honour."),
             listOf("Your father mentioned that you could improve these",
                    "gauntlets in some way for me..."),
             listOf("Your father mentioned that you could improve some",
                    "gauntlets for me."),
             listOf(listOf("Indeed I can. In my long time searching for the",
                           "'perfect' gold I learned a lot about gold items. I can",
                           "improve these gauntlets so that they provide more than",
                           "normal"),
                    listOf("experience when smithing gold. I can easily modify",
                           "these for you.")),
             "That sounds good, improve them for me.",
             listOf("I think I'll check my other options with your brothers",
                    "before committing myself."),
             listOf("If you feel you really must consort with the likes of",
                    "them. I await your decision."),
             listOf("Avan takes your gauntlets, takes out a small hammer,",
                    "and pounds them into a slightly new shape, then hands",
                    "them back to you."),
             declineOption = "I think I'll check my other options with your brothers."),
        JOHNATHON(668, 777, "chaos gauntlets",
                  listOf("Hello again. My family now considers you a hero."),
                  listOf("Your father tells me you can improve these gauntlets",
                         "for me..."),
                  listOf("Your father told me you could improve some gauntlets",
                         "for me."),
                  listOf(listOf("And right he is at that. Although my skill with Death",
                                "Rune magiks was not great enough to defeat",
                                "Chronozon, I am adequately skilled at Chaos Rune",
                                "magiks."),
                         listOf("If you give me your gauntlets I will bestow upon them",
                                "some of my power, and make any bolt spells you wish",
                                "to cast more effective.")),
                  "That sounds good to me!",
                  listOf("I think I'll check my other options with your brothers",
                         "before committing myself."),
                  listOf("I doubt they will offer anything as useful as my",
                         "magical boost, but as you wish."),
                  listOf("Johnathon takes your gauntlets from you, and begins a",
                         "low chant over them. You see them begin to glow and",
                         "sparkle, before he returns them to you."),
                  greetingFace = Expression.DEFAULT, acceptFace = Expression.HAPPY,
                  declineOption = "I shall see what options your brothers can offer me first.");
    }

    /**
     * The gauntlets id that a son made for this player, or 0 before any son has.
     */
    private var Player.crestGauntlets by Attr.int().persist("family_crest_gauntlets")

    /**
     * The gauntlets that Dimintheis keeps for [plr].
     */
    private fun gauntlets(plr: Player) = if (plr.crestGauntlets != 0) plr.crestGauntlets else STEEL_GAUNTLETS

    /**
     * Talks to Dimintheis, who hands [plr] their gauntlets back if they no longer have them.
     */
    fun talkToDimintheis(plr: Player) {
        // TODO Family Crest: before the quest he asks for help finding the crest, and on its completion he hands over
        //  the steel gauntlets. Until the quest exists every player has finished it.
        val gauntlets = gauntlets(plr)
        val dialogue = plr.newDialogue()
            .npc(DIMINTHEIS, Expression.HAPPY, "You have my eternal gratitude adventurer, for all you",
                 "have done in saving my family's honour. Perhaps some", "day my sons will overcome their differences.")
        if (!owns(plr, gauntlets)) {
            val name = Son.values().firstOrNull { it.gauntlets == gauntlets }?.gauntletsName ?: "steel gauntlets"
            if (!plr.inventory.hasSpaceFor(Item(gauntlets))) {
                dialogue.npc(DIMINTHEIS, Expression.HAPPY, "Your $name returned to me through",
                             "their magic power recently. Clear some space and you", "can have them back.")
            } else {
                dialogue.npc(DIMINTHEIS, Expression.HAPPY, "Your $name returned to me through",
                             "their magic power recently. Would you like them back?")
                    .options("Yes please.", {
                        it.newDialogue()
                            .player(Expression.HAPPY, "Yes please.")
                            .npc(DIMINTHEIS, "Here you go.")
                            .then { plr -> if (!owns(plr, gauntlets)) QuestRewards.give(plr, gauntlets) }
                            .open()
                    }, "Not right now.", {
                        it.newDialogue()
                            .player("Not right now.")
                            .npc(DIMINTHEIS, "Well, come back if you change your mind any time. I",
                                 "owe you for your trouble.")
                            .open()
                    })
            }
        }
        dialogue.open()
    }

    /**
     * Talks to [son], who offers to change the steel gauntlets of [plr] into his own kind.
     */
    fun talkToSon(plr: Player, son: Son) {
        // TODO Family Crest: before the quest is done each son talks about the crest instead. Until the quest exists
        //  every player has finished it.
        val dialogue = plr.newDialogue().npc(son.npc, son.greetingFace, *son.greeting.toTypedArray())
        when {
            plr.crestGauntlets != 0 -> Unit
            plr.inventory.contains(STEEL_GAUNTLETS) -> {
                dialogue.player(son.askFace, *son.ask.toTypedArray())
                for (box in son.offer) dialogue.npc(son.npc, *box.toTypedArray())
                dialogue.options(son.accept, { enchant(it, son) }, son.declineOption, {
                    it.newDialogue()
                        .player(son.declineFace, *son.decline.toTypedArray())
                        .npc(son.npc, *son.declineReply.toTypedArray())
                        .open()
                })
            }
            else -> dialogue.player(*son.askEmpty.toTypedArray())
                .npc(son.npc, Expression.HAPPY, "Bring them to me, and I'll see what I can do.")
        }
        dialogue.open()
    }

    /**
     * [son] changes the steel gauntlets of [plr] into his own kind, which Dimintheis keeps from then on.
     */
    private fun enchant(plr: Player, son: Son) {
        plr.newDialogue()
            .player(son.acceptFace, son.accept)
            .then {
                if (it.crestGauntlets == 0 && it.inventory.remove(Item(STEEL_GAUNTLETS))) {
                    it.inventory.add(Item(son.gauntlets))
                    it.crestGauntlets = son.gauntlets
                    it.newDialogue().text(*son.work.toTypedArray()).open()
                }
            }
            .open()
    }
}
