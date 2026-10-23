package game.content.dwarfMulticannon

import api.predef.*
import game.content.dwarfMulticannon.DwarfMulticannon.INSTRUCTION_MANUAL
import game.player.QuestJournalInterface

/**
 * The longest line the scroll fits.
 */
val LINE_LENGTH = 55

/**
 * The manual's pages, each a heading and its paragraphs.
 */
val PAGES = listOf(
    "Constructing the cannon" to listOf(
        "To construct the cannon, firstly set down Dwarf cannon base on the ground.",
        "Next add the Dwarf cannon stand to the Dwarf cannon base.",
        "Then add the Dwarf cannon barrels (this can be tiring work).",
        "Last of all add the Dwarf cannon furnace which powers the cannon.",
        "You should now have a fully set up dwarf multi cannon ready to splat some nasty creatures."
    ),
    "Making ammo" to listOf(
        "The ammo for the cannon is made from steel bars.",
        "Firstly you must heat up a steel bar in a furnace then pour the molten steel into a cannon ammo mould.",
        "You should now have a ready to fire multi cannon ball."
    ),
    "Firing the cannon" to listOf(
        "The cannon will only fire when monsters are available to target.",
        "If you are carrying enough ammo the multi cannon will fire up to 30 rounds before stopping.",
        "The cannon will automatically target non friendly creatures."
    ),
    "Dwarf cannon warranty" to listOf(
        "If your cannon is stolen or lost, after or during being set up, the Dwarf engineer will happily replace the parts.",
        "However cannon parts that were given away or dropped will not be replaced for free.",
        "It is only possible to operate one cannon at a time.",
        "by order of the Dwarven Black Guard"
    )
)

/**
 * Splits [text] into lines of at most [LINE_LENGTH] characters.
 */
fun wrap(text: String): List<String> {
    val lines = ArrayList<String>()
    var line = StringBuilder()
    for (word in text.split(' ')) {
        if (line.isNotEmpty() && line.length + 1 + word.length > LINE_LENGTH) {
            lines += line.toString()
            line = StringBuilder()
        }
        if (line.isNotEmpty()) {
            line.append(' ')
        }
        line.append(word)
    }
    lines += line.toString()
    return lines
}

item1(INSTRUCTION_MANUAL) {
    val manual = QuestJournalInterface("Dwarven Multi Cannon")
    for ((heading, paragraphs) in PAGES) {
        manual.addLine("@red@$heading")
        for (paragraph in paragraphs) {
            manual.newLine()
            wrap(paragraph).forEach(manual::addLine)
        }
        manual.newLine()
    }
    plr.overlays.open(manual)
}
