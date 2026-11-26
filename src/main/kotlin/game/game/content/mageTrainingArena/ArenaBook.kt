package game.content.mageTrainingArena

import game.player.QuestJournalInterface
import io.luna.game.model.mob.Player

/**
 * The Arena book, a guide to the training arena sold by the Rewards Guardian.
 *
 * @author TheLining
 */
object ArenaBook {

    /**
     * The book's chapters: a heading, then its paragraphs.
     */
    private val CHAPTERS = listOf(
        "1) The Arena's History" to listOf(
            "As the manufacture of runestones intensified at the start of the Fifth Age, the magic arts became " +
                "available to people of a great varying age and background. It soon became evident just how " +
                "dangerous this was, with a great many tragic accidents occurring due to inexperienced wizards. " +
                "Wizards and victims alike were calling for something to be done, but it was only due to a tragic " +
                "accident involving one of the leaders of that time that the training arena was constructed. " +
                "Established with all skill levels in mind, it was used for a great many years until it was " +
                "destroyed due to conflict of opinion. There were many warriors of melee and ranged combat that " +
                "took offence to this new art of magic, and taking matters into their own hands, they destroyed the " +
                "arena. At the time of the writing of this book, a collection of Wizards took it upon themselves to " +
                "resurrect the building along with all Guardians of Magic that were destroyed with it. These " +
                "Guardians were created out of the very same essence as runestones, embodying the magic power and " +
                "authority needed to oversee the arena."),
        "2) The Pizazz Points System" to listOf(
            "The arena wants to reward mages for their perseverance in such a complex skill. Four types of Pizazz " +
                "Points can be earnt from the four areas in the arena. There are:",
            "Telekinetic Pizazz Points", "Enchantment Pizazz Points", "Alchemist Pizazz Points",
            "Graveyard Pizazz Points",
            "By earning points for performing the tasks in the four areas, a mage can prove his adeptness at the " +
                "different skills, and claim items to display his skill. In order to keep track of the mage's " +
                "progress, a special type of hat was commissioned. Carrying this chap around, and by talking to it " +
                "nicely, he will keep track of a mage's Pizazz Points for future record. The best way to please the " +
                "hat is by collecting points, by which many astute students have noticed the visual change in the " +
                "hat. However, there is a limit to the total number of points that can be collected without " +
                "spending them, but this won't stop mages from continuing in their training."),
        "3) Telekinetic Theatre" to listOf(
            "The ability to use magic to sense the existence of objects and then move them has a great many " +
                "purposes in day- to-day life. This theatre employs the mage to use their telekinetic spell to move " +
                "a statue around a maze. Standing on the side they wish to move the statue towards, the goal is to " +
                "get the statue to the solution square. This also improves the awareness of the mage, assessing the " +
                "consequences of casting the spell: just how will the object move? Once the maze has been solved, " +
                "the statue will be brought to life, providing the option to move onto the next maze. Reward is " +
                "given in the form of 2 Telekinetic Pizazz points for each maze solved, and a bonus for every five " +
                "mazes solved in a row."),
        "4) Alchemists' Playground" to listOf(
            "The arena is free to all those that wish to train here, but it costs to maintain the building, and the " +
                "Guardians don't work for free! The popularity of the alchemy spell has become apparent over the " +
                "years, and so in this playground of sorts, the art of alchemy can be practiced by finding the most " +
                "expensive items in the playground and converting them into coins.",
            "The money must be deposited in the receptacle opposite the entrance before leaving, otherwise it is " +
                "taken upon leaving the playground without reward. Although we would rather trust the mages, some " +
                "are just far too greedy and deceitful! Every 100 coins will reward the mage with one Alchemy " +
                "Pizazz Point, and you'll also get 10% of your coins back as an added thank you."),
        "5) Enchanting Chamber" to listOf(
            "When the wizards first created the arena, they created special artefacts that would change shape and " +
                "react to the attempt to enchant them. These artefacts could store the power from the spell and be " +
                "used to drive magical devices. Hence in this arena, enchanting the various shapes will create orbs " +
                "that can be deposited to help give power back to the arena itself. For every ten shapes enchanted, " +
                "Pizazz Points are rewarded depending on the level of enchantment spell used, and for every 20 orbs " +
                "given, an item of mage equipment is awarded. An extra Pizazz Point is also given for every shape " +
                "enchanted as indicated by the Enchantment Guardian on hand. It's also a good idea to watch out for " +
                "rare dragon stones which share similar properties as the shapes, but can be converted for extra " +
                "Pizazz Points."),
        "6) Creature Graveyard" to listOf(
            "A lot of death occurs around Gielinor leaving remains lying around for all to see. The wizards took it " +
                "upon themselves to tidy up this mess and put the bones gathered to a better use. Teleporting the " +
                "bones into this graveyard, the Bones to Bananas spell can be used to create food which can be " +
                "delivered to hungry beasts around Gielinor. There are different types, which will reward you with, " +
                "1,2,3 or 4 bananas, as shown beside the bones while you are in the graveyard.",
            "Different piles will yield different bones, changing type as people take from them. Mages can even " +
                "practice to earn the right to learn a new spell - the Bones to Peaches spell, which can be bought " +
                "from the Rewards Guardian above the entrance hall. A great many runestones are also found with the " +
                "remains of bodies, and the wizards have gathered those as a reward for supplying food. There is a " +
                "downside to the graveyard however: the bones are often teleported right on-top of the mages, and " +
                "so the food may come in handy to recover from any damage received!",
            "Good luck!")
    )

    /**
     * The widest line the journal shows comfortably.
     */
    private const val WIDTH = 58

    /**
     * Opens the book for [plr].
     */
    fun read(plr: Player) {
        val lines = ArrayList<String>()
        for ((heading, paragraphs) in CHAPTERS) {
            lines += "@dbl@$heading"
            paragraphs.forEach { lines += wrap(it) }
            lines += ""
        }
        val journal = QuestJournalInterface("Arena Book")
        lines.take(100).forEach { journal.addLine(it) }
        plr.overlays.open(journal)
    }

    private fun wrap(text: String): List<String> {
        val lines = ArrayList<String>()
        var line = StringBuilder()
        for (word in text.split(' ')) {
            if (line.isNotEmpty() && line.length + 1 + word.length > WIDTH) {
                lines += line.toString()
                line = StringBuilder()
            }
            if (line.isNotEmpty()) line.append(' ')
            line.append(word)
        }
        if (line.isNotEmpty()) lines += line.toString()
        return lines
    }
}
