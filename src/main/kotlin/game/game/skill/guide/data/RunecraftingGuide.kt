package game.skill.guide.data

import game.skill.guide.skillGuide

/**
 * The Runecrafting guide. The levels match `CraftableRune` and `EssencePouch`.
 */
val runecraftingGuide = skillGuide("Runecrafting") {
    category("Runes") {
        row(1, 556, "Air rune")
        row(1, 5509, "Small pouch")
        row(2, 558, "Mind rune")
        row(5, 555, "Water rune")
        row(9, 557, "Earth rune")
        row(14, 554, "Fire rune")
        row(20, 559, "Body rune")
        row(25, 5510, "Medium pouch")
        row(27, 564, "Cosmic rune")
        row(35, 562, "Chaos rune")
        row(44, 561, "Nature rune")
        row(50, 5512, "Large pouch")
        row(54, 563, "Law rune")
        row(65, 560, "Death rune")
        row(75, 5514, "Giant pouch")
    }
    category("Multiples") {
        row(11, 556, "2 air runes per essence")
        row(14, 558, "2 mind runes per essence")
        row(19, 555, "2 water runes per essence")
        row(22, 556, "3 air runes per essence")
        row(26, 557, "2 earth runes per essence")
        row(28, 558, "3 mind runes per essence")
        row(33, 556, "4 air runes per essence")
        row(35, 554, "2 fire runes per essence")
        row(38, 555, "3 water runes per essence")
        row(42, 558, "4 mind runes per essence")
        row(44, 556, "5 air runes per essence")
        row(46, 559, "2 body runes per essence")
        row(52, 557, "3 earth runes per essence")
        row(55, 556, "6 air runes per essence")
        row(56, 558, "5 mind runes per essence")
        row(57, 555, "4 water runes per essence")
        row(59, 564, "2 cosmic runes per essence")
        row(66, 556, "7 air runes per essence")
        row(70, 558, "6 mind runes per essence")
        row(70, 554, "3 fire runes per essence")
        row(74, 562, "2 chaos runes per essence")
        row(76, 555, "5 water runes per essence")
        row(77, 556, "8 air runes per essence")
        row(78, 557, "4 earth runes per essence")
        row(84, 558, "7 mind runes per essence")
        row(88, 556, "9 air runes per essence")
        row(91, 561, "2 nature runes per essence")
        row(92, 559, "3 body runes per essence")
        row(95, 555, "6 water runes per essence")
        row(98, 558, "8 mind runes per essence")
        row(99, 556, "10 air runes per essence")
        row(99, 563, "2 law runes per essence")
        row(99, 560, "2 death runes per essence")
    }
}
