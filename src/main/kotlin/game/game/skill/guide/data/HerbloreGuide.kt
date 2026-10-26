package game.skill.guide.data

import game.skill.guide.skillGuide

/**
 * The Herblore guide. The levels match `Herb` and `FinishedPotion`.
 */
val herbloreGuide = skillGuide("Herblore") {
    category("Herbs") {
        row(3, 249, "Guam leaf")
        row(5, 251, "Marrentill")
        row(11, 253, "Tarromin")
        row(20, 255, "Harralander")
        row(25, 257, "Ranarr weed")
        row(30, 2998, "Toadflax")
        row(40, 259, "Irit leaf")
        row(48, 261, "Avantoe")
        row(54, 263, "Kwuarm")
        row(59, 3000, "Snapdragon")
        row(65, 265, "Cadantine")
        row(67, 2481, "Lantadyme")
        row(70, 267, "Dwarf weed")
        row(75, 269, "Torstol")
    }
    category("Potions") {
        row(3, 121, "Attack potion")
        row(5, 175, "Antipoison")
        row(8, 115, "Strength potion")
        row(15, 3410, "Serum 207")
        row(22, 127, "Restore potion")
        row(25, 1582, "Blamish oil")
        row(26, 3010, "Energy potion")
        row(30, 133, "Defence potion")
        row(34, 3034, "Agility potion")
        row(38, 139, "Prayer potion")
        row(45, 145, "Super attack")
        row(48, 181, "Superantipoison")
        row(50, 151, "Fishing potion")
        row(52, 3018, "Super energy")
        row(55, 157, "Super strength")
        row(63, 3026, "Super restore")
        row(66, 163, "Super defence")
        row(69, 2454, "Antifire potion")
        row(72, 169, "Ranging potion")
        row(76, 3042, "Magic potion")
        row(78, 189, "Zamorak brew")
        row(81, 6687, "Saradomin brew")
    }
}
